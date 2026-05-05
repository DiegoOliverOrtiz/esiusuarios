package esi.edu.usuarios.usuarios.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;

import esi.edu.usuarios.usuarios.dao.PasswordResetTokenDao;
import esi.edu.usuarios.usuarios.dao.UserDao;
import esi.edu.usuarios.usuarios.dto.PasswordResetConfirmRequest;
import esi.edu.usuarios.usuarios.dto.PasswordResetRequest;
import esi.edu.usuarios.usuarios.dto.RegisterUserRequest;
import esi.edu.usuarios.usuarios.model.PasswordResetToken;
import esi.edu.usuarios.usuarios.model.User;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:password-reset-tests;MODE=MSSQLServer;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "app.frontend-base-url=http://localhost:4200"
})
class PasswordResetServiceTests {
    @Autowired
    private PasswordResetService passwordResetService;

    @Autowired
    private UserService userService;

    @Autowired
    private UserDao userDao;

    @Autowired
    private PasswordResetTokenDao tokenDao;

    @BeforeEach
    void setUp() {
        tokenDao.deleteAll();
        userDao.deleteAll();
    }

    @Test
    void requestWithExistingEmailCreatesExpiringToken() {
        User user = createUser("reset.existing@example.com", "resetexisting");
        PasswordResetRequest request = request("RESET.EXISTING@example.com ");

        passwordResetService.requestReset(request, "127.0.0.1", "test");

        PasswordResetToken token = tokenDao.findAll().get(0);
        assertEquals(user.getId(), token.getUserId());
        assertTrue(token.getFechaExpiracion().isAfter(Instant.now()));
        assertFalse(token.isUsado());
    }

    @Test
    void requestWithUnknownEmailDoesNotRevealOrCreateToken() {
        passwordResetService.requestReset(request("unknown@example.com"), "127.0.0.1", "test");

        assertTrue(tokenDao.findAll().isEmpty());
    }

    @Test
    void expiredTokenCannotChangePassword() {
        User user = createUser("expired@example.com", "expireduser");
        String token = createToken(user, Instant.now().minusSeconds(60), false);

        assertFalse(passwordResetService.validateToken(token));
        assertThrows(ResponseStatusException.class,
            () -> passwordResetService.confirmReset(confirm(token, "Cambio#Fuerte81!", "Cambio#Fuerte81!")));
    }

    @Test
    void usedTokenCannotBeReused() {
        User user = createUser("used@example.com", "useduser");
        String token = createToken(user, Instant.now().plusSeconds(900), true);

        assertFalse(passwordResetService.validateToken(token));
        assertThrows(ResponseStatusException.class,
            () -> passwordResetService.confirmReset(confirm(token, "Cambio#Fuerte81!", "Cambio#Fuerte81!")));
    }

    @Test
    void weakCommonPersonalAndMismatchedPasswordsAreRejected() {
        User user = createUser("policy@example.com", "policyuser");

        assertThrows(ResponseStatusException.class,
            () -> passwordResetService.confirmReset(confirm(createToken(user, Instant.now().plusSeconds(900), false), "Aa1!", "Aa1!")));
        assertThrows(ResponseStatusException.class,
            () -> passwordResetService.confirmReset(confirm(createToken(user, Instant.now().plusSeconds(900), false), "Password123!", "Password123!")));
        assertThrows(ResponseStatusException.class,
            () -> passwordResetService.confirmReset(confirm(createToken(user, Instant.now().plusSeconds(900), false), "Policy#Fuerte81!", "Policy#Fuerte81!")));
        assertThrows(ResponseStatusException.class,
            () -> passwordResetService.confirmReset(confirm(createToken(user, Instant.now().plusSeconds(900), false), "Cambio#Fuerte81!", "Cambio#Fuerte82!")));
    }

    @Test
    void validPasswordIsHashedAndLoginUsesNewPasswordOnly() {
        User user = createUser("valid.reset@example.com", "validreset");
        String oldHash = user.getPassword();
        String token = createToken(user, Instant.now().plusSeconds(900), false);

        passwordResetService.confirmReset(confirm(token, "Cambio#Fuerte81!", "Cambio#Fuerte81!"));

        User updated = userDao.findByEmail("valid.reset@example.com").orElseThrow();
        assertNotEquals("Cambio#Fuerte81!", updated.getPassword());
        assertNotEquals(oldHash, updated.getPassword());
        assertEquals("Login exitoso", userService.login("valid.reset@example.com", "Cambio#Fuerte81!"));
        assertEquals(null, userService.login("valid.reset@example.com", "Inicio#Fuerte79!"));
        assertFalse(passwordResetService.validateToken(token));
    }

    private User createUser(String email, String username) {
        RegisterUserRequest request = new RegisterUserRequest();
        request.setNombre("Laura");
        request.setApellidos("Martinez Sol");
        request.setEmail(email);
        request.setUsername(username);
        request.setPassword("Inicio#Fuerte79!");
        request.setConfirmPassword("Inicio#Fuerte79!");
        return userService.register(request);
    }

    private PasswordResetRequest request(String email) {
        PasswordResetRequest request = new PasswordResetRequest();
        request.setEmail(email);
        return request;
    }

    private PasswordResetConfirmRequest confirm(String token, String password, String confirmPassword) {
        PasswordResetConfirmRequest request = new PasswordResetConfirmRequest();
        request.setToken(token);
        request.setNewPassword(password);
        request.setConfirmPassword(confirmPassword);
        return request;
    }

    private String createToken(User user, Instant expiresAt, boolean used) {
        String plainToken = "token-seguro-de-prueba-" + System.nanoTime();
        PasswordResetToken token = new PasswordResetToken();
        token.setUserId(user.getId());
        token.setTokenHash(passwordResetService.hashToken(plainToken));
        token.setFechaCreacion(Instant.now());
        token.setFechaExpiracion(expiresAt);
        token.setUsado(used);
        tokenDao.save(token);
        return plainToken;
    }
}
