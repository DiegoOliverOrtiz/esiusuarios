package esi.edu.usuarios.usuarios.http;

import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import esi.edu.usuarios.usuarios.services.EmailServiceBrevo;

@RestController
@Profile("dev")
@RequestMapping("/dev/email")
public class EmailDebugController {
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final EmailServiceBrevo emailService;

    public EmailDebugController(EmailServiceBrevo emailService) {
        this.emailService = emailService;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return emailService.safeStatus();
    }

    @GetMapping("/send-test")
    public Map<String, String> sendTest(@RequestParam String to) {
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
            return Map.of("message", "Correo de prueba enviado");
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, e.getMessage());
        }
    }
}
