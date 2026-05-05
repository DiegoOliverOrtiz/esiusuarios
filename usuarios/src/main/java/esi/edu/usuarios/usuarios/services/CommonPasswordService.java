package esi.edu.usuarios.usuarios.services;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

@Service
public class CommonPasswordService {
    private Set<String> commonPasswords = Collections.emptySet();

    @PostConstruct
    public void load() throws IOException {
        ClassPathResource resource = new ClassPathResource("security/common-passwords.txt");

        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)
        )) {
            commonPasswords = reader.lines()
                .map(line -> line.trim().toLowerCase(Locale.ROOT))
                .filter(line -> !line.isBlank())
                .collect(Collectors.toUnmodifiableSet());
        }
    }

    public boolean isCommon(String password) {
        if (password == null) {
            return true;
        }

        String normalized = password.trim().toLowerCase(Locale.ROOT);
        return commonPasswords.contains(normalized);
    }
}
