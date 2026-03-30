package esi.edu.usuarios.usuarios.http;

import java.util.Map;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import esi.edu.usuarios.usuarios.services.UserService;

@RestController
@RequestMapping("/users")
public class UserControler {
    @Autowired
    private UserService service;

    @PostMapping("/login")
    public String login(@RequestBody Map<String, String> credenciales) {
        JSONObject jsoCredenciales = new JSONObject(credenciales);
        String name = jsoCredenciales.optString("name");
        String password = jsoCredenciales.optString("pwd");

        if(name.isEmpty() || password.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales vacías");
        }
       String result = service.login(name, password);
       if(result == null) {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales incorrectas");
       }
       return result;
    }

    @PostMapping("/register")
    public String register(@RequestBody Map<String, String> credenciales) {
        JSONObject jsoCredenciales = new JSONObject(credenciales);
        String name = jsoCredenciales.optString("name");
        String password = jsoCredenciales.optString("pwd");

        if(name.isEmpty() || password.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Credenciales vacías");
        }
       String result = service.register(name, password);
       if(result == null) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "RegistroFallido");
       }
       return result;
    }
}
