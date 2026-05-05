package esi.edu.usuarios.usuarios.http;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import esi.edu.usuarios.usuarios.dto.LoginRequest;
import esi.edu.usuarios.usuarios.dto.RegisterUserRequest;
import esi.edu.usuarios.usuarios.dto.UserResponse;
import esi.edu.usuarios.usuarios.model.User;
import esi.edu.usuarios.usuarios.services.UserService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/users")
@Validated
public class UserControler {
    @Autowired
    private UserService service;

    @PostMapping("/login")
    public String login(@Valid @RequestBody LoginRequest credenciales) {
        String result = service.login(credenciales.getName(), credenciales.getPwd());
        if (result == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales incorrectas");
        }

        return result;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterUserRequest request) {
        try {
            User user = service.register(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(new UserResponse(user));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @GetMapping("/confirm")
    public String confirm(@RequestParam String token) {
        try {
            return service.confirmRegistration(token);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleValidationError() {
        return "La peticion de registro no es valida.";
    }
}
