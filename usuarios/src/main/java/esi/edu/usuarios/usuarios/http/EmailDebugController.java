package esi.edu.usuarios.usuarios.http;

import java.time.Duration;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import esi.edu.usuarios.usuarios.services.EmailServiceBrevo;
import esi.edu.usuarios.usuarios.services.RateLimiterService;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@Profile("dev")
@RequestMapping("/dev/email")
public class EmailDebugController {
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final EmailServiceBrevo emailService;
    private final RateLimiterService rateLimiterService;

    public EmailDebugController(EmailServiceBrevo emailService, RateLimiterService rateLimiterService) {
        this.emailService = emailService;
        this.rateLimiterService = rateLimiterService;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return emailService.safeStatus();
    }

    @GetMapping("/send-test")
    public ResponseEntity<Map<String, String>> sendTest(@RequestParam String to, HttpServletRequest request) {
        rateLimiterService.check("dev-email-send-test", clientIp(request) + "|" + to, 3, Duration.ofMinutes(15));

        String email = to == null ? "" : to.trim();
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Usa un correo destinatario real, por ejemplo /dev/email/send-test?to=nombre@email.com");
        }

        try {
            emailService.sendEmail(
                email,
                "subject", "Prueba de correo - ESI Entradas",
                "html", "<h1>ESI Entradas</h1><p>Si recibes este correo, Brevo esta funcionando correctamente.</p>"
            );
            return ResponseEntity.ok(Map.of("message", "Correo de prueba enviado"));
        } catch (RuntimeException e) {
            return ResponseEntity
                .status(HttpStatus.BAD_GATEWAY)
                .body(Map.of(
                    "message", "Brevo rechazo el envio o no se pudo conectar.",
                    "detail", safeDetail(e.getMessage())
                ));
        }
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String safeDetail(String detail) {
        if (detail == null || detail.isBlank()) {
            return "Sin detalle.";
        }
        return detail.replaceAll("xkeysib-[A-Za-z0-9_-]+", "xkeysib-***");
    }
}
