package com.app.datadistribution.exception;
public class ResourcesNotFoundException extends RuntimeException {

    public ResourcesNotFoundException() {
        super();
    }

    public ResourcesNotFoundException(String message) {
        super(message);
    }

    public ResourcesNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    // ← The most commonly used constructor in Spring Boot apps
    public ResourcesNotFoundException(String resourceName, String fieldName, Object fieldValue) {
        super(String.format("%s not found with %s : '%s'", resourceName, fieldName, fieldValue));
    }

    // Optional: if you sometimes want to pass a cause
    public ResourcesNotFoundException(String resourceName, String fieldName, Object fieldValue, Throwable cause) {
        super(String.format("%s not found with %s : '%s'", resourceName, fieldName, fieldValue), cause);
    }
}