package com.app.datadistribution.exception;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    /* =========================================================
       CORE BUILDER
    ========================================================= */

    private ProblemDetail problem(
            HttpStatus status,
            String title,
            String detail,
            String type,
            HttpServletRequest request) {

        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);

        pd.setTitle(title);
        pd.setType(java.net.URI.create(type));
        pd.setProperty("timestamp", LocalDateTime.now());
        pd.setProperty("instance", request.getRequestURI());
        pd.setProperty("traceId", MDC.get("traceId"));

        return pd;
    }

    private ProblemDetail validationProblem(
            HttpServletRequest request,
            Map<String, String> errors) {

        ProblemDetail pd = problem(
                HttpStatus.BAD_REQUEST,
                "Validation Failed",
                "One or more fields failed validation",
                "https://api.cms.com/errors/validation-error",
                request
        );

        pd.setProperty("errors", errors);

        return pd;
    }

    /* =========================================================
       VALIDATION
    ========================================================= */

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        Map<String, String> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        f -> f.getDefaultMessage() != null
                                ? f.getDefaultMessage()
                                : "Invalid value",
                        (a, b) -> a
                ));

        return validationProblem(request, errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(
            ConstraintViolationException ex,
            HttpServletRequest request) {

        Map<String, String> errors = ex.getConstraintViolations()
                .stream()
                .collect(Collectors.toMap(
                        v -> v.getPropertyPath().toString(),
                        ConstraintViolation::getMessage,
                        (a, b) -> a
                ));

        return validationProblem(request, errors);
    }

    @ExceptionHandler(TransactionSystemException.class)
    public ProblemDetail handleTransactionSystem(
            TransactionSystemException ex,
            HttpServletRequest request) {

        if (ex.getRootCause() instanceof ConstraintViolationException cve) {
            return handleConstraintViolation(cve, request);
        }

        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Transaction Failed",
                "Database transaction failed",
                "https://api.cms.com/errors/transaction-failed",
                request
        );
    }

    /* =========================================================
       REQUEST / PARSING
    ========================================================= */

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadable(
            HttpServletRequest request) {

        return problem(
                HttpStatus.BAD_REQUEST,
                "Malformed JSON",
                "Request body contains invalid JSON",
                "https://api.cms.com/errors/malformed-json",
                request
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest request) {

        String expected = ex.getRequiredType() != null
                ? ex.getRequiredType().getSimpleName()
                : "Unknown";

        return problem(
                HttpStatus.BAD_REQUEST,
                "Invalid Parameter Type",
                String.format("Parameter '%s' should be of type %s",
                        ex.getName(), expected),
                "https://api.cms.com/errors/type-mismatch",
                request
        );
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ProblemDetail handleMissingParam(
            MissingServletRequestParameterException ex,
            HttpServletRequest request) {

        return problem(
                HttpStatus.BAD_REQUEST,
                "Missing Parameter",
                "Required parameter missing: " + ex.getParameterName(),
                "https://api.cms.com/errors/missing-parameter",
                request
        );
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ProblemDetail handleMissingHeader(
            MissingRequestHeaderException ex,
            HttpServletRequest request) {

        return problem(
                HttpStatus.BAD_REQUEST,
                "Missing Header",
                "Required header missing: " + ex.getHeaderName(),
                "https://api.cms.com/errors/missing-header",
                request
        );
    }

    /* =========================================================
       HTTP / ROUTING
    ========================================================= */

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ProblemDetail handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException ex,
            HttpServletRequest request) {

        return problem(
                HttpStatus.METHOD_NOT_ALLOWED,
                "Method Not Allowed",
                ex.getMessage(),
                "https://api.cms.com/errors/method-not-allowed",
                request
        );
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ProblemDetail handleMediaType(
            HttpServletRequest request) {

        return problem(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "Unsupported Media Type",
                "Content type is not supported",
                "https://api.cms.com/errors/media-type-not-supported",
                request
        );
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail handleNotFound(
            HttpServletRequest request) {

        return problem(
                HttpStatus.NOT_FOUND,
                "Endpoint Not Found",
                "Requested endpoint does not exist",
                "https://api.cms.com/errors/not-found",
                request
        );
    }

    /* =========================================================
       SECURITY
    ========================================================= */

    @ExceptionHandler(BadCredentialsException.class)
    public ProblemDetail handleBadCredentials(
            HttpServletRequest request) {

        return problem(
                HttpStatus.UNAUTHORIZED,
                "Authentication Failed",
                "Invalid username or password",
                "https://api.cms.com/errors/authentication-failed",
                request
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(
            HttpServletRequest request) {

        return problem(
                HttpStatus.FORBIDDEN,
                "Access Denied",
                "You do not have permission to access this resource",
                "https://api.cms.com/errors/access-denied",
                request
        );
    }

    /* =========================================================
       DATABASE
    ========================================================= */

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleIntegrity(
            DataIntegrityViolationException ex,
            HttpServletRequest request) {

        String msg = "Database constraint violation";

        if (ex.getRootCause() != null &&
            ex.getRootCause().getMessage() != null) {

            String root = ex.getRootCause().getMessage().toLowerCase();

            if (root.contains("duplicate")) {
                msg = "Duplicate record already exists";
            } else if (root.contains("foreign key")) {
                msg = "Referenced entity does not exist";
            }
        }

        return problem(
                HttpStatus.CONFLICT,
                "Data Integrity Violation",
                msg,
                "https://api.cms.com/errors/data-integrity",
                request
        );
    }

    /* =========================================================
       FILES
    ========================================================= */

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail handleUploadSize(
            HttpServletRequest request) {

        return problem(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "File Too Large",
                "Uploaded file exceeds maximum allowed size",
                "https://api.cms.com/errors/file-too-large",
                request
        );
    }

    /* =========================================================
       BUSINESS EXCEPTIONS
    ========================================================= */

    @ExceptionHandler(BadRequestException.class)
    public ProblemDetail handleBadRequest(
            BadRequestException ex,
            HttpServletRequest request) {

        return problem(
                HttpStatus.BAD_REQUEST,
                "Bad Request",
                ex.getMessage(),
                "https://api.cms.com/errors/bad-request",
                request
        );
    }

    @ExceptionHandler(ResourcesNotFoundException.class)
    public ProblemDetail handleResourceNotFound(
            ResourcesNotFoundException ex,
            HttpServletRequest request) {

        return problem(
                HttpStatus.NOT_FOUND,
                "Resource Not Found",
                ex.getMessage(),
                "https://api.cms.com/errors/resource-not-found",
                request
        );
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ProblemDetail handleUnauthorized(
            UnauthorizedException ex,
            HttpServletRequest request) {

        return problem(
                HttpStatus.UNAUTHORIZED,
                "Unauthorized",
                ex.getMessage(),
                "https://api.cms.com/errors/unauthorized",
                request
        );
    }

    /* =========================================================
       FALLBACK
    ========================================================= */

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneric(
            Exception ex,
            HttpServletRequest request) {

        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal Server Error",
                "An unexpected internal error occurred",
                "https://api.cms.com/errors/internal-server-error",
                request
        );
    }
}