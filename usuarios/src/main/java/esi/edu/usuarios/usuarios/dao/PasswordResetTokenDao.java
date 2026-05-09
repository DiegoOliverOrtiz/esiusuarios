package esi.edu.usuarios.usuarios.dao;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.data.repository.query.Param;

import esi.edu.usuarios.usuarios.model.PasswordResetToken;

@RepositoryRestResource(exported = false)
public interface PasswordResetTokenDao extends JpaRepository<PasswordResetToken, Long> {
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    void deleteByUserId(Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE PasswordResetToken t SET t.usado = true, t.fechaUso = :fechaUso WHERE t.userId = :userId")
    void markActiveTokensAsUsed(@Param("userId") Long userId, @Param("fechaUso") Instant fechaUso);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE PasswordResetToken t SET t.usado = true, t.fechaUso = :fechaUso WHERE t.usado = false AND t.fechaExpiracion <= :fechaUso")
    int markExpiredTokensAsUsed(@Param("fechaUso") Instant fechaUso);
}
