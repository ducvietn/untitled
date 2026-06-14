package com.teamup.teamup.exception;

/**
 * Thrown when a requested resource (file, task, group, etc.) cannot be found.
 * Caught by {@code GlobalExceptionHandler} and mapped to HTTP 404.
 */
public class ResourceNotFoundException extends RuntimeException {

    private final String resourceType;
    private final Object resourceId;

    public ResourceNotFoundException(String resourceType, Object resourceId) {
        super(String.format("%s not found with id: %s", resourceType, resourceId));
        this.resourceType = resourceType;
        this.resourceId   = resourceId;
    }

    public String getResourceType() { return resourceType; }
    public Object getResourceId()   { return resourceId; }
}
