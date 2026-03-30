package esi.edu.usuarios.usuarios.services;

public abstract class EmailService {
    public EmailService() {}

    public abstract void sendEmail(String destinatario, Object... parametros);
}
