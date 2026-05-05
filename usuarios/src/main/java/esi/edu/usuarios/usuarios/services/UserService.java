package esi.edu.usuarios.usuarios.services;

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

    private final UserDao userDao;
    private final PasswordPolicy passwordPolicy;
    private final BCryptPasswordEncoder passwordEncoder;

    public UserService(UserDao userDao, PasswordPolicy passwordPolicy) {
        this.userDao = userDao;
        this.passwordPolicy = passwordPolicy;
        this.passwordEncoder = new BCryptPasswordEncoder(12);
    }

    public String login(String name, String password) {
        String normalizedLogin = normalize(name);
        Optional<User> user = this.userDao.findByEmail(normalizedLogin);

        if (user.isEmpty()) {
            user = this.userDao.findByUsername(normalizedLogin);
        }

        return user
            .filter(User::isConfirmed)
            .filter(foundUser -> passwordEncoder.matches(password, foundUser.getPassword()))
            .map(foundUser -> "Login exitoso")
            .orElse(null);
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
        if (this.userDao.existsByEmail(request.getEmail())) {
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
        newUser.setConfirmed(true);

        try {
            return this.userDao.save(newUser);
        } catch (DataIntegrityViolationException e) {
            throw new IllegalArgumentException("Ya existe una cuenta con esos datos.");
        }
    }

    private void normalizeRequest(RegisterUserRequest request) {
        request.setNombre(trim(request.getNombre()));
        request.setApellidos(trim(request.getApellidos()));
        request.setEmail(normalize(request.getEmail()));
        request.setUsername(optionalNormalize(request.getUsername()));
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
        return trim(value).toLowerCase(Locale.ROOT);
    }

    private String optionalNormalize(String value) {
        String trimmed = trim(value);
        return trimmed.isBlank() ? null : trimmed.toLowerCase(Locale.ROOT);
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
