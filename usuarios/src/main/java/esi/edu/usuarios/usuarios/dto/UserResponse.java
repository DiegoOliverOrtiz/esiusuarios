package esi.edu.usuarios.usuarios.dto;

import esi.edu.usuarios.usuarios.model.User;

public class UserResponse {
    private final Long id;
    private final String nombre;
    private final String email;
    private final String username;
    private final String rol;

    public UserResponse(User user) {
        this.id = user.getId();
        this.nombre = user.getNombre();
        this.email = user.getEmail();
        this.username = user.getUsername();
        this.rol = user.getRol();
    }

    public Long getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public String getEmail() {
        return email;
    }

    public String getUsername() {
        return username;
    }

    public String getRol() {
        return rol;
    }
}
