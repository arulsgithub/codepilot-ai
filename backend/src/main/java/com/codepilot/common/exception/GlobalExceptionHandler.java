package com.codepilot.common.exception;

import com.codepilot.repo.service.GitRepositoryService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.OffsetDateTime;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Small helper so every handler builds the same response shape. Before this, each
     * handler repeated five constructor arguments, and the Git handler had drifted into
     * returning a bare Map - a different JSON shape for one error type, which any client
     * parsing our errors would choke on.
     */
    private ResponseEntity<ApiErrorResponse> error(HttpStatus status, ErrorCode code,
                                                   String message, HttpServletRequest request) {
        ApiErrorResponse response = new ApiErrorResponse(
                OffsetDateTime.now(),
                status.value(),
                code.name(),
                message,
                request.getRequestURI()
        );
        return ResponseEntity.status(status).body(response);
    }

    /**
     * Bad input from the caller: a path that is not a directory, an unknown repository id,
     * neither repositoryId nor repositoryRoot supplied.
     *
     * Note the code is INVALID_REQUEST, not RESOURCE_NOT_FOUND. The old value was
     * misleading - it paired a 400 status with a "not found" code, so a client switching
     * on the code would draw the wrong conclusion about what went wrong.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgumentException(
            IllegalArgumentException exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_REQUEST,
                exception.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationException(
            MethodArgumentNotValidException exception, HttpServletRequest request) {

        String message = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("Invalid request");

        return error(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_REQUEST, message, request);
    }

    /**
     * NEW. Jackson could not turn the request JSON into our DTO - a malformed UUID,
     * an unknown enum value, a string where a number belongs.
     *
     * This is the caller's mistake, so 400. Without this handler it fell to the generic
     * handler as a 500, which is what made a literal "<your-uuid>" placeholder look like
     * a server crash rather than a typo.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableBody(
            HttpMessageNotReadableException exception, HttpServletRequest request) {

        String detail = exception.getMostSpecificCause().getMessage();
        log.warn("Malformed request body for {} {}: {}",
                request.getMethod(), request.getRequestURI(), detail);

        return error(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_REQUEST,
                "Malformed request body: " + detail, request);
    }

    /**
     * NEW. No controller is mapped to this URL, so Spring fell through to static resource
     * handling and found nothing there either.
     *
     * Must be 404. As a 500 it claims the server broke when the truth is the URL is wrong,
     * which sends you debugging the wrong layer entirely.
     *
     * log.warn rather than log.error on purpose: a wrong URL is a client mistake. Logging
     * it at ERROR pollutes the error log and makes real faults harder to spot.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNoEndpoint(
            NoResourceFoundException exception, HttpServletRequest request) {

        log.warn("No endpoint mapped for {} {}", request.getMethod(), request.getRequestURI());

        return error(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                "No endpoint for " + request.getMethod() + " " + request.getRequestURI(), request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException exception, HttpServletRequest request) {

        String message = "Content-Type '" + exception.getContentType()
                + "' is not supported. Send this request with 'Content-Type: application/json'.";

        return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ErrorCode.INVALID_REQUEST, message, request);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalStateException(
            IllegalStateException exception, HttpServletRequest request) {

        log.warn("AI provider error for {} {}: {}",
                request.getMethod(), request.getRequestURI(), exception.getMessage(), exception);

        return error(HttpStatus.BAD_GATEWAY, ErrorCode.AI_PROVIDER_ERROR,
                exception.getMessage(), request);
    }

    /**
     * Errors coming back FROM an AI provider (402 out of credit, 429 rate limited, 401 bad key).
     * Without this they fall through to the generic handler and read as "something broke in our
     * code", sending you to debug the wrong thing - which is exactly what happened with a
     * 402 from OpenRouter.
     */
    @ExceptionHandler(WebClientResponseException.class)
    public ResponseEntity<ApiErrorResponse> handleProviderError(
            WebClientResponseException exception, HttpServletRequest request) {

        log.warn("AI provider returned {} for {} {}", exception.getStatusCode(),
                request.getMethod(), request.getRequestURI());

        return error(HttpStatus.BAD_GATEWAY, ErrorCode.AI_PROVIDER_ERROR,
                "AI provider returned " + exception.getStatusCode() + ": "
                        + exception.getResponseBodyAsString(), request);
    }

    /**
     * Git clone/fetch failed. 502 because our service is fine - the upstream interaction
     * with GitHub is what broke. GitRepositoryService has already turned the raw JGit
     * exception into a readable message (bad token, repo not found), so we pass it through.
     *
     * Now returns ApiErrorResponse like every other handler instead of a bare Map.
     */
    @ExceptionHandler(GitRepositoryService.GitOperationException.class)
    public ResponseEntity<ApiErrorResponse> handleGitFailure(
            GitRepositoryService.GitOperationException exception, HttpServletRequest request) {

        log.warn("Git operation failed for {} {}: {}",
                request.getMethod(), request.getRequestURI(), exception.getMessage());

        return error(HttpStatus.BAD_GATEWAY, ErrorCode.AI_PROVIDER_ERROR,
                exception.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGenericException(
            Exception exception, HttpServletRequest request) {

        log.error("Unhandled exception for {} {}", request.getMethod(), request.getRequestURI(), exception);

        return error(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR,
                "An unexpected error occurred.", request);
    }
}