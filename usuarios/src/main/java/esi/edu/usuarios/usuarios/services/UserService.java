package esi.edu.usuarios.usuarios.services;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;

import esi.edu.usuarios.usuarios.dao.PasswordHistoryDao;
import esi.edu.usuarios.usuarios.dao.PasswordResetTokenDao;
import esi.edu.usuarios.usuarios.dao.UserDao;
import esi.edu.usuarios.usuarios.dto.RegisterUserRequest;
import esi.edu.usuarios.usuarios.dto.TwoFactorSetupResponse;
import esi.edu.usuarios.usuarios.dto.UpdateProfileRequest;
import esi.edu.usuarios.usuarios.dto.UserResponse;
import esi.edu.usuarios.usuarios.model.PasswordHistory;
import esi.edu.usuarios.usuarios.model.User;

@Service
public class UserService {
    private static final Logger logger = LoggerFactory.getLogger(UserService.class);
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern HUMAN_NAME_PATTERN = Pattern.compile("^[\\p{L}\\p{M}]+(?:[ .'-][\\p{L}\\p{M}]+)*$");
    private static final int MAX_FAILED_LOGIN_ATTEMPTS = 5;
    private static final Duration ACCOUNT_LOCK_DURATION = Duration.ofMinutes(15);
    private static final Duration TWO_FACTOR_CHALLENGE_TTL = Duration.ofMinutes(5);
    private static final String TWO_FACTOR_ISSUER = "ESI Entradas";

    private final UserDao userDao;
    private final PasswordResetTokenDao passwordResetTokenDao;
    private final PasswordHistoryDao passwordHistoryDao;
    private final PasswordPolicy passwordPolicy;
    private final RiskDataEncryptionService riskDataEncryptionService;
    private final BCryptPasswordEncoder passwordEncoder;
    private final GoogleAuthenticator googleAuthenticator;
    private final Map<String, PendingTwoFactorLogin> pendingTwoFactorLogins = new ConcurrentHashMap<>();

    @Value("${app.session.cookie.max-age-seconds:7200}")
    private long sessionMaxAgeSeconds;

    public UserService(
        UserDao userDao,
        PasswordResetTokenDao passwordResetTokenDao,
        PasswordHistoryDao passwordHistoryDao,
        PasswordPolicy passwordPolicy,
        RiskDataEncryptionService riskDataEncryptionService
    ) {
        this.userDao = userDao;
        this.passwordResetTokenDao = passwordResetTokenDao;
        this.passwordHistoryDao = passwordHistoryDao;
        this.passwordPolicy = passwordPolicy;
        this.riskDataEncryptionService = riskDataEncryptionService;
        this.passwordEncoder = new BCryptPasswordEncoder(12);
        this.googleAuthenticator = new GoogleAuthenticator();
    }

    public String login(String name, String password) {
        return authenticate(name, password)
            .map(foundUser -> "Login exitoso")
            .orElse(null);
    }

    public Optional<User> authenticate(String name, String password) {
        String normalizedLogin = normalizeEmail(name);
        Optional<User> user = findByCanonicalEmail(normalizedLogin);

        if (user.isEmpty()) {
            user = this.userDao.findByUsername(normalize(name));
        }

        if (user.isEmpty()) {
            logger.warn("Login fallido: usuario no encontrado");
            return Optional.empty();
        }

        User foundUser = user.get();
        if (isAccountLocked(foundUser)) {
            logger.warn("Login bloqueado temporalmente para usuario {}", foundUser.getId());
            return Optional.empty();
        }

        if (!passwordMatches(foundUser, password)) {
            registerFailedLogin(foundUser);
            logger.warn("Login fallido: credenciales invalidas para usuario {}", foundUser.getId());
            return Optional.empty();
        }

        resetFailedLogins(foundUser);
        logger.info("Login correcto para usuario {}", foundUser.getId());
        return Optional.of(foundUser);
    }

    public User startSession(User user) {
        String plainToken = UUID.randomUUID().toString();
        user.setToken(hashSessionToken(plainToken));
        user.setSessionTokenExpiresAt(Instant.now().plusSeconds(sessionMaxAgeSeconds));
        User saved = this.userDao.save(user);
        saved.setSessionToken(plainToken);
        logger.info("Sesion iniciada para usuario {} caducaEnSegundos={}", saved.getId(), sessionMaxAgeSeconds);
        return saved;
    }

    public String beginTwoFactorLogin(User user) {
        String challengeToken = UUID.randomUUID().toString();
        pendingTwoFactorLogins.put(challengeToken, new PendingTwoFactorLogin(user.getId(), Instant.now().plus(TWO_FACTOR_CHALLENGE_TTL)));
        logger.info("Reto 2FA iniciado para usuario {}", user.getId());
        return challengeToken;
    }

