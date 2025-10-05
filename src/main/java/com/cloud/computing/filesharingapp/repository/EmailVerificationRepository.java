package com.cloud.computing.filesharingapp.repository;

import com.cloud.computing.filesharingapp.entity.EmailVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface EmailVerificationRepository extends JpaRepository<EmailVerification, Long> {
    
    /**
     * Find the most recent unused verification code for an email
     */
    Optional<EmailVerification> findTopByEmailAndUsedFalseOrderByCreatedAtDesc(String email);
    
    /**
     * Find verification record by email and code
     */
    Optional<EmailVerification> findByEmailAndVerificationCodeAndUsedFalse(String email, String verificationCode);
    
    /**
     * Find all verification records for a specific email
     */
    List<EmailVerification> findByEmailOrderByCreatedAtDesc(String email);
    
    /**
     * Find all expired verification codes
     */
    List<EmailVerification> findByExpiresAtBeforeAndUsedFalse(LocalDateTime dateTime);
    
    /**
     * Count unused verification codes for an email within a time period
     */
    @Query("SELECT COUNT(ev) FROM EmailVerification ev WHERE ev.email = :email AND ev.createdAt >= :since AND ev.used = false")
    long countByEmailAndCreatedAtAfterAndUsedFalse(@Param("email") String email, @Param("since") LocalDateTime since);
    
    /**
     * Mark all unused verification codes for an email as used
     */
    @Modifying
    @Query("UPDATE EmailVerification ev SET ev.used = true WHERE ev.email = :email AND ev.used = false")
    void markAllAsUsedByEmail(@Param("email") String email);
    
    /**
     * Delete expired verification codes
     */
    @Modifying
    @Query("DELETE FROM EmailVerification ev WHERE ev.expiresAt < :dateTime")
    void deleteExpiredVerifications(@Param("dateTime") LocalDateTime dateTime);
    
    /**
     * Count unused verification codes
     */
    long countByUsedFalse();
}