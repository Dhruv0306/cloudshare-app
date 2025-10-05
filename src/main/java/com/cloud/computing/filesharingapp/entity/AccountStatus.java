package com.cloud.computing.filesharingapp.entity;

public enum AccountStatus {
    PENDING,    // Account created but email not verified
    ACTIVE,     // Account active and email verified
    SUSPENDED   // Account suspended by admin
}