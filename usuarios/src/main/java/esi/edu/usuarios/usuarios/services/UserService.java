package esi.edu.usuarios.usuarios.services;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import esi.edu.usuarios.usuarios.dao.UserDao;
import esi.edu.usuarios.usuarios.dto.RegisterUserRequest;
import esi.edu.usuarios.usuarios.model.User;

@Service
public class UserService {
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final int MAX_FAILED_LOGIN_ATTEMPTS = 5;
    private static final Duration ACCOUNT_LOCK_DURATION = Duration.ofMinutes(15);

    private final UserDao userDao;
    private final PasswordPolicy passwordPolicy;
    private final RiskDataEncryptionService riskDataEncryptionService;
    private final BCryptPasswordEncoder passwordEncoder;

    public UserService(UserDao userDao, PasswordPolicy passwordPolicy, RiskDataEncryptionService riskDataEncryptionService) {
        this.userDao = userDao;
        this.passwordPolicy = passwordPolicy;
        this.riskDataEncryptionService = riskDataEncryptionService;
        this.passwordEncoder = new BCryptPasswordEncoder(12);
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
            return Optional.empty();
        }

        User foundUser = user.get();
        if (isAccountLocked(foundUser)) {
            return Optional.empty();
        }

        if (!passwordMatches(foundUser, password)) {
            registerFailedLogin(foundUser);
            return Optional.empty();
        }

        resetFailedLogins(foundUser);
        return Optional.of(foundUser);
    }

    public User startSession(User user) {
        user.setToken(UUID.randomUUID().toString());
        return this.userDao.save(user);
    }

    public void logout(String token) {
        if (token == null || token.isBlank()) {
            return;
        }

        this.userDao.findByToken(token).ifPresent(user -> {
            user.setToken(null);
            this.userDao.save(user);
        });
    }

    public Optional<User> findBySessionToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return this.userDao.findByToken(token);
    }

    public String checkToken(String token) {
        return this.userDao.findByToken(token)
            .map(User::getEmail)
            .orElse(null);
    }

    public String confirmRegistration(String token) {
        User user = this.userDao.findByConfirmationToken(token)
            .orElseThrow(() -> new IllegalArgumentException("Token de confirmacion no valido."));

        user.setConfirmed(true);
        user.setConfirmationToken(null);
        this.userDao.save(user);

        return "Cuenta confirmada correctamente.";
    }

    public User register(RegisterUserRequest request) {
        normalizeRequest(request);
        validateRequiredFields(request);

        if (!EMAIL_PATTERN.matcher(request.getEmail()).matches()) {
            throw new IllegalArgumentException("El correo no tiene un formato valido.");
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
            return this.userDao.save(newUser);
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

    private void validateRequiredFields(RegisterUserRequest request) {
        if (isBlank(request.getNombre())
            || isBlank(request.getApellidos())
            || isBlank(request.getEmail())
            || isBlank(request.getPassword())
            || isBlank(request.getConfirmPassword())) {
            throw new IllegalArgumentException("Faltan campos obligatorios.");
        }
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
