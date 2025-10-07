/**
 * Repository interfaces for file sharing data access.
 * 
 * <p>This package contains Spring Data JPA repository interfaces that provide
 * data access methods for file sharing entities. The repositories include:
 * 
 * <ul>
 *   <li>{@link com.cloud.computing.filesharingapp.repository.FileShareRepository} - File share CRUD and queries</li>
 *   <li>{@link com.cloud.computing.filesharingapp.repository.ShareAccessRepository} - Access logging and analytics</li>
 *   <li>{@link com.cloud.computing.filesharingapp.repository.ShareNotificationRepository} - Notification tracking</li>
 * </ul>
 * 
 * <p>Each repository extends {@link org.springframework.data.jpa.repository.JpaRepository}
 * and provides custom query methods for:
 * <ul>
 *   <li>Token-based share lookups</li>
 *   <li>User-scoped queries</li>
 *   <li>Security and analytics queries</li>
 *   <li>Maintenance and cleanup operations</li>
 *   <li>Statistical reporting</li>
 * </ul>
 * 
 * <p>All custom queries are optimized with proper indexing and follow Spring Data
 * naming conventions for automatic query generation where possible.
 * 
 * @author File Sharing App Team
 * @version 1.0
 * @since 1.0
 */
package com.cloud.computing.filesharingapp.repository;