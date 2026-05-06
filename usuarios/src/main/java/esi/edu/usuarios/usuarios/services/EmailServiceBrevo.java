package esi.edu.usuarios.usuarios.services;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;

@Service
public class EmailServiceBrevo extends EmailService {
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${email.api.url:https://api.brevo.com/v3/smtp/email}")
    private String apiUrl;

    @Value("${email.api.key:}")
    private String apiKey;

    @Value("${email.sender.name:ESI Entradas}")
    private String senderName;

    @Value("${email.sender.address:no-reply@example.com}")
    private String senderAddress;

    @PostConstruct
    public void logEmailConfiguration() {
        if (apiKey == null || apiKey.isBlank()) {
            System.out.println("BREVO: email.api.key no esta configurada. No se enviaran correos reales.");
        } else {
            System.out.println("BREVO: envio HTTP configurado para " + apiUrl + " desde " + senderAddress);
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

        sendHttpEmail(email, "Solicitud de cambio de contrasena en ESI Entradas", html);
    }

    public void sendPasswordChangedEmail(String email) {
        String html = loadEmailTemplate("email-templates/password-changed.html")
            .replace("{{BRAND}}", "ESI Entradas");

        sendHttpEmail(email, "Contrasena actualizada en ESI Entradas", html);
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
            ResponseEntity<String> response = restTemplate.postForEntity(cleanApiUrl, requestEntity, String.class);
            HttpStatusCode status = response.getStatusCode();
            if (!status.is2xxSuccessful()) {
                throw new IllegalStateException("El proveedor de email ha rechazado el envio.");
            }
            System.out.println("BREVO: correo enviado correctamente a " + cleanTo);
        } catch (HttpStatusCodeException e) {
            String responseBody = e.getResponseBodyAsString();
            String detail = responseBody == null || responseBody.isBlank()
                ? "sin detalle de Brevo. Revisa que la API key sea SMTP/transaccional y que el remitente este verificado en Brevo."
                : responseBody;
            throw new IllegalStateException(
                "Brevo ha rechazado el envio. HTTP " + e.getStatusCode() + " - " + detail,
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

}
