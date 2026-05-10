package esi.edu.usuarios.usuarios.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import esi.edu.usuarios.usuarios.dao.PasswordResetTokenDao;
import esi.edu.usuarios.usuarios.dao.PasswordHistoryDao;
import esi.edu.usuarios.usuarios.dao.UserDao;
import esi.edu.usuarios.usuarios.dto.PasswordResetConfirmRequest;
import esi.edu.usuarios.usuarios.dto.PasswordResetRequest;
import esi.edu.usuarios.usuarios.dto.RegisterUserRequest;
import esi.edu.usuarios.usuarios.dto.UpdateProfileRequest;
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
    private static final Pattern BCRYPT_COST_12 = Pattern.compile("^\\$2[aby]\\$12\\$.*");
    private static final Pattern BASE64_URL_TOKEN = Pattern.compile("^[A-Za-z0-9_-]+$");

    @Autowired
    private PasswordResetService passwordResetService;

    @Autowired
    private UserService userService;

    @Autowired
    private UserDao userDao;

    @Autowired
    private PasswordResetTokenDao tokenDao;

    @Autowired
    private PasswordHistoryDao passwordHistoryDao;

    @DynamicPropertySource
    static void riskDataEncryptionKey(DynamicPropertyRegistry registry) {
        registry.add("app.risk-data.encryption-key", PasswordResetServiceTests::testKey);
    }

    @BeforeEach
    void setUp() {
        tokenDao.deleteAll();
        passwordHistoryDao.deleteAll();
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
    void generatedResetTokenIsLongUrlSafeAndRandomLooking() {
        String firstToken = ReflectionTestUtils.invokeMethod(passwordResetService, "generateToken");
        String secondToken = ReflectionTestUtils.invokeMethod(passwordResetService, "generateToken");

        assertTrue(firstToken.length() >= 32);
        assertTrue(secondToken.length() >= 32);
        assertTrue(BASE64_URL_TOKEN.matcher(firstToken).matches());
        assertTrue(BASE64_URL_TOKEN.matcher(secondToken).matches());
        assertNotEquals(firstToken, secondToken);
    }

    @Test
    void requestWithUnknownEmailDoesNotRevealOrCreateToken() {
        passwordResetService.requestReset(request("unknown@example.com"), "127.0.0.1", "test");

        assertTrue(tokenDao.findAll().isEmpty());
    }

    @Test
    void newResetRequestInvalidatesPreviousTokensForSameUser() {
        User user = createUser("latest.reset@example.com", "latestreset");
        String previousToken = createToken(user, Instant.now().plusSeconds(900), false);

        assertTrue(passwordResetService.validateToken(previousToken));

        passwordResetService.requestReset(request("latest.reset@example.com"), "127.0.0.1", "test");

        assertFalse(passwordResetService.validateToken(previousToken));
        long activeTokens = tokenDao.findAll().stream()
            .filter(token -> token.getUserId().equals(user.getId()))
            .filter(token -> !token.isUsado())
            .count();
        assertEquals(1, activeTokens);
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
    void expiredTokensAreMarkedAsUsedInDatabase() {
        User user = createUser("expired.db@example.com", "expireddb");
        createToken(user, Instant.now().minusSeconds(60), false);

        passwordResetService.invalidateExpiredTokens();

        PasswordResetToken token = tokenDao.findAll().get(0);
        assertTrue(token.isUsado());
        assertTrue(token.getFechaUso() != null);
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

    @Test
    void resetCannotReuseAnyOfLastFivePasswords() {
        User user = createUser("history.reset@example.com", "historyreset");
        String firstToken = createToken(user, Instant.now().plusSeconds(900), false);
        passwordResetService.confirmReset(confirm(firstToken, "Cambio#Fuerte81!", "Cambio#Fuerte81!"));

        String secondToken = createToken(userDao.findByEmail("history.reset@example.com").orElseThrow(), Instant.now().plusSeconds(900), false);

        assertThrows(ResponseStatusException.class,
            () -> passwordResetService.confirmReset(confirm(secondToken, "Inicio#Fuerte79!", "Inicio#Fuerte79!")));
        assertTrue(passwordResetService.validateToken(secondToken));
    }

    @Test
    void sessionTokenExpiresServerSideEvenIfCookieIsReused() {
        User user = userService.startSession(createUser("session.expired@example.com", "sessionexpired"));
        String sessionToken = user.getToken();

        assertEquals("session.expired@example.com", userService.checkToken(sessionToken));

        User persisted = userDao.findByEmail("session.expired@example.com").orElseThrow();
        persisted.setSessionTokenExpiresAt(Instant.now().minusSeconds(1));
        userDao.save(persisted);

        assertEquals(null, userService.checkToken(sessionToken));
        User updated = userDao.findByEmail("session.expired@example.com").orElseThrow();
        assertEquals(null, updated.getStoredTokenHash());
        assertEquals(null, updated.getSessionTokenExpiresAt());
    }

    @Test
    void tokenCannotBeConfirmedForDifferentEmail() {
        User owner = createUser("owner.reset@example.com", "ownerreset");
        User other = createUser("other.reset@example.com", "otherreset");
        String token = createToken(owner, Instant.now().plusSeconds(900), false);
        String otherOldHash = other.getPassword();

        PasswordResetConfirmRequest request = confirm(token, "Cambio#Fuerte81!", "Cambio#Fuerte81!");
        request.setEmail("other.reset@example.com");

        assertThrows(ResponseStatusException.class, () -> passwordResetService.confirmReset(request));
        assertEquals(otherOldHash, userDao.findByEmail("other.reset@example.com").orElseThrow().getPassword());
        assertTrue(passwordResetService.validateToken(token));
    }

    @Test
    void passwordResetInvalidatesActiveSession() {
        User user = userService.startSession(createUser("reset.session@example.com", "resetsession"));
        String sessionToken = user.getToken();
        String resetToken = createToken(user, Instant.now().plusSeconds(900), false);

        assertEquals("reset.session@example.com", userService.checkToken(sessionToken));

        passwordResetService.confirmReset(confirm(resetToken, "Cambio#Fuerte81!", "Cambio#Fuerte81!"));

        assertEquals(null, userService.checkToken(sessionToken));
    }

    @Test
    void cancelAccountDeletesUserAndPasswordResetTokens() {
        User user = userService.startSession(createUser("cancel@example.com", "canceluser"));
        String resetToken = createToken(user, Instant.now().plusSeconds(900), false);

        assertTrue(passwordResetService.validateToken(resetToken));

        userService.cancelAccount(user.getToken());

        assertTrue(userDao.findByEmail("cancel@example.com").isEmpty());
        assertEquals(null, userService.login("cancel@example.com", "Inicio#Fuerte79!"));
        assertFalse(passwordResetService.validateToken(resetToken));
        assertTrue(tokenDao.findAll().stream().noneMatch(token -> token.getUserId().equals(user.getId())));
        assertTrue(passwordHistoryDao.findByUserIdOrderByCreatedAtDescIdDesc(user.getId()).isEmpty());
    }

    @Test
    void registerUsesBCryptCostTwelveAndUniqueSaltPerUser() {
        User first = createUser("salt.one@example.com", "saltone");
        User second = createUser("salt.two@example.com", "salttwo");
        BCryptPasswordEncoder verifier = new BCryptPasswordEncoder();

        assertTrue(BCRYPT_COST_12.matcher(first.getPassword()).matches());
        assertTrue(BCRYPT_COST_12.matcher(second.getPassword()).matches());
        assertNotEquals(first.getPassword(), second.getPassword());
        assertTrue(verifier.matches("Inicio#Fuerte79!", first.getPassword()));
        assertTrue(verifier.matches("Inicio#Fuerte79!", second.getPassword()));
    }

    @Test
    void accountIsLockedAfterFiveFailedLoginAttempts() {
        createUser("locked@example.com", "lockeduser");

        for (int attempt = 0; attempt < 5; attempt++) {
            assertEquals(null, userService.login("locked@example.com", "Incorrecta#12345"));
        }

        User locked = userDao.findByEmail("locked@example.com").orElseThrow();
        assertEquals(5, locked.getFailedLoginAttempts());
        assertTrue(locked.getAccountLockedUntil() != null);
        assertEquals(null, userService.login("locked@example.com", "Inicio#Fuerte79!"));
    }

    @Test
    void successfulLoginBeforeLockResetsFailedAttempts() {
        createUser("reset.attempts@example.com", "resetattempts");

        assertEquals(null, userService.login("reset.attempts@example.com", "Incorrecta#12345"));
        assertEquals(null, userService.login("reset.attempts@example.com", "Incorrecta#12345"));
        assertEquals("Login exitoso", userService.login("reset.attempts@example.com", "Inicio#Fuerte79!"));

        User updated = userDao.findByEmail("reset.attempts@example.com").orElseThrow();
        assertEquals(0, updated.getFailedLoginAttempts());
        assertTrue(updated.getAccountLockedUntil() == null);
    }

    @Test
    void registerEncryptsRiskDataBeforePersisting() {
        RegisterUserRequest request = registerRequest("risk@example.com", "riskuser");
        request.setDniNie("12345678Z");
        request.setTelefono("+34 600 111 222");
        request.setDireccion("Calle Mayor 1");

        User saved = userService.register(request);

        assertNotEquals("12345678Z", saved.getDniNieEncrypted());
        assertNotEquals("+34 600 111 222", saved.getTelefonoEncrypted());
        assertNotEquals("Calle Mayor 1", saved.getDireccionEncrypted());
        assertTrue(saved.getDniNieEncrypted() != null);
        assertTrue(saved.getTelefonoEncrypted() != null);
        assertTrue(saved.getDireccionEncrypted() != null);
    }

    @Test
    void registerRejectsNamesWithNumbersSymbolsOrEmoji() {
        RegisterUserRequest nameWithNumber = registerRequest("bad.name@example.com", "badname");
        nameWithNumber.setNombre("Laura123");
        assertThrows(IllegalArgumentException.class, () -> userService.register(nameWithNumber));

        RegisterUserRequest surnameWithSymbol = registerRequest("bad.surname@example.com", "badsurname");
        surnameWithSymbol.setApellidos("Martinez @ Sol");
        assertThrows(IllegalArgumentException.class, () -> userService.register(surnameWithSymbol));

        RegisterUserRequest nameWithEmoji = registerRequest("emoji.name@example.com", "emojiname");
        nameWithEmoji.setNombre("Laura🙂");
        assertThrows(IllegalArgumentException.class, () -> userService.register(nameWithEmoji));
    }

    @Test
    void updateProfileRejectsNamesWithInvalidCharacters() {
        User user = userService.startSession(createUser("profile.name@example.com", "profilename"));
        UpdateProfileRequest request = updateProfileRequest("Ana", "Lopez99", "profile.name@example.com");

        assertThrows(IllegalArgumentException.class, () -> userService.updateProfile(user.getToken(), request));
    }

    @Test
    void registerAcceptsAccentsSpacesHyphensAndApostrophesInNames() {
        RegisterUserRequest request = registerRequest("valid.name@example.com", "validname");
        request.setNombre("Maria-Jose");
        request.setApellidos("O'Neill de la Cruz");

        User saved = userService.register(request);

        assertEquals("Maria-Jose", saved.getNombre());
        assertEquals("O'Neill de la Cruz", saved.getApellidos());
    }

    private User createUser(String email, String username) {
        return userService.register(registerRequest(email, username));
    }

    private RegisterUserRequest registerRequest(String email, String username) {
        RegisterUserRequest request = new RegisterUserRequest();
        request.setNombre("Laura");
        request.setApellidos("Martinez Sol");
        request.setEmail(email);
        request.setUsername(username);
        request.setPassword("Inicio#Fuerte79!");
        request.setConfirmPassword("Inicio#Fuerte79!");
        return request;
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

    private UpdateProfileRequest updateProfileRequest(String nombre, String apellidos, String email) {
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setNombre(nombre);
        request.setApellidos(apellidos);
        request.setEmail(email);
        request.setUsername("profilealias");
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

    private static String testKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }
}
