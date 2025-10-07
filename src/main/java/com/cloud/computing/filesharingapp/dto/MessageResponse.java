package com.cloud.computing.filesharingapp.dto;

/**
 * Data Transfer Object for simple message responses.
 * 
 * <p>This DTO is used to return simple text messages in API responses,
 * typically for success confirmations, error messages, or informational
 * responses. It provides a consistent structure for message-only responses
 * across the application.
 * 
 * <p>Common use cases include:
 * <ul>
 *   <li>Success messages after operations (e.g., "User registered successfully")</li>
 *   <li>Error messages for validation failures</li>
 *   <li>Informational messages for user guidance</li>
 * </ul>
 * 
 * @author File Sharing App Team
 * @version 1.0
 * @since 1.0
 */
public class MessageResponse {
    /** The message content to be returned to the client */
    private String message;
    
    /**
     * Constructor for creating a message response.
     * 
     * @param message the message content to include in the response
     */
    public MessageResponse(String message) {
        this.message = message;
    }
    
    /**
     * Gets the message content.
     * 
     * @return the message string
     */
    public String getMessage() { return message; }
    
    /**
     * Sets the message content.
     * 
     * @param message the message string to set
     */
    public void setMessage(String message) { this.message = message; }
}