package org.zstack.storage.primary.flashsystem;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpEntity;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.*;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.springframework.beans.factory.annotation.Autowired;
import org.zstack.core.crypt.CryptoFacade;
import org.zstack.header.errorcode.OperationFailureException;
import org.zstack.header.exception.CloudRuntimeException;
import org.zstack.storage.primary.flashsystem.model.FlashSystemPool;
import org.zstack.storage.primary.flashsystem.model.FlashSystemVolume;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

import static org.zstack.core.Platform.operr;

/**
 * REST API Client for IBM FlashSystem / IBM Storage Virtualize
 * 
 * Handles authentication, token management, and API calls to FlashSystem storage array
 */
public class FlashSystemApiClient {
    
    private static final CLogger logger = Utils.getLogger(FlashSystemApiClient.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    @Autowired
    private CryptoFacade cryptoFacade;
    
    // Token cache per storage configuration
    private final ConcurrentHashMap<String, CachedToken> tokenCache = new ConcurrentHashMap<>();
    
    // HTTP client uses the JVM trust store. Arrays with self-signed certificates must
    // install their CA certificate in the management node trust store.
    private final CloseableHttpClient httpClient;
    
    private static class CachedToken {
        String token;
        long expiryTime;
        
        CachedToken(String token, long ttlSeconds) {
            this.token = token;
            this.expiryTime = System.currentTimeMillis() + (ttlSeconds * 1000);
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() >= expiryTime;
        }
    }

    private static class AuthenticationRejectedException extends RuntimeException {
        private AuthenticationRejectedException() {
            super("FlashSystem rejected the authentication token");
        }
    }
    
    public FlashSystemApiClient() {
        try {
            RequestConfig requestConfig = RequestConfig.custom()
                    .setConnectTimeout(FlashSystemConstant.DEFAULT_API_TIMEOUT * 1000)
                    .setSocketTimeout(FlashSystemConstant.DEFAULT_API_TIMEOUT * 1000)
                    .setConnectionRequestTimeout(FlashSystemConstant.DEFAULT_API_TIMEOUT * 1000)
                    .build();
            
            this.httpClient = HttpClients.custom()
                    .setDefaultRequestConfig(requestConfig)
                    .build();
        } catch (Exception e) {
            throw new CloudRuntimeException("Failed to initialize FlashSystem HTTP client", e);
        }
    }
    
    /**
     * Authenticate with FlashSystem REST API and obtain JWT token
     */
    public synchronized String authenticate(FlashSystemStorageVO scfg) {
        String cacheKey = scfg.getUuid();
        CachedToken cached = tokenCache.get(cacheKey);
        
        if (cached != null && !cached.isExpired()) {
            logger.debug(String.format("Using cached token for FlashSystem[uuid:%s]", scfg.getUuid()));
            return cached.token;
        }
        
        try {
            String url = buildUrl(scfg, FlashSystemConstant.AUTH_ENDPOINT);
            
            HttpPost post = new HttpPost(url);
            post.setHeader("Accept", "application/json");
            post.setHeader("X-Auth-Username", scfg.getUsername());
            post.setHeader("X-Auth-Password", decryptPassword(scfg.getPassword()));
            
            logger.debug(String.format("Authenticating to FlashSystem[%s:%d]", scfg.getManagementIp(), getRestApiPort(scfg)));
            
            try (CloseableHttpResponse response = httpClient.execute(post)) {
                int statusCode = response.getStatusLine().getStatusCode();
                HttpEntity responseEntity = response.getEntity();
                
                if (statusCode != 200 || responseEntity == null) {
                    throw new OperationFailureException(operr(
                        "FlashSystem authentication failed: HTTP %d - %s", 
                        statusCode, 
                        response.getStatusLine().getReasonPhrase()
                    ));
                }
                
                String responseBody = org.apache.commons.io.IOUtils.toString(responseEntity.getContent(), StandardCharsets.UTF_8);
                JsonNode jsonNode = objectMapper.readTree(responseBody);
                
                String token = jsonNode.path("token").asText();
                long ttl = jsonNode.path("ttl").asLong(3600); // Default 1 hour TTL
                
                if (token == null || token.isEmpty()) {
                    throw new OperationFailureException(operr("No token returned from FlashSystem authentication"));
                }
                
                // Cache the token
                tokenCache.put(cacheKey, new CachedToken(token, ttl));
                
                logger.info(String.format("Successfully authenticated to FlashSystem[uuid:%s, ip:%s]", scfg.getUuid(), scfg.getManagementIp()));
                return token;
                
            } finally {
                post.releaseConnection();
            }
            
        } catch (IOException e) {
            throw new OperationFailureException(operr("Failed to authenticate to FlashSystem[%s]: %s", 
                scfg.getManagementIp(), e.getMessage()), e);
        }
    }
    
    /**
     * Execute a GET request against FlashSystem REST API
     */
    public JsonNode get(FlashSystemStorageVO scfg, String endpoint) {
        return executeApiCall(scfg, "GET", endpoint, null);
    }
    
    /**
     * Execute a POST request against FlashSystem REST API
     */
    public JsonNode post(FlashSystemStorageVO scfg, String endpoint, Map<String, Object> body) {
        return executeApiCall(scfg, "POST", endpoint, body);
    }
    
    /**
     * Execute a DELETE request against FlashSystem REST API
     */
    public JsonNode delete(FlashSystemStorageVO scfg, String endpoint) {
        return executeApiCall(scfg, "DELETE", endpoint, null);
    }
    
    /**
     * Execute a PUT request against FlashSystem REST API
     */
    public JsonNode put(FlashSystemStorageVO scfg, String endpoint, Map<String, Object> body) {
        return executeApiCall(scfg, "PUT", endpoint, body);
    }

    /**
     * Resolve the array volume name from a multipath WWID. Keeping this lookup in
     * the sole array client prevents lifecycle code from guessing array identifiers.
     */
    public String findVolumeName(FlashSystemStorageVO scfg, String wwid) {
        JsonNode volumes = get(scfg, FlashSystemConstant.LSVOLUME_ENDPOINT + "?filter=value:uid:" + wwid);
        if (volumes == null) {
            return null;
        }
        JsonNode volume = volumes.isArray() ? volumes.path(0) : volumes;
        String name = volume.path("name").asText();
        return name.isEmpty() ? null : name;
    }
    
    /**
     * Generic method to execute API calls with authentication
     */
    private JsonNode executeApiCall(FlashSystemStorageVO scfg, String method, String endpoint, Map<String, Object> body) {
        // Authentication failures can occur after a controller expires a token. Retry
        // exactly once with a newly obtained token; do not recurse indefinitely.
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                return executeApiCallOnce(scfg, method, endpoint, body);
            } catch (AuthenticationRejectedException e) {
                if (attempt == 1) {
                    throw new OperationFailureException(operr(
                            "FlashSystem API rejected a refreshed authentication token (%s %s)", method, endpoint));
                }
            }
        }
        return null; // unreachable, retained for the compiler
    }

    private JsonNode executeApiCallOnce(FlashSystemStorageVO scfg, String method, String endpoint, Map<String, Object> body) {
        String token = authenticate(scfg);
        String url = buildUrl(scfg, endpoint);
        HttpRequestBase request;
        switch (method.toUpperCase()) {
            case "GET":
                request = new HttpGet(url);
                break;
            case "POST":
                request = new HttpPost(url);
                break;
            case "PUT":
                request = new HttpPut(url);
                break;
            case "DELETE":
                request = new HttpDelete(url);
                break;
            default:
                throw new IllegalArgumentException("Unsupported HTTP method: " + method);
        }
        
        try {
            request.setHeader("X-Auth-Token", token);
            request.setHeader("Content-Type", "application/json");
            request.setHeader("Accept", "application/json");
            
            if (body != null && !body.isEmpty()) {
                if (request instanceof HttpEntityEnclosingRequest) {
                    String jsonBody = objectMapper.writeValueAsString(body);
                    ((HttpEntityEnclosingRequest) request).setEntity(
                        new StringEntity(jsonBody, StandardCharsets.UTF_8)
                    );
                }
            }
            
            logger.debug(String.format("Executing %s request to FlashSystem: %s", method, url));
            
            try (CloseableHttpResponse response = httpClient.execute(request)) {
                int statusCode = response.getStatusLine().getStatusCode();
                HttpEntity responseEntity = response.getEntity();
                
                if (statusCode < 200 || statusCode >= 300) {
                    String errorMsg = responseEntity != null ? 
                        org.apache.commons.io.IOUtils.toString(responseEntity.getContent(), StandardCharsets.UTF_8) : 
                        "No response body";
                    
                    if (statusCode == 401 || statusCode == 403) {
                        tokenCache.remove(scfg.getUuid());
                        logger.warn(String.format("Token expired or invalid for FlashSystem[%s], clearing cache", scfg.getUuid()));
                        throw new AuthenticationRejectedException();
                    }
                    
                    throw new OperationFailureException(operr(
                        "FlashSystem API error (%s %s): HTTP %d - %s", 
                        method, endpoint, statusCode, errorMsg
                    ));
                }
                
                if (responseEntity == null) {
                    return null;
                }
                
                String responseBody = org.apache.commons.io.IOUtils.toString(responseEntity.getContent(), StandardCharsets.UTF_8);
                
                if (responseBody == null || responseBody.trim().isEmpty()) {
                    return null;
                }
                
                return objectMapper.readTree(responseBody);
                
            } finally {
                request.releaseConnection();
            }
            
        } catch (IOException e) {
            throw new OperationFailureException(operr("FlashSystem API call failed (%s %s): %s", 
                method, endpoint, e.getMessage()), e);
        }
    }
    
    /**
     * Build full URL for API endpoint
     */
    private String buildUrl(FlashSystemStorageVO scfg, String endpoint) {
        if (!endpoint.startsWith("/")) {
            endpoint = "/" + endpoint;
        }
        if (!endpoint.startsWith("/" + FlashSystemConstant.REST_API_VERSION)) {
            endpoint = "/" + FlashSystemConstant.REST_API_VERSION + endpoint;
        }
        return String.format("https://%s:%d%s", scfg.getManagementIp(), getRestApiPort(scfg), endpoint);
    }

    private int getRestApiPort(FlashSystemStorageVO scfg) {
        return scfg.getRestApiPort() == null ? FlashSystemConstant.DEFAULT_REST_PORT : scfg.getRestApiPort();
    }
    
    /**
     * Decrypt stored password
     */
    private String decryptPassword(String encryptedPassword) {
        try {
            return cryptoFacade.decrypt(encryptedPassword);
        } catch (Exception e) {
            // If decryption fails, try using as-is (might already be decrypted in some cases)
            logger.warn("Password decryption failed, using as-is");
            return encryptedPassword;
        }
    }
    
    /**
     * Clear cached token (useful for forced re-authentication)
     */
    public void clearTokenCache(String storageUuid) {
        tokenCache.remove(storageUuid);
        logger.debug(String.format("Cleared token cache for storage[uuid:%s]", storageUuid));
    }
}
