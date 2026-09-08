package com.zstack.storage.flashsystem.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zstack.storage.flashsystem.model.*;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.SSLContext;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cliente REST para IBM FlashSystem 7300 / Storage Virtualize 8.7
 * Soporta autenticación por token con caché, operaciones de volúmenes, pools y snapshots
 */
@Component
public class FlashSystemRestClient {
    
    private static final Logger logger = LoggerFactory.getLogger(FlashSystemRestClient.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Map<String, CachedToken> tokenCache = new ConcurrentHashMap<>();
    
    private final RestTemplate restTemplate;
    
    public FlashSystemRestClient() throws Exception {
        SSLContext sslContext = new SSLContextBuilder()
            .loadTrustMaterial(null, (chain, authType) -> true)
            .build();
        
        SSLConnectionSocketFactory socketFactory = new SSLConnectionSocketFactory(
            sslContext, NoopHostnameVerifier.INSTANCE);
        
        CloseableHttpClient httpClient = HttpClients.custom()
            .setSSLSocketFactory(socketFactory)
            .build();
        
        this.restTemplate = new RestTemplate(new HttpComponentsClientHttpRequestFactory(httpClient));
    }
    
    /**
     * Autenticación con caching de tokens (10-120 minutos según configuración SV 8.7)
     */
    public String authenticate(String baseUrl, String username, String password, int timeoutMinutes) {
        String cacheKey = baseUrl + ":" + username;
        
        CachedToken cached = tokenCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            logger.debug("Usando token en caché para {}", baseUrl);
            return cached.getToken();
        }
        
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Auth-Username", username);
            headers.set("X-Auth-Password", password);
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<String> entity = new HttpEntity<>(headers);
            String authUrl = baseUrl + "/rest/v1/auth";
            
            ResponseEntity<AuthTokenResponse> response = restTemplate.exchange(
                authUrl,
                HttpMethod.POST,
                entity,
                AuthTokenResponse.class
            );
            
            AuthTokenResponse authResponse = response.getBody();
            if (authResponse == null || authResponse.getToken() == null) {
                throw new RuntimeException("Error en autenticación: respuesta nula");
            }
            
            long expirationTime = Instant.now().plusSeconds(timeoutMinutes * 60).toEpochMilli();
            CachedToken newToken = new CachedToken(authResponse.getToken(), expirationTime);
            tokenCache.put(cacheKey, newToken);
            
            logger.info("Autenticación exitosa en {}, token válido por {} minutos", baseUrl, timeoutMinutes);
            return authResponse.getToken();
            
        } catch (Exception e) {
            logger.error("Error autenticando en {}: {}", baseUrl, e.getMessage());
            throw new RuntimeException("Fallo de autenticación FlashSystem", e);
        }
    }
    
    /**
     * Llamada genérica a API REST con token automático
     */
    @SuppressWarnings("unchecked")
    public <T> T callApi(String baseUrl, String username, String password, 
                         HttpMethod method, String endpoint, Object body, Class<T> responseType) {
        
        String token = authenticate(baseUrl, username, password, 60); // Default 60 min
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Auth-Token", token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        HttpEntity<Object> entity = new HttpEntity<>(body, headers);
        String fullUrl = baseUrl + "/rest/v1/" + endpoint;
        
        try {
            ResponseEntity<T> response = restTemplate.exchange(fullUrl, method, entity, responseType);
            return response.getBody();
        } catch (Exception e) {
            logger.error("Error en API {} {}: {}", method, endpoint, e.getMessage());
            throw new RuntimeException("Error en llamada API FlashSystem: " + endpoint, e);
        }
    }
    
    /**
     * Listar volúmenes (lsvdisk)
     */
    public List<FlashSystemVolume> listVolumes(String baseUrl, String username, String password, 
                                                String poolName, String volumeGroupName) {
        Map<String, String> params = new HashMap<>();
        if (poolName != null) params.put("mdisk_grp_name", poolName);
        if (volumeGroupName != null) params.put("volume_group_name", volumeGroupName);
        
        String endpoint = "lsvdisk?" + buildQueryString(params);
        return List.of(callApi(baseUrl, username, password, HttpMethod.GET, endpoint, null, FlashSystemVolume[].class));
    }
    
    /**
     * Crear volumen (mkvdisk)
     */
    public FlashSystemVolume createVolume(String baseUrl, String username, String password,
                                          String name, long sizeMB, String poolName, 
                                          boolean thin, boolean compressed, boolean deduplicated) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("name", name);
        payload.put("size", sizeMB);
        payload.put("unit", "mb");
        payload.put("mdisk_grp_name", poolName);
        payload.put("thin", thin);
        payload.put("compressed", compressed);
        payload.put("deduplicated", deduplicated);
        
        return callApi(baseUrl, username, password, HttpMethod.POST, "mkvdisk", payload, FlashSystemVolume.class);
    }
    
    /**
     * Eliminar volumen (rmvdisk)
     */
    public void deleteVolume(String baseUrl, String username, String password, String volumeId) {
        callApi(baseUrl, username, password, HttpMethod.DELETE, "rmvdisk/" + volumeId, null, Void.class);
        logger.info("Volumen {} eliminado correctamente", volumeId);
    }
    
    /**
     * Obtener información de pool (lsmdiskgrp)
     */
    public FlashSystemPool getPoolInfo(String baseUrl, String username, String password, String poolName) {
        String endpoint = "lsmdiskgrp?filter=value=name," + poolName;
        FlashSystemPool[] pools = callApi(baseUrl, username, password, HttpMethod.GET, endpoint, null, FlashSystemPool[].class);
        return pools != null && pools.length > 0 ? pools[0] : null;
    }
    
    /**
     * Listar pools disponibles
     */
    public List<FlashSystemPool> listPools(String baseUrl, String username, String password) {
        String endpoint = "lsmdiskgrp";
        return List.of(callApi(baseUrl, username, password, HttpMethod.GET, endpoint, null, FlashSystemPool[].class));
    }
    
    /**
     * Crear snapshot (mkfcmap - FlashCopy)
     */
    public FlashSystemSnapshot createSnapshot(String baseUrl, String username, String password,
                                              String sourceVolumeId, String snapshotName, 
                                              boolean safeguarded) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("source_volume_id", sourceVolumeId);
        payload.put("name", snapshotName);
        payload.put("safeguarded", safeguarded);
        
        return callApi(baseUrl, username, password, HttpMethod.POST, "mkfcmap", payload, FlashSystemSnapshot.class);
    }
    
    /**
     * Listar snapshots (lsvolumesnapshot)
     */
    public List<FlashSystemSnapshot> listSnapshots(String baseUrl, String username, String password,
                                                    String volumeId) {
        String endpoint = "lsvolumesnapshot?filter=value=source_volume_id," + volumeId;
        return List.of(callApi(baseUrl, username, password, HttpMethod.GET, endpoint, null, FlashSystemSnapshot[].class));
    }
    
    /**
     * Restaurar desde snapshot (restorefromsnapshot)
     */
    public void restoreSnapshot(String baseUrl, String username, String password, String snapshotId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("snapshot_id", snapshotId);
        
        callApi(baseUrl, username, password, HttpMethod.POST, "restorefromsnapshot", payload, Void.class);
        logger.info("Snapshot {} restaurado correctamente", snapshotId);
    }
    
    /**
     * Eliminar snapshot (rmsnapshot)
     */
    public void deleteSnapshot(String baseUrl, String username, String password, String snapshotId) {
        callApi(baseUrl, username, password, HttpMethod.DELETE, "rmsnapshot/" + snapshotId, null, Void.class);
        logger.info("Snapshot {} eliminado correctamente", snapshotId);
    }
    
    /**
     * Registrar host (mkhost)
     */
    public void registerHost(String baseUrl, String username, String password,
                             String hostname, String hostGroup, List<String> wwpns) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("name", hostname);
        if (hostGroup != null) payload.put("host_group_name", hostGroup);
        if (wwpns != null && !wwpns.isEmpty()) payload.put("wwpn", wwpns);
        
        callApi(baseUrl, username, password, HttpMethod.POST, "mkhost", payload, Void.class);
        logger.info("Host {} registrado en grupo {}", hostname, hostGroup);
    }
    
    /**
     * Mapear volumen a host (mkvdiskhostmap)
     */
    public void mapVolumeToHost(String baseUrl, String username, String password,
                                String volumeId, String hostId, int lun) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("vdisk_id", volumeId);
        payload.put("host_id", hostId);
        payload.put("lun", lun);
        
        callApi(baseUrl, username, password, HttpMethod.POST, "mkvdiskhostmap", payload, Void.class);
        logger.info("Volumen {} mapeado al host {} en LUN {}", volumeId, hostId, lun);
    }
    
    /**
     * Obtener evento log (lseventlog) para alertas
     */
    public List<Map<String, Object>> getEventLog(String baseUrl, String username, String password, 
                                                  int limit) {
        String endpoint = "lseventlog?limit=" + limit;
        return List.of(callApi(baseUrl, username, password, HttpMethod.GET, endpoint, null, Map[].class));
    }
    
    private String buildQueryString(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (sb.length() > 0) sb.append("&");
            sb.append(entry.getKey()).append("=").append(entry.getValue());
        }
        return sb.toString();
    }
    
    private static class CachedToken {
        private final String token;
        private final long expirationTime;
        
        CachedToken(String token, long expirationTime) {
            this.token = token;
            this.expirationTime = expirationTime;
        }
        
        String getToken() { return token; }
        boolean isExpired() { return System.currentTimeMillis() >= expirationTime; }
    }
}
