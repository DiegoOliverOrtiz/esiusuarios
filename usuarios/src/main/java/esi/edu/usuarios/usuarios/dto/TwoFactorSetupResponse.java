package esi.edu.usuarios.usuarios.dto;

public class TwoFactorSetupResponse {
    private final String qrUrl;
    private final String secretKey;

    public TwoFactorSetupResponse(String qrUrl, String secretKey) {
        this.qrUrl = qrUrl;
        this.secretKey = secretKey;
    }

    public String getQrUrl() {
        return qrUrl;
    }

    public String getSecretKey() {
        return secretKey;
    }
}
