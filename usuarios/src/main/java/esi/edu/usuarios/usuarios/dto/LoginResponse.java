package esi.edu.usuarios.usuarios.dto;

import esi.edu.usuarios.usuarios.model.User;

public class LoginResponse {
    private final boolean twoFactorRequired;
    private final String twoFactorChallengeToken;
    private final UserResponse user;

    public LoginResponse(User user) {
        this.twoFactorRequired = false;
        this.twoFactorChallengeToken = null;
        this.user = new UserResponse(user);
    }

    public LoginResponse(String twoFactorChallengeToken, User user) {
        this.twoFactorRequired = true;
        this.twoFactorChallengeToken = twoFactorChallengeToken;
        this.user = new UserResponse(user);
    }

    public boolean isTwoFactorRequired() {
        return twoFactorRequired;
    }

    public String getTwoFactorChallengeToken() {
        return twoFactorChallengeToken;
    }

    public UserResponse getUser() {
        return user;
    }
}
