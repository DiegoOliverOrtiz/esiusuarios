package esi.edu.usuarios.usuarios.http;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import esi.edu.usuarios.usuarios.services.UserService;

@RestController
@RequestMapping("/external")
public class ExternalController {

    @Autowired
    private UserService service;

    @GetMapping("/checkToken/{token}")
    public String checkToken(@PathVariable String token) {
        if(token == null || token.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Token is required");
        }
        String userName = this.service.checkToken(token);
        if(userName == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token");
        }
        return userName;
    }

}
