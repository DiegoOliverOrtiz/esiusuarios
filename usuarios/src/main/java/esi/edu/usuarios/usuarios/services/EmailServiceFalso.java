package esi.edu.usuarios.usuarios.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EmailServiceFalso extends EmailService {
    private static final Logger logger = LoggerFactory.getLogger(EmailServiceFalso.class);

    @Override
    public void sendEmail(String destinatario, Object...parametros) {
        logger.info("Correo preparado para {} ({} campos)", destinatario, parametros.length / 2);
    }
}
