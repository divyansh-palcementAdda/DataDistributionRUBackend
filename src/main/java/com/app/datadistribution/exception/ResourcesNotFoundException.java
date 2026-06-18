package com.app.datadistribution.exception;

public class ResourcesNotFoundException extends ResourceNotFoundException {

    public ResourcesNotFoundException() {
        super("Resource not found");
    }

    public ResourcesNotFoundException(String message) {
        super(message);
    }

    public ResourcesNotFoundException(String message, Throwable cause) {
        super(message);
    }

    public ResourcesNotFoundException(String resourceName, String fieldName, Object fieldValue) {
        super(String.format("%s not found with %s : '%s'", resourceName, fieldName, fieldValue));
    }

    public ResourcesNotFoundException(String resourceName, String fieldName, Object fieldValue, Throwable cause) {
        super(String.format("%s not found with %s : '%s'", resourceName, fieldName, fieldValue));
    }
}