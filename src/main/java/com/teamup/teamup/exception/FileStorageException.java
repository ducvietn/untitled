package com.teamup.teamup.exception;

/**
 * Thrown when a file exists in the database but the underlying storage
 * (Cloudinary / S3 / local disk) no longer has the bytes.
 * Caught by {@code GlobalExceptionHandler} and mapped to HTTP 404
 * with a distinct error code: {@code FILE_MISSING_FROM_STORAGE}.
 */
public class FileStorageException extends RuntimeException {

    private final String errorCode;

    public FileStorageException(String message) {
        super(message);
        this.errorCode = "FILE_STORAGE_ERROR";
    }

    public FileStorageException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "FILE_STORAGE_ERROR";
    }

    /**
     * Used when the DB record exists but the cloud file has been deleted
     * or the signed URL has expired.
     */
    public static FileStorageException fileMissingFromStorage(String fileUrl) {
        FileStorageException ex = new FileStorageException(
            "File record exists but file is no longer available in storage: " + fileUrl);
        return ex;
    }

    public String getErrorCode() { return errorCode; }
}
