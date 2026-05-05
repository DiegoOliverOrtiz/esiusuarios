package esi.edu.usuarios.usuarios.http;

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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/password-reset")
@Validated
public class PasswordResetController {
    private final PasswordResetService passwordResetService;

    public PasswordResetController(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/request")
    public ResponseEntity<MessageResponse> requestReset(
        @RequestBody(required = false) PasswordResetRequest request,
        HttpServletRequest httpRequest,
        @RequestHeader(value = "User-Agent", required = false) String userAgent
    ) {
        passwordResetService.requestReset(request, clientIp(httpRequest), userAgent);
        return ResponseEntity
            .status(HttpStatus.ACCEPTED)
            .body(new MessageResponse(PasswordResetService.GENERIC_REQUEST_MESSAGE));
    }

    @GetMapping("/validate")
    public TokenValidationResponse validate(@RequestParam(required = false) String token) {
        return new TokenValidationResponse(passwordResetService.validateToken(token));
    }

    @PostMapping("/confirm")
    public MessageResponse confirm(@Valid @RequestBody PasswordResetConfirmRequest request) {
        passwordResetService.confirmReset(request);
        return new MessageResponse("Contrasena actualizada correctamente");
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
}
