package esi.edu.usuarios.usuarios.services;

public class EmailServiceFalso extends EmailService {
    @Override
    public void sendEmail(String destinatario, Object...parametros) {
        System.out.println("Correo preparado para " + destinatario + " (" + (parametros.length / 2) + " campos)");
    }
}
