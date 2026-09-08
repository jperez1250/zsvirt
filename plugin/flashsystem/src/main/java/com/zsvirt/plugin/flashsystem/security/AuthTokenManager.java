package com.zsvirt.plugin.flashsystem.security;

import com.zsvirt.plugin.flashsystem.model.AuthToken;
import com.zsvirt.plugin.flashsystem.model.FlashSystemConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * Gestor de autenticación para API REST de IBM Storage Virtualize 8.7
 * Maneja tokens JWT con refresh automático y caching
 */
@Slf4j
public class AuthTokenManager {
    
    private final FlashSystemConfig config;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    
    private AuthToken cachedToken;
    private final Object lock = new Object();
    
    /** Buffer de minutos antes de la expiración para refresh preventivo */
    private static final int REFRESH_BUFFER_MINUTES = 5;
    
    public AuthTokenManager(FlashSystemConfig config, OkHttpClient httpClient) {
        this.config = config;
        this.httpClient = httpClient;
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * Obtiene un token válido, haciendo refresh si es necesario
     * @return Token JWT válido
     * @throws IOException Si falla la autenticación
     */
    public String getValidToken() throws IOException {
        synchronized (lock) {
            if (cachedToken == null || cachedToken.isExpired(REFRESH_BUFFER_MINUTES)) {
                log.info("Token expirado o inexistente, solicitando nuevo token");
                cachedToken = requestNewToken();
            } else {
                log.debug("Usando token cacheado válido hasta: {}", cachedToken.getExpiresAt());
            }
            return cachedToken.getToken();
        }
    }
    
    /**
     * Solicita un nuevo token a la API REST
     * @return Nuevo AuthToken
     * @throws IOException Si falla la autenticación
     */
    private AuthToken requestNewToken() throws IOException {
        String url = String.format("https://%s:%d/rest/v1/auth", 
            config.getManagementIp(), config.getApiPort());
        
        RequestBody body = RequestBody.create("", MediaType.parse("application/json"));
        
        Request request = new Request.Builder()
            .url(url)
            .post(body)
            .addHeader("Content-Type", "application/json")
            .addHeader("X-Auth-Username", config.getUsername())
            .addHeader("X-Auth-Password", config.getPassword())
            .build();
        
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorMsg = response.body() != null ? response.body().string() : "Sin detalles";
                log.error("Fallo de autenticación: HTTP {}, detalle: {}", response.code(), errorMsg);
                throw new IOException("Fallo de autenticación en FlashSystem: " + response.code() + " - " + errorMsg);
            }
            
            String responseBody = response.body().string();
            JsonNode jsonNode = objectMapper.readTree(responseBody);
            String token = jsonNode.path("token").asText();
            
            if (token.isEmpty()) {
                throw new IOException("Token no recibido en la respuesta de autenticación");
            }
            
            AuthToken authToken = new AuthToken();
            authToken.setToken(token);
            authToken.setCreatedAt(Instant.now());
            authToken.setTimeoutMinutes(config.getSessionTimeout());
            authToken.setExpiresAt(Instant.now().plusSeconds(config.getSessionTimeout() * 60L));
            
            log.info("Nuevo token obtenido, expira en {} minutos", config.getSessionTimeout());
            return authToken;
        }
    }
    
    /**
     * Invalida el token cacheado (para logout o reconexión)
     */
    public void invalidateToken() {
        synchronized (lock) {
            if (cachedToken != null) {
                log.info("Invalidando token cacheado");
                cachedToken = null;
            }
        }
    }
    
    /**
     * Verifica si hay un token cacheado válido
     * @return true si existe un token válido
     */
    public boolean hasValidToken() {
        synchronized (lock) {
            return cachedToken != null && cachedToken.isValid();
        }
    }
}
