package com.cloud.computing.filesharingapp.repository;

import com.cloud.computing.filesharingapp.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    Boolean existsByUsername(String username);
    Boolean existsByEmail(String email);
    
    // Email verification related queries
    long countByEmailVerifiedTrue();
    long countByEmailVerifiedFalse();
    
    // Account status related queries
    @Query("SELECT COUNT(u) FROM User u WHERE u.accountStatus = :status AND u.createdAt < :dateTime")
    long countPendingUsersOlderThan(@Param("dateTime") LocalDateTime dateTime);
    
    @Modifying
    @Query("DELETE FROM User u WHERE u.accountStatus = 'PENDING' AND u.createdAt < :dateTime")
    void deletePendingUsersOlderThan(@Param("dateTime") LocalDateTime dateTime);
}