    public User completeTwoFactorLogin(String challengeToken, String code) {
        PendingTwoFactorLogin challenge = pendingTwoFactorLogins.remove(challengeToken);
        if (challenge == null || challenge.expiresAt().isBefore(Instant.now())) {
            logger.warn("Reto 2FA caducado o inexistente");
            throw new IllegalArgumentException("El reto 2FA ha caducado.");
        }

        User user = this.userDao.findById(challenge.userId())
            .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));

        if (!user.isTwoFactorEnabled() || !verifyTotp(user, code)) {
            logger.warn("Codigo 2FA inválido para usuario {}", user.getId());
            throw new IllegalArgumentException("Codigo 2FA inválido.");
        }

        logger.info("2FA verificado correctamente para usuario {}", user.getId());
        return startSession(user);
    }

    public TwoFactorSetupResponse setupTwoFactor(String token) {
        User user = authenticatedUser(token);
        GoogleAuthenticatorKey key = googleAuthenticator.createCredentials();

        user.setTwoFactorSecret(key.getKey());
        user.setTwoFactorEnabled(false);
        this.userDao.save(user);
        logger.info("Configuracion 2FA iniciada para usuario {}", user.getId());

        String otpAuthUrl = buildOtpAuthUrl(user.getEmail(), key.getKey());
        String qrUrl = "https://api.qrserver.com/v1/create-qr-code/?size=220x220&data="
            + URLEncoder.encode(otpAuthUrl, StandardCharsets.UTF_8);

        return new TwoFactorSetupResponse(qrUrl, key.getKey());
    }

    public UserResponse verifyAndEnableTwoFactor(String token, String code) {
        User user = authenticatedUser(token);
        if (!verifyTotp(user, code)) {
            throw new IllegalArgumentException("Codigo 2FA inválido.");
        }

        user.setTwoFactorEnabled(true);
        logger.info("2FA activado para usuario {}", user.getId());
        return toProfileResponse(this.userDao.save(user));
    }

    public UserResponse disableTwoFactor(String token) {
        User user = authenticatedUser(token);
        user.setTwoFactorEnabled(false);
        user.setTwoFactorSecret(null);
        logger.info("2FA desactivado para usuario {}", user.getId());
        return toProfileResponse(this.userDao.save(user));
    }

    public void logout(String token) {
        if (token == null || token.isBlank()) {
            return;
        }

        findBySessionToken(token).ifPresent(user -> {
            user.setToken(null);
            user.setSessionTokenExpiresAt(null);
            this.userDao.save(user);
            logger.info("Logout completado para usuario {}", user.getId());
        });
    }

    @Transactional
    public void cancelAccount(String token) {
        User user = authenticatedUser(token);
        passwordResetTokenDao.deleteByUserId(user.getId());
        passwordHistoryDao.deleteByUserId(user.getId());
        user.setToken(null);
        user.setSessionTokenExpiresAt(null);
        logger.warn("Cuenta cancelada para usuario {}", user.getId());
        this.userDao.delete(user);
    }

    public Optional<User> findBySessionToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String normalizedToken = token.trim();
        Optional<User> hashedMatch = this.userDao.findByToken(hashSessionToken(normalizedToken));
        if (hashedMatch.isPresent()) {
            return usableSession(hashedMatch.get());
        }

        return this.userDao.findByToken(normalizedToken)
            .flatMap(user -> usableSession(user).map(validUser -> migratePlainSessionToken(validUser, normalizedToken)));
    }

    public Optional<UserResponse> profileBySessionToken(String token) {
        return findBySessionToken(token).map(this::toProfileResponse);
    }

    public UserResponse updateProfile(String token, UpdateProfileRequest request) {
        User user = authenticatedUser(token);

        normalizeProfileRequest(request);
        validateProfileFields(request);

        if (!EMAIL_PATTERN.matcher(request.getEmail()).matches()) {
            throw new IllegalArgumentException("El correo no tiene un formato válido.");
        }
        Optional<User> existingEmail = findByCanonicalEmail(request.getEmail());
        if (existingEmail.isPresent() && !existingEmail.get().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Ya existe una cuenta con ese correo.");
        }
        if (request.getUsername() != null) {
            Optional<User> existingUsername = this.userDao.findByUsername(request.getUsername());
            if (existingUsername.isPresent() && !existingUsername.get().getId().equals(user.getId())) {
                throw new IllegalArgumentException("Ya existe una cuenta con ese alias.");
            }
        }

        user.setNombre(request.getNombre());
        user.setApellidos(request.getApellidos());
        user.setEmail(request.getEmail());
        user.setUsername(request.getUsername());
        user.setFechaNacimiento(request.getFechaNacimiento());
        return toProfileResponse(this.userDao.save(user));
    }

    public String checkToken(String token) {
        return findBySessionToken(token)
            .map(User::getEmail)
            .orElse(null);
    }

    public String confirmRegistration(String token) {
        User user = this.userDao.findByConfirmationToken(token)
            .orElseThrow(() -> new IllegalArgumentException("Token de confirmacion no válido."));

        user.setConfirmed(true);
        user.setConfirmationToken(null);
        this.userDao.save(user);

        return "Cuenta confirmada correctamente.";
    }

    public User register(RegisterUserRequest request) {
        normalizeRequest(request);
        validateRequiredFields(request);
        validatePersonalNames(request.getNombre(), request.getApellidos());

        if (!EMAIL_PATTERN.matcher(request.getEmail()).matches()) {
            throw new IllegalArgumentException("El correo no tiene un formato válido.");
        }
        if (findByCanonicalEmail(request.getEmail()).isPresent()) {
            throw new IllegalArgumentException("Ya existe una cuenta con ese correo.");
        }
        if (request.getUsername() != null && this.userDao.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Ya existe una cuenta con ese alias.");
        }

        this.passwordPolicy.validate(request);

        String hashedPassword = passwordEncoder.encode(request.getPassword());
        User newUser = new User(
            request.getNombre(),
            request.getApellidos(),
            request.getEmail(),
            request.getUsername(),
            request.getFechaNacimiento(),
            hashedPassword,
            UUID.randomUUID().toString(),
            null
        );
        newUser.setDniNieEncrypted(riskDataEncryptionService.encrypt(request.getDniNie()));
        newUser.setTelefonoEncrypted(riskDataEncryptionService.encrypt(request.getTelefono()));
        newUser.setDireccionEncrypted(riskDataEncryptionService.encrypt(request.getDireccion()));
        newUser.setConfirmed(true);

        try {
            User saved = this.userDao.save(newUser);
            passwordHistoryDao.save(new PasswordHistory(saved.getId(), saved.getPassword(), Instant.now()));
            logger.info("Cuenta registrada para usuario {}", saved.getId());
            return saved;
        } catch (DataIntegrityViolationException e) {
            throw new IllegalArgumentException("Ya existe una cuenta con esos datos.");
        }
    }

    private void normalizeRequest(RegisterUserRequest request) {
        request.setNombre(strip(request.getNombre()));
        request.setApellidos(strip(request.getApellidos()));
        request.setEmail(normalizeEmail(request.getEmail()));
        request.setUsername(optionalNormalize(request.getUsername()));
        request.setDniNie(optionalStrip(request.getDniNie()));
        request.setTelefono(optionalStrip(request.getTelefono()));
        request.setDireccion(optionalStrip(request.getDireccion()));
    }

    private void normalizeProfileRequest(UpdateProfileRequest request) {
        request.setNombre(strip(request.getNombre()));
        request.setApellidos(strip(request.getApellidos()));
        request.setEmail(normalizeEmail(request.getEmail()));
        request.setUsername(optionalNormalize(request.getUsername()));
    }

    private UserResponse toProfileResponse(User user) {
        return new UserResponse(user);
    }

    private User authenticatedUser(String token) {
        return findBySessionToken(token)
            .orElseThrow(() -> new IllegalArgumentException("Sesion no valida."));
    }

    private User migratePlainSessionToken(User user, String plainToken) {
        user.setToken(hashSessionToken(plainToken));
        user.setSessionTokenExpiresAt(Instant.now().plusSeconds(sessionMaxAgeSeconds));
        User saved = this.userDao.save(user);
        saved.setSessionToken(plainToken);
        logger.info("Sesion antigua migrada a hash para usuario {}", saved.getId());
        return saved;
    }

    private Optional<User> usableSession(User user) {
        Instant expiresAt = user.getSessionTokenExpiresAt();
        if (expiresAt == null || !expiresAt.isAfter(Instant.now())) {
            user.setToken(null);
            user.setSessionTokenExpiresAt(null);
            this.userDao.save(user);
            logger.warn("Sesion caducada o sin expiracion invalidada para usuario {}", user.getId());
            return Optional.empty();
        }
        return Optional.of(user);
    }

    private String hashSessionToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    private void validateRequiredFields(RegisterUserRequest request) {
        if (isBlank(request.getNombre())
            || isBlank(request.getApellidos())
            || isBlank(request.getEmail())
            || isBlank(request.getPassword())
            || isBlank(request.getConfirmPassword())) {
            throw new IllegalArgumentException("Faltan campos obligatorios.");
        }
    }

    private void validateProfileFields(UpdateProfileRequest request) {
        if (isBlank(request.getNombre()) || isBlank(request.getApellidos()) || isBlank(request.getEmail())) {
            throw new IllegalArgumentException("Faltan campos obligatorios.");
        }
        validatePersonalNames(request.getNombre(), request.getApellidos());
    }

    private void validatePersonalNames(String nombre, String apellidos) {
        if (!isValidHumanName(nombre) || !isValidHumanName(apellidos)) {
            throw new IllegalArgumentException("Nombre o apellidos no válidos.");
        }
    }

    private boolean isValidHumanName(String value) {
        return value != null && HUMAN_NAME_PATTERN.matcher(value).matches();
    }

    private String normalize(String value) {
        return strip(value).toLowerCase(Locale.ROOT);
    }

    private String optionalNormalize(String value) {
        String trimmed = strip(value);
        return trimmed.isBlank() ? null : trimmed.toLowerCase(Locale.ROOT);
    }

    private String optionalStrip(String value) {
        String trimmed = strip(value);
        return trimmed.isBlank() ? null : trimmed;
    }

    private String normalizeEmail(String value) {
        return strip(value)
            .replaceAll("\\p{Z}+", "")
            .replaceAll("\\p{Cntrl}+", "")
            .toLowerCase(Locale.ROOT);
    }

    private String strip(String value) {
        return value == null ? "" : value.strip();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private Optional<User> findByCanonicalEmail(String canonicalEmail) {
        Optional<User> exact = this.userDao.findByEmail(canonicalEmail);
        if (exact.isPresent()) {
            return exact;
        }

        return this.userDao.findAll().stream()
            .filter(user -> normalizeEmail(user.getEmail()).equals(canonicalEmail))
            .findFirst();
    }

    private boolean verifyTotp(User user, String codeText) {
        if (user.getTwoFactorSecret() == null || user.getTwoFactorSecret().isBlank()) {
            return false;
        }
        if (codeText == null || !codeText.matches("^\\d{6}$")) {
            return false;
        }
        return googleAuthenticator.authorize(user.getTwoFactorSecret(), Integer.parseInt(codeText));
    }

    private String buildOtpAuthUrl(String email, String secret) {
        String label = URLEncoder.encode(TWO_FACTOR_ISSUER + ":" + email, StandardCharsets.UTF_8);
        String issuer = URLEncoder.encode(TWO_FACTOR_ISSUER, StandardCharsets.UTF_8);
        return "otpauth://totp/" + label
            + "?secret=" + secret
            + "&issuer=" + issuer
            + "&algorithm=SHA1&digits=6&period=30";
    }

    private record PendingTwoFactorLogin(Long userId, Instant expiresAt) {}

    private boolean passwordMatches(User user, String rawPassword) {
        if (rawPassword == null || user.getPassword() == null) {
            return false;
        }

        String storedPassword = user.getPassword();
        if (storedPassword.startsWith("$2a$")
            || storedPassword.startsWith("$2b$")
            || storedPassword.startsWith("$2y$")) {
            return passwordEncoder.matches(rawPassword, storedPassword);
        }

        if (!storedPassword.equals(rawPassword)) {
            return false;
        }

        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setConfirmed(true);
        this.userDao.save(user);
        return true;
    }

    private boolean isAccountLocked(User user) {
        Instant lockedUntil = user.getAccountLockedUntil();
        if (lockedUntil == null) {
            return false;
        }

        if (lockedUntil.isAfter(Instant.now())) {
            return true;
        }

        user.setAccountLockedUntil(null);
        user.setFailedLoginAttempts(0);
        this.userDao.save(user);
        return false;
    }

    private void registerFailedLogin(User user) {
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);
        if (attempts >= MAX_FAILED_LOGIN_ATTEMPTS) {
            user.setAccountLockedUntil(Instant.now().plus(ACCOUNT_LOCK_DURATION));
            user.setToken(null);
            user.setSessionTokenExpiresAt(null);
            logger.warn("Cuenta bloqueada temporalmente por intentos fallidos para usuario {}", user.getId());
        }
        this.userDao.save(user);
    }

    private void resetFailedLogins(User user) {
        if (user.getFailedLoginAttempts() == 0 && user.getAccountLockedUntil() == null) {
            return;
        }
        user.setFailedLoginAttempts(0);
        user.setAccountLockedUntil(null);
        this.userDao.save(user);
    }
}
