package esi.edu.usuarios.usuarios.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class UpdateProfileRequest {
    @NotBlank
    @Size(max = 80)
    @Pattern(regexp = "^[\\p{L}\\p{M}]+(?:[ .'-][\\p{L}\\p{M}]+)*$")
    private String nombre;

    @NotBlank
    @Size(max = 120)
    @Pattern(regexp = "^[\\p{L}\\p{M}]+(?:[ .'-][\\p{L}\\p{M}]+)*$")
    private String apellidos;

    @NotBlank
    @Email
    @Size(max = 180)
    private String email;

    @Size(max = 60)
    @Pattern(regexp = "^[A-Za-z0-9._-]*$")
    private String username;

    @PastOrPresent
    private LocalDate fechaNacimiento;

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getApellidos() { return apellidos; }
    public void setApellidos(String apellidos) { this.apellidos = apellidos; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public LocalDate getFechaNacimiento() { return fechaNacimiento; }
    public void setFechaNacimiento(LocalDate fechaNacimiento) { this.fechaNacimiento = fechaNacimiento; }
}
