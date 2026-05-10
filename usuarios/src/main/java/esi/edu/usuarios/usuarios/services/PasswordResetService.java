package esi.edu.usuarios.usuarios.services;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import esi.edu.usuarios.usuarios.dao.PasswordHistoryDao;
import esi.edu.usuarios.usuarios.dao.PasswordResetTokenDao;
import esi.edu.usuarios.usuarios.dao.UserDao;
import esi.edu.usuarios.usuarios.dto.PasswordResetConfirmRequest;
import esi.edu.usuarios.usuarios.dto.PasswordResetRequest;
import esi.edu.usuarios.usuarios.model.PasswordHistory;
import esi.edu.usuarios.usuarios.model.PasswordResetToken;
import esi.edu.usuarios.usuarios.model.User;

@Service
public class PasswordResetService {
    public static final String GENERIC_REQUEST_MESSAGE = "Si el correo existe, enviaremos instrucciones para restablecer la contraseña.";
    private static final String INVALID_LINK_MESSAGE = "El enlace no es válido o ha caducado.";
    private static final String PASSWORD_POLICY_MESSAGE = "No se pudo establecer la contraseña. Verifique la política de seguridad.";
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Duration TOKEN_TTL = Duration.ofMinutes(15);
    private static final int MAX_REQUESTS_PER_WINDOW = 3;
    private static final int PASSWORD_HISTORY_LIMIT = 5;
    private static final Duration RATE_LIMIT_WINDOW = Duration.ofMinutes(15);

    private final Logger logger = LoggerFactory.getLogger(PasswordResetService.class);
    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, RateLimitBucket> requestBuckets = new ConcurrentHashMap<>();

    private final UserDao userDao;
    private final PasswordResetTokenDao tokenDao;
    private final PasswordHistoryDao passwordHistoryDao;
    private final PasswordPolicy passwordPolicy;
    private final EmailServiceBrevo emailService;
    private final BCryptPasswordEncoder passwordEncoder;
    private final String frontendBaseUrl;

    public PasswordResetService(
        UserDao userDao,
        PasswordResetTokenDao tokenDao,
        PasswordHistoryDao passwordHistoryDao,
        PasswordPolicy passwordPolicy,
        EmailServiceBrevo emailService,
        @Value("${app.frontend.url:${app.frontend-base-url:http://localhost:4200}}") String frontendBaseUrl
    ) {
        this.userDao = userDao;
        this.tokenDao = tokenDao;
        this.passwordHistoryDao = passwordHistoryDao;
        this.passwordPolicy = passwordPolicy;
        this.emailService = emailService;
        this.passwordEncoder = new BCryptPasswordEncoder(12);
        this.frontendBaseUrl = frontendBaseUrl;
    }

    @Transactional
    public void requestReset(PasswordResetRequest request, String ipAddress, String userAgent) {
        invalidateExpiredTokens();

        if (request == null) {
            return;
        }

        String normalizedEmail = normalize(request.getEmail());
        if (!EMAIL_PATTERN.matcher(normalizedEmail).matches()) {
            return;
        }

        String bucketKey = normalize(ipAddress) + "|" + normalizedEmail;
        if (isRateLimited(bucketKey)) {
            logger.warn("Solicitud de recuperacion limitada por frecuencia");
            return;
        }

        Optional<User> user = userDao.findByEmail(normalizedEmail);
        if (user.isEmpty()) {
            logger.info("Solicitud de recuperacion recibida para correo no registrado");
            return;
        }

        tokenDao.markActiveTokensAsUsed(user.get().getId(), Instant.now());

        String plainToken = generateToken();
        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setUserId(user.get().getId());
        resetToken.setTokenHash(hashToken(plainToken));
        resetToken.setFechaCreacion(Instant.now());
        resetToken.setFechaExpiracion(Instant.now().plus(TOKEN_TTL));
        resetToken.setIpSolicitud(truncate(ipAddress, 80));
        resetToken.setUserAgent(truncate(userAgent, 255));
        tokenDao.save(resetToken);

        sendRecoveryEmail(user.get(), plainToken);
        logger.info("Registro de recuperacion generado con id {}", resetToken.getId());
    }

    @Transactional(readOnly = true)
    public boolean validateToken(String token) {
        return findUsableToken(token).isPresent();
    }

