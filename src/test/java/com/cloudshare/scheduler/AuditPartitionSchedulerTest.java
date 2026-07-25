package com.cloudshare.scheduler;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditPartitionSchedulerTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private SimpleMeterRegistry meterRegistry;
    private AuditPartitionScheduler scheduler;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        scheduler = new AuditPartitionScheduler(jdbcTemplate, meterRegistry);
        ReflectionTestUtils.setField(scheduler, "lookaheadMonths", 3);
    }

    @SuppressWarnings("unchecked")
    @Test
    void partitionsAlreadyExist_forConfiguredLookaheadWindow_createsNothing() {
        YearMonth start = YearMonth.of(2026, 7);
        List<String> mockPartitions = Arrays.asList(
            "audit_logs_y2026m07",
            "audit_logs_y2026m08",
            "audit_logs_y2026m09",
            "audit_logs_y2026m10"
        );

        when(jdbcTemplate.query(anyString(), any(RowMapper.class))).thenReturn(mockPartitions);

        scheduler.checkAndCreatePartitions(start);

        verify(jdbcTemplate, never()).execute(anyString());
        assertEquals(0.0, meterRegistry.counter("cloudshare.audit_partition.created").count());
        assertEquals(0.0, meterRegistry.counter("cloudshare.audit_partition.check_failures").count());
    }

    @SuppressWarnings("unchecked")
    @Test
    void missingFuturePartitions_createsExactlyTheMissingOnes() {
        YearMonth start = YearMonth.of(2026, 7);
        List<String> mockPartitions = new ArrayList<>(Arrays.asList(
            "audit_logs_y2026m07",
            "audit_logs_y2026m08"
        ));

        when(jdbcTemplate.query(anyString(), any(RowMapper.class))).thenReturn(mockPartitions);

        scheduler.checkAndCreatePartitions(start);

        verify(jdbcTemplate).execute("CREATE TABLE IF NOT EXISTS audit_logs_y2026m09 PARTITION OF audit_logs FOR VALUES FROM ('2026-09-01 00:00:00+00') TO ('2026-10-01 00:00:00+00')");
        verify(jdbcTemplate).execute("CREATE TABLE IF NOT EXISTS audit_logs_y2026m10 PARTITION OF audit_logs FOR VALUES FROM ('2026-10-01 00:00:00+00') TO ('2026-11-01 00:00:00+00')");
        
        verify(jdbcTemplate, times(2)).execute(anyString());
        
        assertEquals(2.0, meterRegistry.counter("cloudshare.audit_partition.created").count());
        assertEquals(0.0, meterRegistry.counter("cloudshare.audit_partition.check_failures").count());
    }

    @SuppressWarnings("unchecked")
    @Test
    void partitionCreationThrows_logsAtErrorAndIncrementsFailureCounter() {
        YearMonth start = YearMonth.of(2026, 7);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class))).thenReturn(Collections.emptyList());
        
        doThrow(new RuntimeException("PostgreSQL connection lost")).when(jdbcTemplate).execute(anyString());

        scheduler.checkAndCreatePartitions(start);

        assertEquals(0.0, meterRegistry.counter("cloudshare.audit_partition.created").count());
        assertEquals(4.0, meterRegistry.counter("cloudshare.audit_partition.check_failures").count());
    }

    @SuppressWarnings("unchecked")
    @Test
    void checkAndCreatePartitions_rollsOverYearBoundary_correctly() {
        YearMonth start = YearMonth.of(2026, 12);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class))).thenReturn(Collections.emptyList());

        scheduler.checkAndCreatePartitions(start);

        verify(jdbcTemplate).execute("CREATE TABLE IF NOT EXISTS audit_logs_y2026m12 PARTITION OF audit_logs FOR VALUES FROM ('2026-12-01 00:00:00+00') TO ('2027-01-01 00:00:00+00')");
        verify(jdbcTemplate).execute("CREATE TABLE IF NOT EXISTS audit_logs_y2027m01 PARTITION OF audit_logs FOR VALUES FROM ('2027-01-01 00:00:00+00') TO ('2027-02-01 00:00:00+00')");
        verify(jdbcTemplate).execute("CREATE TABLE IF NOT EXISTS audit_logs_y2027m02 PARTITION OF audit_logs FOR VALUES FROM ('2027-02-01 00:00:00+00') TO ('2027-03-01 00:00:00+00')");
        verify(jdbcTemplate).execute("CREATE TABLE IF NOT EXISTS audit_logs_y2027m03 PARTITION OF audit_logs FOR VALUES FROM ('2027-03-01 00:00:00+00') TO ('2027-04-01 00:00:00+00')");

        verify(jdbcTemplate, times(4)).execute(anyString());
        assertEquals(4.0, meterRegistry.counter("cloudshare.audit_partition.created").count());
    }

    @SuppressWarnings("unchecked")
    @Test
    void checkAndCreatePartitions_isIdempotent() {
        YearMonth start = YearMonth.of(2026, 7);
        List<String> mockPartitions = new ArrayList<>(Collections.singletonList("audit_logs_y2026m07"));
        
        when(jdbcTemplate.query(anyString(), any(RowMapper.class))).thenReturn(mockPartitions);

        scheduler.checkAndCreatePartitions(start);
        verify(jdbcTemplate, times(3)).execute(anyString());
        assertEquals(3.0, meterRegistry.counter("cloudshare.audit_partition.created").count());

        reset(jdbcTemplate);
        List<String> mockPartitionsSecondRun = Arrays.asList(
            "audit_logs_y2026m07",
            "audit_logs_y2026m08",
            "audit_logs_y2026m09",
            "audit_logs_y2026m10"
        );
        when(jdbcTemplate.query(anyString(), any(RowMapper.class))).thenReturn(mockPartitionsSecondRun);

        scheduler.checkAndCreatePartitions(start);

        verify(jdbcTemplate, never()).execute(anyString());
        assertEquals(3.0, meterRegistry.counter("cloudshare.audit_partition.created").count());
        assertEquals(0.0, meterRegistry.counter("cloudshare.audit_partition.check_failures").count());
    }
}
