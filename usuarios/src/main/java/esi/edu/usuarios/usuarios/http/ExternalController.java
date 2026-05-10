package esi.edu.usuarios.usuarios.http;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import esi.edu.usuarios.usuarios.dto.MessageResponse;
import esi.edu.usuarios.usuarios.dto.TicketEmailRequest;
import esi.edu.usuarios.usuarios.dto.TokenCheckRequest;
import esi.edu.usuarios.usuarios.services.EmailServiceBrevo;
import esi.edu.usuarios.usuarios.services.UserService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/external")
@Validated
public class ExternalController {

    @Autowired
    private UserService service;

    @Autowired
    private EmailServiceBrevo emailService;

    @Value("${app.internal.api.secret:}")
    private String internalApiSecret;

    @PostMapping("/checkToken")
    public String checkToken(
        @RequestHeader(value = "X-Internal-Secret", required = false) String internalSecret,
        @Valid @RequestBody TokenCheckRequest request
    ) {
        validateInternalSecret(internalSecret);
        String userName = this.service.checkToken(request.getToken());
        if(userName == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token inválido");
        }
        return userName;
    }

    @PostMapping("/sendTicket")
    public MessageResponse sendTicket(
        @RequestHeader(value = "X-Internal-Secret", required = false) String internalSecret,
        @Valid @RequestBody TicketEmailRequest request
    ) {
        validateInternalSecret(internalSecret);
        emailService.sendTicketEmail(request.getTo(), request.getSubject(), request.getHtml());
        return new MessageResponse("Entradas enviadas correctamente.");
    }

    private void validateInternalSecret(String internalSecret) {
        if (internalApiSecret == null || internalApiSecret.isBlank()) {
            return;
        }
        if (!internalApiSecret.equals(internalSecret)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso no permitido");
        }
    }
}
