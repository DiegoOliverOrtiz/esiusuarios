package esi.edu.usuarios.usuarios.services;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import esi.edu.usuarios.usuarios.dto.RegisterUserRequest;

class PasswordPolicyTests {
    private PasswordPolicy passwordPolicy;

    @BeforeEach
    void setUp() throws Exception {
        CommonPasswordService commonPasswordService = new CommonPasswordService();
        commonPasswordService.load();
        passwordPolicy = new PasswordPolicy(commonPasswordService);
    }

    @Test
    void acceptsStrongPasswordWithoutPersonalInfo() {
        assertDoesNotThrow(() -> passwordPolicy.validate(request("Ana", "Lopez Ruiz", "alopez", "ana@example.com", "Entrada#2026A!", "Entrada#2026A!")));
    }

    @Test
    void rejectsPasswordShorterThanTwelveCharacters() {
        assertThrows(IllegalArgumentException.class,
            () -> passwordPolicy.validate(request("Ana", "Lopez", "alopez", "ana@example.com", "Aa1!short", "Aa1!short")));
    }

    @Test
    void rejectsPasswordWithoutRequiredCharacterTypes() {
        assertThrows(IllegalArgumentException.class,
            () -> passwordPolicy.validate(request("Ana", "Lopez", "alopez", "ana@example.com", "sinSimbolos2026", "sinSimbolos2026")));
    }

    @Test
    void rejectsPasswordContainingPersonalInfo() {
        assertThrows(IllegalArgumentException.class,
            () -> passwordPolicy.validate(request("Maria", "Garcia", "mgarcia", "maria.garcia@example.com", "Maria2026!Garcia", "Maria2026!Garcia")));
    }

    @Test
    void rejectsCommonPassword() {
        assertThrows(IllegalArgumentException.class,
            () -> passwordPolicy.validate(request("Ana", "Lopez", "alopez", "ana@example.com", "Password123!", "Password123!")));
    }

    @Test
    void rejectsMismatchedConfirmation() {
        assertThrows(IllegalArgumentException.class,
            () -> passwordPolicy.validate(request("Ana", "Lopez", "alopez", "ana@example.com", "Entrada#2026A!", "Entrada#2026B!")));
    }

    private RegisterUserRequest request(
        String nombre,
        String apellidos,
        String username,
        String email,
        String password,
        String confirmPassword
    ) {
        RegisterUserRequest request = new RegisterUserRequest();
        request.setNombre(nombre);
        request.setApellidos(apellidos);
        request.setUsername(username);
        request.setEmail(email);
        request.setPassword(password);
        request.setConfirmPassword(confirmPassword);
        return request;
    }
}
