package esi.edu.usuarios.usuarios.dao;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import esi.edu.usuarios.usuarios.model.User;

public interface UserDao extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    Optional<User> findByUsername(String username);

    Optional<User> findByToken(String token);

    Optional<User> findByConfirmationToken(String confirmationToken);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    default Optional<User> findByName(String name) {
        return findByEmail(name);
    }
}
