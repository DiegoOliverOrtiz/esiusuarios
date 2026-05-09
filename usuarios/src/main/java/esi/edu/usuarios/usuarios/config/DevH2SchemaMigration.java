package esi.edu.usuarios.usuarios.config;

import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public class DevH2SchemaMigration {
    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    public DevH2SchemaMigration(JdbcTemplate jdbcTemplate, DataSource dataSource) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
    }

    @PostConstruct
    public void migrate() {
        if (!isH2Database()) {
            return;
        }

        addColumn("users", "account_locked_until", "timestamp with time zone");
        addColumn("users", "failed_login_attempts", "integer not null default 0");
        addColumn("users", "session_token_expires_at", "timestamp with time zone");
        addColumn("users", "two_factor_secret", "varchar(64)");
        addColumn("users", "two_factor_enabled", "boolean not null default false");
        addColumn("users", "dni_nie_encrypted", "varchar(512)");
        addColumn("users", "telefono_encrypted", "varchar(512)");
        addColumn("users", "direccion_encrypted", "varchar(1024)");
    }

    private boolean isH2Database() {
        try (var connection = dataSource.getConnection()) {
            return connection.getMetaData().getURL().startsWith("jdbc:h2:");
        } catch (Exception e) {
            return false;
        }
    }

    private void addColumn(String table, String column, String definition) {
        jdbcTemplate.execute("alter table if exists " + table + " add column if not exists " + column + " " + definition);
    }
}
