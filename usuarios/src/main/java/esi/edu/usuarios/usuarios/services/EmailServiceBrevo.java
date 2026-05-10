package esi.edu.usuarios.usuarios.services;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import jakarta.annotation.PostConstruct;

@Service
public class EmailServiceBrevo extends EmailService {
    private static final Logger logger = LoggerFactory.getLogger(EmailServiceBrevo.class);

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${email.api.url:https://api.brevo.com/v3/smtp/email}")
    private String apiUrl;

    @Value("${email.allowed-hosts:api.brevo.com}")
    private String allowedEmailHosts;

    @Value("${email.api.key:}")
    private String apiKey;

    @Value("${email.sender.name:ESI Entradas}")
    private String senderName;

    @Value("${email.sender.address:no-reply@example.com}")
    private String senderAddress;

    @PostConstruct
    public void logEmailConfiguration() {
        if (apiKey == null || apiKey.isBlank()) {
            logger.info("BREVO: email.api.key no esta configurada. No se enviaran correos reales.");
        } else {
            logger.info("BREVO: envio HTTP configurado para {} desde {}", apiUrl, senderAddress);
        }
    }

    @Override
    public void sendEmail(String destinatario, Object...parametros) {
        String subject = getParametro(parametros, "subject", "ESI Entradas");
        String html = getParametro(parametros, "html", "");

        sendHttpEmail(destinatario, subject, html);
    }

    public void sendPasswordResetEmail(String email, String resetLink) {
        String html = loadEmailTemplate("email-templates/password-reset.html")
            .replace("{{RESET_LINK}}", resetLink)
            .replace("{{BRAND}}", "ESI Entradas");

        sendHttpEmail(email, "Solicitud de cambio de contraseña en ESI Entradas", html);
    }

    public void sendPasswordChangedEmail(String email) {
        String html = loadEmailTemplate("email-templates/password-changed.html")
            .replace("{{BRAND}}", "ESI Entradas");

        sendHttpEmail(email, "Contraseña actualizada en ESI Entradas", html);
    }

    public void sendTicketEmail(String email, String subject, String html) {
        sendHttpEmail(email, subject, html);
    }

    public Map<String, Object> safeStatus() {
        String cleanApiKey = clean(apiKey);
        String envMailApi = clean(System.getenv("MAIL_API"));
        return Map.of(
            "apiUrl", clean(apiUrl),
            "apiKeyConfigured", cleanApiKey != null && !cleanApiKey.isBlank(),
            "apiKeyLength", cleanApiKey == null ? 0 : cleanApiKey.length(),
            "apiKeyLooksLikeBrevo", cleanApiKey != null && cleanApiKey.startsWith("xkeysib-"),
            "apiKeyIsDummy", "dummy".equals(cleanApiKey),
            "envMailApiConfigured", envMailApi != null && !envMailApi.isBlank(),
            "envMailApiLength", envMailApi == null ? 0 : envMailApi.length(),
            "envMailApiLooksLikeBrevo", envMailApi != null && envMailApi.startsWith("xkeysib-"),
            "senderName", clean(senderName),
            "senderAddress", clean(senderAddress)
        );
    }

    private void sendHttpEmail(String to, String subject, String htmlBody) {
        String cleanApiKey = clean(apiKey);
        String cleanApiUrl = clean(apiUrl);
        String cleanSenderName = clean(senderName);
        String cleanSenderAddress = clean(senderAddress);
        String cleanTo = clean(to);

        if (cleanApiKey == null || cleanApiKey.isBlank()) {
            throw new IllegalStateException("Falta configurar email.api.key para enviar correos reales.");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set("api-key", cleanApiKey);

        Map<String, Object> requestBody = Map.of(
            "sender", Map.of("name", cleanSenderName, "email", cleanSenderAddress),
            "to", List.of(Map.of("email", cleanTo)),
            "subject", subject,
            "htmlContent", htmlBody
        );

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(validatedUrl(cleanApiUrl), requestEntity, String.class);
            HttpStatusCode status = response.getStatusCode();
            if (!status.is2xxSuccessful()) {
                throw new IllegalStateException("El proveedor de email ha rechazado el envio.");
            }
            logger.info("BREVO: correo enviado correctamente a {}", cleanTo);
        } catch (HttpStatusCodeException e) {
            logger.warn("BREVO: proveedor rechazo el envio. status={}", e.getStatusCode());
            throw new IllegalStateException(
                "Brevo ha rechazado el envio. Revisa que la API key sea SMTP/transaccional y que el remitente este verificado.",
                e
            );
        } catch (RestClientException e) {
            throw new IllegalStateException("No se pudo enviar el correo mediante la API externa.", e);
        }
    }

    private String loadEmailTemplate(String path) {
        try {
            ClassPathResource resource = new ClassPathResource(path);
            return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo cargar la plantilla de email " + path, e);
        }
    }

    private String getParametro(Object[] parametros, String key, String defaultValue) {
        for (int i = 0; i + 1 < parametros.length; i += 2) {
            if (key.equals(parametros[i])) {
                return String.valueOf(parametros[i + 1]);
            }
        }

        return defaultValue;
    }

    private String clean(String value) {
        return value == null ? null : value.trim();
    }

    private String validatedUrl(String value) {
        URI uri = URI.create(value == null ? "" : value.trim());
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (scheme == null || host == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "URL de email no valida");
        }
        if (!allowedHosts().contains(host.toLowerCase(Locale.ROOT))) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Host de email no permitido");
        }
        return value.trim();
    }

    private Set<String> allowedHosts() {
        return Arrays.stream(allowedEmailHosts.split(","))
            .map(host -> host.trim().toLowerCase(Locale.ROOT))
            .filter(host -> !host.isBlank())
            .collect(Collectors.toSet());
    }

}
