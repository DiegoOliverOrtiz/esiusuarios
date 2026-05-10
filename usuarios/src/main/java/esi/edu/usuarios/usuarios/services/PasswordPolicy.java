package esi.edu.usuarios.usuarios.services;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import esi.edu.usuarios.usuarios.dto.RegisterUserRequest;
import esi.edu.usuarios.usuarios.model.User;

@Component
public class PasswordPolicy {
    private static final int MIN_LENGTH = 12;
    private static final Pattern UPPERCASE = Pattern.compile("[A-Z]");
    private static final Pattern LOWERCASE = Pattern.compile("[a-z]");
    private static final Pattern DIGIT = Pattern.compile("\\d");
    private static final Pattern SYMBOL = Pattern.compile("[^A-Za-z0-9]");
    private static final Pattern CONTROL_CHARS = Pattern.compile("\\p{Cntrl}");
    private static final Pattern SIMPLE_REPETITION = Pattern.compile("(.)\\1{7,}");
    private static final String GENERIC_POLICY_ERROR = "La contraseña no cumple la política de seguridad.";

    private final CommonPasswordService commonPasswordService;

    public PasswordPolicy(CommonPasswordService commonPasswordService) {
        this.commonPasswordService = commonPasswordService;
    }

    public void validate(RegisterUserRequest request) {
        List<String> errors = getErrors(request);

        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(GENERIC_POLICY_ERROR);
        }
    }

    public void validateForUser(User user, String password, String confirmPassword) {
        RegisterUserRequest request = new RegisterUserRequest();
        request.setNombre(user.getNombre());
        request.setApellidos(user.getApellidos());
        request.setUsername(user.getUsername());
        request.setEmail(user.getEmail());
        request.setFechaNacimiento(user.getFechaNacimiento());
        request.setPassword(password);
        request.setConfirmPassword(confirmPassword);

        validate(request);
    }

    public List<String> getErrors(RegisterUserRequest request) {
        List<String> errors = new ArrayList<>();
        String password = request.getPassword() == null ? "" : request.getPassword();
        String confirmPassword = request.getConfirmPassword() == null ? "" : request.getConfirmPassword();
        String normalizedPassword = normalize(password);

        if (password.length() < MIN_LENGTH) {
            errors.add("min_length");
        }
        if (!UPPERCASE.matcher(password).find()) {
            errors.add("uppercase");
        }
        if (!LOWERCASE.matcher(password).find()) {
            errors.add("lowercase");
        }
        if (!DIGIT.matcher(password).find()) {
            errors.add("digit");
        }
        if (!SYMBOL.matcher(password).find()) {
            errors.add("symbol");
        }
        if (!password.equals(confirmPassword)) {
            errors.add("match");
        }
        if (!password.equals(password.trim())) {
            errors.add("edge_spaces");
        }
        if (CONTROL_CHARS.matcher(password).find()) {
            errors.add("control_chars");
        }
        if (containsPersonalInfo(request, normalizedPassword)) {
            errors.add("personal_info");
        }
        if (commonPasswordService.isCommon(password) || containsObviousPattern(normalizedPassword)) {
            errors.add("common_or_obvious");
        }
        if (containsBirthYear(request.getFechaNacimiento(), normalizedPassword)) {
            errors.add("birth_year");
        }

        return errors;
    }

    private boolean containsPersonalInfo(RegisterUserRequest request, String normalizedPassword) {
        List<String> values = new ArrayList<>();
        values.add(request.getNombre());
        values.add(request.getApellidos());
        values.add(request.getUsername());

        if (request.getEmail() != null) {
            String localPart = request.getEmail().split("@")[0];
            values.add(localPart);
            for (String part : localPart.split("[._\\-+]")) {
                values.add(part);
            }
        }

        for (String value : values) {
            if (containsValue(normalizedPassword, value)) {
                return true;
            }
        }

        return false;
    }

    private boolean containsObviousPattern(String normalizedPassword) {
        return normalizedPassword.contains("123456")
            || normalizedPassword.contains("123456789")
            || normalizedPassword.contains("abcdef")
            || normalizedPassword.contains("qwerty")
            || normalizedPassword.contains("asdfgh")
            || normalizedPassword.contains("password")
            || normalizedPassword.contains("contrasena")
            || normalizedPassword.contains("admin")
            || normalizedPassword.contains("usuario")
            || SIMPLE_REPETITION.matcher(normalizedPassword).find();
    }

    private boolean containsBirthYear(LocalDate birthDate, String normalizedPassword) {
        return birthDate != null && normalizedPassword.contains(String.valueOf(birthDate.getYear()));
    }

    private boolean containsValue(String normalizedPassword, String value) {
        if (value == null || value.isBlank()) {
            return false;
        }

        String normalizedValue = normalize(value);
        if (normalizedValue.length() >= 3 && normalizedPassword.contains(normalizedValue)) {
            return true;
        }

        for (String part : normalizedValue.split("\\s+")) {
            if (part.length() >= 3 && normalizedPassword.contains(part)) {
                return true;
            }
        }

        return false;
    }

    private String normalize(String value) {
        String lower = value == null ? "" : value.toLowerCase(Locale.ROOT);
        return Normalizer.normalize(lower, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "");
    }
}
