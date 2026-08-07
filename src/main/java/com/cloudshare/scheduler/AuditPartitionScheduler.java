package com.cloudshare.scheduler;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.time.YearMonth;
import java.util.List;
import java.util.Locale;

@Component
@RequiredArgsConstructor
@Slf4j
@Profile("!rekey-job")
public class AuditPartitionScheduler {

    private final JdbcTemplate jdbcTemplate;
    private final MeterRegistry meterRegistry;

    @Value("${app.scheduler.audit-partition.lookahead-months:3}")
    private int lookaheadMonths = 3;

    @Scheduled(cron = "${app.scheduler.audit-partition.cron:0 0 4 * * ?}")
    public void maintainPartitions() {
        log.info("Starting audit log partition maintenance scheduler job...");
        try {
            String dbProduct = jdbcTemplate.execute((Connection conn) -> conn.getMetaData().getDatabaseProductName());
            if (dbProduct == null || !dbProduct.toLowerCase(Locale.ROOT).contains("postgresql")) {
                log.info("Database is not PostgreSQL ({}); skipping native partition creation.", dbProduct);
                return;
            }
        } catch (Exception e) {
            log.error("Failed to determine database product name during partition check", e);
            meterRegistry.counter("cloudshare.audit_partition.check_failures").increment();
            return;
        }

        checkAndCreatePartitions(YearMonth.now());
    }

    public void checkAndCreatePartitions(YearMonth startMonth) {
        List<String> existingPartitions;
        try {
            existingPartitions = jdbcTemplate.query(
                "SELECT child.relname AS partition_name " +
                "FROM pg_inherits " +
                "JOIN pg_class parent ON pg_inherits.inhparent = parent.oid " +
                "JOIN pg_class child ON pg_inherits.inhrelid = child.oid " +
                "WHERE parent.relname = 'audit_logs'",
                (rs, rowNum) -> rs.getString("partition_name").toLowerCase(Locale.ROOT)
            );
        } catch (Exception e) {
            log.error("Failed to query existing partitions for audit_logs table", e);
            meterRegistry.counter("cloudshare.audit_partition.check_failures").increment();
            return;
        }

        for (int i = 0; i <= lookaheadMonths; i++) {
            YearMonth targetMonth = startMonth.plusMonths(i);
            String partitionName = String.format("audit_logs_y%04dm%02d", targetMonth.getYear(), targetMonth.getMonthValue());

            if (!existingPartitions.contains(partitionName)) {
                log.info("Partition {} is missing; executing creation DDL.", partitionName);
                String fromDate = String.format("%04d-%02d-01 00:00:00+00", targetMonth.getYear(), targetMonth.getMonthValue());
                
                YearMonth nextMonth = targetMonth.plusMonths(1);
                String toDate = String.format("%04d-%02d-01 00:00:00+00", nextMonth.getYear(), nextMonth.getMonthValue());

                String ddl = String.format(
                    "CREATE TABLE IF NOT EXISTS %s PARTITION OF audit_logs " +
                    "FOR VALUES FROM ('%s') TO ('%s')",
                    partitionName, fromDate, toDate
                );

                try {
                    jdbcTemplate.execute(ddl);
                    log.info("Successfully created partition: {}", partitionName);
                    meterRegistry.counter("cloudshare.audit_partition.created").increment();
                } catch (Exception e) {
                    log.error("Failed to create audit log partition: " + partitionName, e);
                    meterRegistry.counter("cloudshare.audit_partition.check_failures").increment();
                }
            } else {
                log.debug("Partition {} already exists.", partitionName);
            }
        }
        log.info("Finished audit log partition maintenance scheduler job.");
    }
}
