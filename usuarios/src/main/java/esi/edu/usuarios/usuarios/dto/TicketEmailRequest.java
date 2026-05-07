package esi.edu.usuarios.usuarios.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class TicketEmailRequest {
    @NotBlank
    @Email
    @Size(max = 255)
    private String to;

    @NotBlank
    @Size(max = 150)
    private String subject;

    @NotBlank
    @Size(max = 20000)
    private String html;

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getHtml() {
        return html;
    }

    public void setHtml(String html) {
        this.html = html;
    }
}
