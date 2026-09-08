package com.zsvirt.plugin.flashsystem.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zsvirt.plugin.flashsystem.model.FlashSystemConfig;
import com.zsvirt.plugin.flashsystem.security.AuthTokenManager;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Cliente HTTP para API REST de IBM Storage Virtualize 8.7
 * Maneja autenticación automática, retry en caso de token expirado y parsing de errores CMMVC
 */
@Slf4j
public class FlashSystemApiClient {
    
    private final FlashSystemConfig config;
    private final OkHttpClient httpClient;
    private final AuthTokenManager authTokenManager;
    private final ObjectMapper objectMapper;
    
    private static final String MEDIA_TYPE_JSON = "application/json";
    
    public FlashSystemApiClient(FlashSystemConfig config, OkHttpClient httpClient, AuthTokenManager authTokenManager) {
        this.config = config;
        this.httpClient = httpClient;
        this.authTokenManager = authTokenManager;
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * Ejecuta una llamada GET a la API REST
     */
    public JsonNode get(String command) throws IOException {
        return executeRequest("GET", command, null);
    }
    
    /**
     * Ejecuta una llamada POST a la API REST
     */
    public JsonNode post(String command, Map<String, Object> parameters) throws IOException {
        return executeRequest("POST", command, parameters);
    }
    
    /**
     * Ejecuta una llamada DELETE a la API REST
     */
    public JsonNode delete(String command) throws IOException {
        return executeRequest("DELETE", command, null);
    }
    
    /**
     * Ejecuta una llamada PUT a la API REST
     */
    public JsonNode put(String command, Map<String, Object> parameters) throws IOException {
        return executeRequest("PUT", command, parameters);
    }
    
    /**
     * Método genérico para ejecutar requests con manejo automático de tokens y retries
     */
    private JsonNode executeRequest(String method, String command, Map<String, Object> parameters) throws IOException {
        String url = buildUrl(command);
        
        // Primer intento con token actual
        try {
            JsonNode result = doRequest(method, url, parameters);
            log.debug("{} {} exitoso", method, command);
            return result;
        } catch (HttpException e) {
            // Si es error 401 (token expirado), invalidar token y reintentar
            if (e.getStatusCode() == 401) {
                log.warn("Token expirado (401), invalidando y reintentando...");
                authTokenManager.invalidateToken();
                
                // Reintentar con nuevo token
                String newUrl = buildUrl(command);
                return doRequest(method, newUrl, parameters);
            }
            throw e;
        }
    }
    
    /**
     * Construye la URL completa para el comando
     */
    private String buildUrl(String command) {
        // Los comandos ya incluyen el path completo (ej: lsvdisk, mkvdisk)
        return String.format("https://%s:%d/rest/v1/%s", 
            config.getManagementIp(), config.getApiPort(), command);
    }
    
    /**
     * Ejecuta el request HTTP real
     */
    private JsonNode doRequest(String method, String url, Map<String, Object> parameters) throws IOException {
        String token = authTokenManager.getValidToken();
        
        RequestBody body = null;
        if (parameters != null && !parameters.isEmpty()) {
            String json = objectMapper.writeValueAsString(parameters);
            body = RequestBody.create(json, MediaType.parse(MEDIA_TYPE_JSON));
        } else if ("POST".equals(method) || "PUT".equals(method)) {
            body = RequestBody.create("", MediaType.parse(MEDIA_TYPE_JSON));
        }
        
        Request.Builder requestBuilder = new Request.Builder()
            .url(url)
            .addHeader("Content-Type", MEDIA_TYPE_JSON)
            .addHeader("X-Auth-Token", token);
        
        // Configurar método y body
        if ("GET".equals(method)) {
            requestBuilder.get();
        } else if ("POST".equals(method)) {
            requestBuilder.post(body != null ? body : RequestBody.create("", MediaType.parse(MEDIA_TYPE_JSON)));
        } else if ("PUT".equals(method)) {
            requestBuilder.put(body);
        } else if ("DELETE".equals(method)) {
            requestBuilder.delete();
        }
        
        Request request = requestBuilder.build();
        
        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            
            if (!response.isSuccessful()) {
                handleError(response.code(), responseBody, url);
            }
            
            // Respuesta vacía es válida para algunos comandos
            if (responseBody.trim().isEmpty()) {
                return objectMapper.createObjectNode();
            }
            
            return objectMapper.readTree(responseBody);
        }
    }
    
    /**
     * Manejo de errores con parsing de códigos CMMVC
     */
    private void handleError(int statusCode, String responseBody, String url) throws IOException {
        String errorMsg = "Error HTTP " + statusCode;
        
        try {
            JsonNode errorJson = objectMapper.readTree(responseBody);
            JsonNode errors = errorJson.path("errors");
            
            if (errors.isArray() && errors.size() > 0) {
                JsonNode firstError = errors.get(0);
                String errorCode = firstError.path("error_code").asText("UNKNOWN");
                String errorText = firstError.path("error_text").asText("Sin descripción");
                
                errorMsg = String.format("CMMVC%s: %s (HTTP %d)", errorCode, errorText, statusCode);
                log.error("Error API FlashSystem: {}", errorMsg);
            }
        } catch (Exception e) {
            // Si no se puede parsear el JSON, usar el texto plano
            if (!responseBody.isEmpty()) {
                errorMsg += ": " + responseBody;
            }
            log.error("Error API FlashSystem (sin detalle JSON): {}", errorMsg);
        }
        
        throw new HttpException(statusCode, errorMsg);
    }
    
    /**
     * Excepción personalizada para errores HTTP
     */
    public static class HttpException extends IOException {
        private final int statusCode;
        
        public HttpException(int statusCode, String message) {
            super(message);
            this.statusCode = statusCode;
        }
        
        public int getStatusCode() {
            return statusCode;
        }
    }
}
