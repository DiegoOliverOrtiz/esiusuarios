package esi.edu.usuarios.usuarios.http;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final String BAD_REQUEST = "Solicitud invalida.";
    private static final String INTERNAL_ERROR = "No se ha podido completar la solicitud.";
    private final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatus(ResponseStatusException error, HttpServletRequest request) {
        HttpStatus status = HttpStatus.valueOf(error.getStatusCode().value());
        if (status == HttpStatus.UNAUTHORIZED || status == HttpStatus.FORBIDDEN) {
            logger.warn("Acceso denegado en {} {} status={} ip={}", request.getMethod(), request.getRequestURI(), status.value(), clientIp(request));
        }
        String message = status.is4xxClientError() ? safeClientMessage(error) : INTERNAL_ERROR;
        return build(status, message, request);
    }

    @ExceptionHandler({
        MethodArgumentNotValidException.class,
        BindException.class,
        ConstraintViolationException.class,
        IllegalArgumentException.class
    })
    public ResponseEntity<ApiErrorResponse> handleBadRequest(Exception error, HttpServletRequest request) {
        logger.warn("Solicitud invalida en {} {}", request.getMethod(), request.getRequestURI());
        return build(HttpStatus.BAD_REQUEST, BAD_REQUEST, request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotAllowed(Exception error, HttpServletRequest request) {
        logger.warn("Metodo no permitido en {} {}", request.getMethod(), request.getRequestURI());
        return build(HttpStatus.METHOD_NOT_ALLOWED, "Metodo no permitido.", request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(Exception error, HttpServletRequest request) {
        logger.warn("Recurso no encontrado en {} {}", request.getMethod(), request.getRequestURI());
        return build(HttpStatus.NOT_FOUND, "Recurso no encontrado.", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception error, HttpServletRequest request) {
        logger.error("Error no controlado en {} {}", request.getMethod(), request.getRequestURI(), error);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, INTERNAL_ERROR, request);
    }

    private ResponseEntity<ApiErrorResponse> build(HttpStatus status, String message, HttpServletRequest request) {
        return ResponseEntity
            .status(status)
            .body(new ApiErrorResponse(Instant.now(), status.value(), message, request.getRequestURI()));
    }

    private String safeClientMessage(ResponseStatusException error) {
        String reason = error.getReason();
        return reason == null || reason.isBlank() ? BAD_REQUEST : reason;
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
