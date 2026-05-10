package esi.edu.usuarios.usuarios.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import esi.edu.usuarios.usuarios.model.PasswordHistory;

@RepositoryRestResource(exported = false)
public interface PasswordHistoryDao extends JpaRepository<PasswordHistory, Long> {
    List<PasswordHistory> findTop5ByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    List<PasswordHistory> findByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    void deleteByUserId(Long userId);
}
