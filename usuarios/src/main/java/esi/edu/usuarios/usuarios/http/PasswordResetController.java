package esi.edu.usuarios.usuarios.http;

import java.time.Duration;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import esi.edu.usuarios.usuarios.dto.MessageResponse;
import esi.edu.usuarios.usuarios.dto.PasswordResetConfirmRequest;
import esi.edu.usuarios.usuarios.dto.PasswordResetRequest;
import esi.edu.usuarios.usuarios.dto.TokenValidationResponse;
import esi.edu.usuarios.usuarios.services.PasswordResetService;
import esi.edu.usuarios.usuarios.services.RateLimiterService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/password-reset")
@Validated
public class PasswordResetController {
    private final PasswordResetService passwordResetService;
    private final RateLimiterService rateLimiterService;

    public PasswordResetController(PasswordResetService passwordResetService, RateLimiterService rateLimiterService) {
        this.passwordResetService = passwordResetService;
        this.rateLimiterService = rateLimiterService;
    }

    @PostMapping("/request")
    public ResponseEntity<MessageResponse> requestReset(
        @RequestBody(required = false) PasswordResetRequest request,
        HttpServletRequest httpRequest,
        @RequestHeader(value = "User-Agent", required = false) String userAgent
    ) {
        String ip = clientIp(httpRequest);
        rateLimiterService.check("password-reset-request", ip + "|" + emailFrom(request), 3, Duration.ofMinutes(15));
        passwordResetService.requestReset(request, ip, userAgent);
        return ResponseEntity
            .status(HttpStatus.ACCEPTED)
            .body(new MessageResponse(PasswordResetService.GENERIC_REQUEST_MESSAGE));
    }

    @GetMapping("/validate")
    public TokenValidationResponse validate(@RequestParam(required = false) String token, HttpServletRequest request) {
        rateLimiterService.check("password-reset-validate", clientIp(request), 20, Duration.ofMinutes(15));
        return new TokenValidationResponse(passwordResetService.validateToken(token));
    }

    @PostMapping("/confirm")
    public MessageResponse confirm(@Valid @RequestBody PasswordResetConfirmRequest request, HttpServletRequest httpRequest) {
        rateLimiterService.check(
            "password-reset-confirm",
            clientIp(httpRequest) + "|" + tokenRateKey(request.getToken()),
            5,
            Duration.ofMinutes(15)
        );
        passwordResetService.confirmReset(request);
        return new MessageResponse("Contraseña actualizada correctamente");
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<MessageResponse> handleResponseStatus(ResponseStatusException error) {
        return ResponseEntity
            .status(error.getStatusCode())
            .body(new MessageResponse(error.getReason() == null ? "Solicitud invalida" : error.getReason()));
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String emailFrom(PasswordResetRequest request) {
        return request == null || request.getEmail() == null ? "" : request.getEmail();
    }

    private String tokenRateKey(String token) {
        return token == null ? "" : Integer.toHexString(token.hashCode());
    }
}
