package com.cloud.computing.filesharingapp.entity;

/**
 * Enumeration representing the various states of a user account.
 * 
 * <p>This enum defines the lifecycle states that a user account can be in,
 * from initial registration through active use and potential suspension.
 * The account status determines what actions a user can perform and which
 * resources they can access.
 * 
 * <p>Account status transitions:
 * <ul>
 *   <li>PENDING → ACTIVE: When email verification is completed</li>
 *   <li>ACTIVE → SUSPENDED: When administrative action is taken</li>
 *   <li>SUSPENDED → ACTIVE: When suspension is lifted by admin</li>
 * </ul>
 * 
 * @author File Sharing App Team
 * @version 1.0
 * @since 1.0
 */
public enum AccountStatus {
    /** 
     * Account has been created but email verification is not yet completed.
     * Users with PENDING status cannot log in or access protected resources.
     */
    PENDING,
    
    /** 
     * Account is fully active with verified email address.
     * Users with ACTIVE status have full access to application features.
     */
    ACTIVE,
    
    /** 
     * Account has been suspended by an administrator.
     * Users with SUSPENDED status cannot log in or access any resources.
     */
    SUSPENDED
}