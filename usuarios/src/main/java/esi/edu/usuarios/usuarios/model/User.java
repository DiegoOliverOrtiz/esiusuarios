package esi.edu.usuarios.usuarios.model;

import java.time.LocalDate;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
    name = "users",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_users_email", columnNames = "email")
    }
)
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String nombre;

    @Column(nullable = false, length = 120)
    private String apellidos;

    @Column(nullable = false, length = 180)
    private String email;

    @Column(length = 60)
    private String username;

    private LocalDate fechaNacimiento;

    @Column(nullable = false, length = 100)
    private String password;

    @Column(nullable = false, length = 40)
    private String rol = "USER";

    @Column(nullable = false)
    private boolean confirmed = false;

    private String token;
    private Instant sessionTokenExpiresAt;
    @Transient
    private String sessionToken;
    private String confirmationToken;
    @Column(length = 64)
    private String twoFactorSecret;
    private Boolean twoFactorEnabled = false;
    private Integer failedLoginAttempts = 0;
    private Instant accountLockedUntil;

    @Column(length = 512)
    private String dniNieEncrypted;

    @Column(length = 512)
    private String telefonoEncrypted;

    @Column(length = 1024)
    private String direccionEncrypted;

    public User() {}

    public User(
        String nombre,
        String apellidos,
        String email,
        String username,
        LocalDate fechaNacimiento,
        String password,
        String token,
        String confirmationToken
    ) {
        this.nombre = nombre;
        this.apellidos = apellidos;
        this.email = email;
        this.username = username;
        this.fechaNacimiento = fechaNacimiento;
        this.password = password;
        this.token = token;
        this.confirmationToken = confirmationToken;
    }

    public Long getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getApellidos() {
        return apellidos;
    }

    public void setApellidos(String apellidos) {
        this.apellidos = apellidos;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public LocalDate getFechaNacimiento() {
        return fechaNacimiento;
    }

    public void setFechaNacimiento(LocalDate fechaNacimiento) {
        this.fechaNacimiento = fechaNacimiento;
    }

    public String getRol() {
        return rol;
    }

    public void setRol(String rol) {
        this.rol = rol;
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public void setConfirmed(boolean confirmed) {
        this.confirmed = confirmed;
    }

    public String getToken() {
        return sessionToken != null ? sessionToken : token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getStoredTokenHash() {
        return token;
    }

    public Instant getSessionTokenExpiresAt() {
        return sessionTokenExpiresAt;
    }

    public void setSessionTokenExpiresAt(Instant sessionTokenExpiresAt) {
        this.sessionTokenExpiresAt = sessionTokenExpiresAt;
    }

    public String getSessionToken() {
        return sessionToken;
    }

    public void setSessionToken(String sessionToken) {
        this.sessionToken = sessionToken;
    }

    public String getConfirmationToken() {
        return confirmationToken;
    }

    public void setConfirmationToken(String confirmationToken) {
        this.confirmationToken = confirmationToken;
    }

    public String getTwoFactorSecret() {
        return twoFactorSecret;
    }

    public void setTwoFactorSecret(String twoFactorSecret) {
        this.twoFactorSecret = twoFactorSecret;
    }

    public boolean isTwoFactorEnabled() {
        return Boolean.TRUE.equals(twoFactorEnabled);
    }

    public void setTwoFactorEnabled(boolean twoFactorEnabled) {
        this.twoFactorEnabled = twoFactorEnabled;
    }

    public int getFailedLoginAttempts() {
        return failedLoginAttempts == null ? 0 : failedLoginAttempts;
    }

    public void setFailedLoginAttempts(int failedLoginAttempts) {
        this.failedLoginAttempts = failedLoginAttempts;
    }

    public Instant getAccountLockedUntil() {
        return accountLockedUntil;
    }

    public void setAccountLockedUntil(Instant accountLockedUntil) {
        this.accountLockedUntil = accountLockedUntil;
    }

    public String getDniNieEncrypted() {
        return dniNieEncrypted;
    }

    public void setDniNieEncrypted(String dniNieEncrypted) {
        this.dniNieEncrypted = dniNieEncrypted;
    }

    public String getTelefonoEncrypted() {
        return telefonoEncrypted;
    }

    public void setTelefonoEncrypted(String telefonoEncrypted) {
        this.telefonoEncrypted = telefonoEncrypted;
    }

    public String getDireccionEncrypted() {
        return direccionEncrypted;
    }

    public void setDireccionEncrypted(String direccionEncrypted) {
        this.direccionEncrypted = direccionEncrypted;
    }

    public String getName() {
        return email;
    }

    public void setName(String name) {
        this.email = name;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
