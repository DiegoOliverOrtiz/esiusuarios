package esi.edu.usuarios.usuarios.dto;

import esi.edu.usuarios.usuarios.model.User;

public class UserResponse {
    private final Long id;
    private final String nombre;
    private final String apellidos;
    private final String email;
    private final String username;
    private final java.time.LocalDate fechaNacimiento;
    private final boolean twoFactorEnabled;
    private final String rol;

    public UserResponse(User user) {
        this.id = user.getId();
        this.nombre = user.getNombre();
        this.apellidos = user.getApellidos();
        this.email = user.getEmail();
        this.username = user.getUsername();
        this.fechaNacimiento = user.getFechaNacimiento();
        this.twoFactorEnabled = user.isTwoFactorEnabled();
        this.rol = user.getRol();
    }

    public Long getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public String getApellidos() {
        return apellidos;
    }

    public String getEmail() {
        return email;
    }

    public String getUsername() {
        return username;
    }

    public java.time.LocalDate getFechaNacimiento() {
        return fechaNacimiento;
    }

    public boolean isTwoFactorEnabled() {
        return twoFactorEnabled;
    }

    public String getRol() {
        return rol;
    }
}
