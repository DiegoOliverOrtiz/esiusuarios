package esi.edu.usuarios.usuarios.auxiliares;

import esi.edu.usuarios.usuarios.services.EmailService;

public class Manager {
    private static Manager instance;
    private EmailService emailService;

    private Manager() {
        this.emailService = new EmailService();
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
