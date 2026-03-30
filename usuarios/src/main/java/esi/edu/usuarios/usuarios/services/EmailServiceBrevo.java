package esi.edu.usuarios.usuarios.services;

public class EmailServiceBrevo extends EmailService {
    @Override
    public void sendEmail(String destinatario, Object...parametros) {
        System.out.println("Enviando correo a " + destinatario);
        for(int i = 0; i < parametros.length; i+=2) {
            System.out.println(parametros[i] + ": " + parametros[i+1]);
        }
    }

}
