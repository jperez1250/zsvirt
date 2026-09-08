package org.zstack.storage.primary.flashsystem;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpEntity;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.*;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.zstack.core.CoreService;
import org.zstack.core.crypt.CryptoFacade;
import org.zstack.header.errorcode.ErrorCode;
import org.zstack.header.errorcode.OperationFailureException;
import org.zstack.header.exception.CloudRuntimeException;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
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
    private CoreService coreService;
    
    @Autowired
    private CryptoFacade cryptoFacade;
    
    // Token cache per storage configuration
    private final ConcurrentHashMap<String, CachedToken> tokenCache = new ConcurrentHashMap<>();
    
    // HTTP client with SSL support
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
    
    public FlashSystemApiClient() {
        try {
            // Create HTTP client that accepts self-signed certificates
            SSLContextBuilder sslBuilder = new SSLContextBuilder();
            sslBuilder.loadTrustMaterial(null, (chain, authType) -> true);
            
            RequestConfig requestConfig = RequestConfig.custom()
                    .setConnectTimeout(FlashSystemConstant.DEFAULT_API_TIMEOUT * 1000)
                    .setSocketTimeout(FlashSystemConstant.DEFAULT_API_TIMEOUT * 1000)
                    .setConnectionRequestTimeout(FlashSystemConstant.DEFAULT_API_TIMEOUT * 1000)
                    .build();
            
            this.httpClient = HttpClients.custom()
                    .setSSLContext(sslBuilder.build())
                    .setSSLHostnameVerifier(new NoopHostnameVerifier())
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
            String url = buildUrl(scfg.getManagementIp(), FlashSystemConstant.AUTH_ENDPOINT);
            
            HttpPost post = new HttpPost(url);
            post.setHeader("Content-Type", "application/json");
            
            // Build authentication payload
            Map<String, String> authPayload = new HashMap<>();
            authPayload.put("username", scfg.getUsername());
            authPayload.put("password", decryptPassword(scfg.getPassword()));
            
            StringEntity entity = new StringEntity(objectMapper.writeValueAsString(authPayload), StandardCharsets.UTF_8);
            post.setEntity(entity);
            
            logger.debug(String.format("Authenticating to FlashSystem[%s:%d]", scfg.getManagementIp(), FlashSystemConstant.DEFAULT_REST_PORT));
            
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
     * Generic method to execute API calls with authentication
     */
    private JsonNode executeApiCall(FlashSystemStorageVO scfg, String method, String endpoint, Map<String, Object> body) {
        String token = authenticate(scfg);
        String url = buildUrl(scfg.getManagementIp(), endpoint);
        
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
                    
                    // Check if token expired
                    if (statusCode == 401 || statusCode == 403) {
                        logger.warn(String.format("Token expired or invalid for FlashSystem[%s], clearing cache", scfg.getUuid()));
                        tokenCache.remove(scfg.getUuid());
                        // Retry once with fresh authentication
                        return executeApiCall(scfg, method, endpoint, body);
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
    private String buildUrl(String ipAddress, String endpoint) {
        if (!endpoint.startsWith("/")) {
            endpoint = "/" + endpoint;
        }
        if (!endpoint.startsWith("/" + FlashSystemConstant.REST_API_VERSION)) {
            endpoint = "/" + FlashSystemConstant.REST_API_VERSION + endpoint;
        }
        return String.format("https://%s:%d%s", ipAddress, FlashSystemConstant.DEFAULT_REST_PORT, endpoint);
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
