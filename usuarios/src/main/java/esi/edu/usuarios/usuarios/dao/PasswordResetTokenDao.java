package esi.edu.usuarios.usuarios.dao;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import esi.edu.usuarios.usuarios.model.PasswordResetToken;

public interface PasswordResetTokenDao extends JpaRepository<PasswordResetToken, Long> {
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("UPDATE PasswordResetToken t SET t.usado = true, t.fechaUso = :fechaUso WHERE t.userId = :userId AND t.usado = false")
    void markActiveTokensAsUsed(@Param("userId") Long userId, @Param("fechaUso") Instant fechaUso);

    @Modifying
    @Query("UPDATE PasswordResetToken t SET t.usado = true, t.fechaUso = :fechaUso WHERE t.usado = false AND t.fechaExpiracion <= :fechaUso")
    int markExpiredTokensAsUsed(@Param("fechaUso") Instant fechaUso);
}