    @Transactional
    public void confirmReset(PasswordResetConfirmRequest request) {
        invalidateExpiredTokens();

        PasswordResetToken resetToken = findUsableToken(request.getToken())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_LINK_MESSAGE));

        User user = userDao.findById(resetToken.getUserId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_LINK_MESSAGE));

        if (!confirmEmailMatchesTokenOwner(request, user)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_LINK_MESSAGE);
        }

        try {
            passwordPolicy.validateForUser(user, request.getNewPassword(), request.getConfirmPassword());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, PASSWORD_POLICY_MESSAGE);
        }

        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, PASSWORD_POLICY_MESSAGE);
        }

        if (matchesRecentPassword(user.getId(), request.getNewPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, PASSWORD_POLICY_MESSAGE);
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setToken(null);
        user.setSessionTokenExpiresAt(null);
        userDao.save(user);
        recordPasswordHistory(user);

        Instant usedAt = Instant.now();
        resetToken.setUsado(true);
        resetToken.setFechaUso(usedAt);
        tokenDao.save(resetToken);
        tokenDao.markActiveTokensAsUsed(user.getId(), usedAt);

        sendConfirmationEmail(user);
        logger.info("Contrasena actualizada correctamente para usuario {}", user.getId());
    }

    @Scheduled(fixedDelayString = "${app.password-reset.cleanup-delay-ms:300000}")
    @Transactional
    public void invalidateExpiredTokens() {
        int updated = tokenDao.markExpiredTokensAsUsed(Instant.now());
        if (updated > 0) {
            logger.info("Registros de recuperacion caducados invalidados: {}", updated);
        }
    }

    private Optional<PasswordResetToken> findUsableToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }

        return tokenDao.findByTokenHash(hashToken(token.trim()))
            .filter(found -> !found.isUsado())
            .filter(found -> found.getFechaExpiracion().isAfter(Instant.now()));
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                result.append(String.format("%02x", value));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("No se pudo inicializar el hash de token", e);
        }
    }

    private void sendRecoveryEmail(User user, String plainToken) {
        String link = frontendBaseUrl + "/reset-password?token=" + plainToken;
        try {
            emailService.sendPasswordResetEmail(user.getEmail(), link);
        } catch (RuntimeException e) {
            logger.error("No se pudo enviar el email de recuperacion para usuario {}: {}", user.getId(), e.getMessage());
        }
    }

    private void sendConfirmationEmail(User user) {
        try {
            emailService.sendPasswordChangedEmail(user.getEmail());
        } catch (RuntimeException e) {
            logger.error("No se pudo enviar el email de confirmacion para usuario {}: {}", user.getId(), e.getMessage());
        }
    }

    private boolean isRateLimited(String key) {
        Instant now = Instant.now();
        RateLimitBucket bucket = requestBuckets.compute(key, (ignored, current) -> {
            if (current == null || current.windowStart.plus(RATE_LIMIT_WINDOW).isBefore(now)) {
                return new RateLimitBucket(now, 1);
            }
            return new RateLimitBucket(current.windowStart, current.count + 1);
        });
        return bucket.count > MAX_REQUESTS_PER_WINDOW;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private boolean confirmEmailMatchesTokenOwner(PasswordResetConfirmRequest request, User user) {
        String requestedEmail = normalize(request.getEmail());
        if (requestedEmail.isBlank()) {
            return true;
        }
        return requestedEmail.equals(normalize(user.getEmail()));
    }

    private boolean matchesRecentPassword(Long userId, String rawPassword) {
        return passwordHistoryDao.findTop5ByUserIdOrderByCreatedAtDescIdDesc(userId).stream()
            .map(PasswordHistory::getPasswordHash)
            .anyMatch(hash -> passwordEncoder.matches(rawPassword, hash));
    }

    private void recordPasswordHistory(User user) {
        passwordHistoryDao.save(new PasswordHistory(user.getId(), user.getPassword(), Instant.now()));

        List<PasswordHistory> entries = passwordHistoryDao.findByUserIdOrderByCreatedAtDescIdDesc(user.getId());
        if (entries.size() <= PASSWORD_HISTORY_LIMIT) {
            return;
        }

        passwordHistoryDao.deleteAll(entries.subList(PASSWORD_HISTORY_LIMIT, entries.size()));
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private record RateLimitBucket(Instant windowStart, int count) {
    }
}
