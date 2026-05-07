package esi.edu.usuarios.usuarios.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class TwoFactorVerifyRequest {
    @NotBlank
    @Size(min = 6, max = 6)
    @Pattern(regexp = "^[0-9]{6}$")
    private String code;

    @Size(max = 120)
    private String challengeToken;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getChallengeToken() {
        return challengeToken;
    }

    public void setChallengeToken(String challengeToken) {
        this.challengeToken = challengeToken;
    }
}
