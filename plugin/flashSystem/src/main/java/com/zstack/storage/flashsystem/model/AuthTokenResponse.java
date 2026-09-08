package com.zstack.storage.flashsystem.model;

import java.util.Map;

/**
 * Modelo para respuesta de autenticación IBM FlashSystem 7300 / Storage Virtualize 8.7
 */
public class AuthTokenResponse {
    private String token;
    private long expiration;
    private String username;
    
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public long getExpiration() { return expiration; }
    public void setExpiration(long expiration) { this.expiration = expiration; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
}
