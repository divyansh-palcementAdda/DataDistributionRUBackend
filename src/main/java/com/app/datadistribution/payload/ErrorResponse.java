package com.app.datadistribution.payload;


import java.time.LocalDateTime;
import java.util.Map;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ErrorResponse {

    private boolean success;
    private int status;
    private String message;
    private LocalDateTime timestamp;
    private Map<String, String> errors;
}