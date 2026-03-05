package esi.edu.usuarios.usuarios.services;

import java.util.List;

import org.springframework.stereotype.Service;

import esi.edu.usuarios.usuarios.model.User;

@Service
public class UserService {
    private List<User> users; 
    public UserService() {
        this.users = List.of(
            new User("Pepe", "pepe123", "1234"),
            new User("Juan", "juan123", "4321")
        );
    }

    public String login(String name, String password) {
        for(User user : this.users) {
            if(user.getName().equals(name) && user.getPassword().equals(password)) {
                return "Login exitoso";
            }
        }
        return null;
    }

    public String checkToken(String token) {
        for(User user : this.users) {
            if(user.getToken().equals(token)) {
                return user.getName();
            }
        }
        return null;
    }
}
