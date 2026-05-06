package esi.edu.usuarios.usuarios.config;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
@Profile("dev")
public class DevH2SchemaMigration {
    private final JdbcTemplate jdbcTemplate;

    public DevH2SchemaMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void migrate() {
        addColumn("users", "account_locked_until", "timestamp with time zone");
        addColumn("users", "failed_login_attempts", "integer not null default 0");
        addColumn("users", "dni_nie_encrypted", "varchar(512)");
        addColumn("users", "telefono_encrypted", "varchar(512)");
        addColumn("users", "direccion_encrypted", "varchar(1024)");
    }

    private void addColumn(String table, String column, String definition) {
        jdbcTemplate.execute("alter table if exists " + table + " add column if not exists " + column + " " + definition);
    }
}
