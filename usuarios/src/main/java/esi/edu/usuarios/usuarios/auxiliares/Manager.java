package esi.edu.usuarios.usuarios.auxiliares;

import esi.edu.usuarios.usuarios.services.EmailService;
import esi.edu.usuarios.usuarios.services.EmailServiceFalso;

public class Manager {
    private static Manager instance;
    private EmailService emailService;

    private Manager() {
        this.emailService = new EmailServiceFalso();
    }

    public synchronized static Manager getInstance() {
        if(instance == null) {
            instance = new Manager();
        }
        return instance;
    }

    public EmailService getEmailService(){
        return this.emailService;
    }
}
