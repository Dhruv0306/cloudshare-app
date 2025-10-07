/**
 * Entity classes for the file sharing system.
 * 
 * <p>This package contains JPA entity classes that represent the data model
 * for file sharing functionality, including:
 * 
 * <ul>
 *   <li>{@link com.cloud.computing.filesharingapp.entity.FileShare} - Core file sharing entity</li>
 *   <li>{@link com.cloud.computing.filesharingapp.entity.ShareAccess} - Access logging for shared files</li>
 *   <li>{@link com.cloud.computing.filesharingapp.entity.ShareNotification} - Email notification tracking</li>
 *   <li>{@link com.cloud.computing.filesharingapp.entity.SharePermission} - Permission levels for shares</li>
 *   <li>{@link com.cloud.computing.filesharingapp.entity.ShareAccessType} - Types of file access</li>
 * </ul>
 * 
 * <p>All entities follow JPA best practices with proper annotations, relationships,
 * and database indexing for optimal performance. The entities support comprehensive
 * auditing, security tracking, and analytics for the file sharing system.
 * 
 * @author File Sharing App Team
 * @version 1.0
 * @since 1.0
 */
package com.cloud.computing.filesharingapp.entity;