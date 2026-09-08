package com.zsvirt.plugin.flashsystem.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.zsvirt.plugin.flashsystem.api.FlashSystemApiClient;
import com.zsvirt.plugin.flashsystem.model.FlashSystemConfig;
import com.zsvirt.plugin.flashsystem.model.StoragePoolInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Servicio para monitoreo de capacidad y estado de pools en IBM FlashSystem 7300
 * Soporta modelos Utility (Capacity on Demand) y telemetría de Data Reduction
 */
@Slf4j
@Service
public class StorageMonitoringService {
    
    private final FlashSystemApiClient apiClient;
    private final FlashSystemConfig config;
    
    public StorageMonitoringService(FlashSystemApiClient apiClient, FlashSystemConfig config) {
        this.apiClient = apiClient;
        this.config = config;
    }
    
    /**
     * Obtiene información detallada del pool de almacenamiento configurado
     * @return Información del pool
     * @throws IOException Si falla la consulta
     */
    public StoragePoolInfo getPoolInfo() throws IOException {
        log.debug("Consultando información del pool {}", config.getStoragePool());
        
        JsonNode response = apiClient.post("lsmdiskgrp", 
            Collections.singletonMap("mdiskgrp", config.getStoragePool()));
        
        return parsePoolInfo(response);
    }
    
    /**
     * Lista todos los pools disponibles
     * @return Lista de pools
     * @throws IOException Si falla la consulta
     */
    public List<StoragePoolInfo> listAllPools() throws IOException {
        log.debug("Listando todos los pools");
        
        JsonNode response = apiClient.post("lsmdiskgrp", Collections.emptyMap());
        
        List<StoragePoolInfo> pools = new ArrayList<>();
        JsonNode result = response.path("result");
        
        if (result.isArray()) {
            for (JsonNode node : result) {
                pools.add(parsePoolInfo(node));
            }
        }
        
        return pools;
    }
    
    /**
     * Obtiene el estado global del sistema FlashSystem
     * @return Nodo JSON con estado del sistema
     * @throws IOException Si falla la consulta
     */
    public JsonNode getSystemStatus() throws IOException {
        log.debug("Consultando estado del sistema");
        
        return apiClient.post("lssystem", Collections.emptyMap());
    }
    
    /**
     * Obtiene alertas activas del sistema
     * @return Lista de alertas activas
     * @throws IOException Si falla la consulta
     */
    public List<String> getActiveAlerts() throws IOException {
        log.debug("Consultando alertas activas");
        
        JsonNode response = apiClient.post("lseventlog", 
            Collections.singletonMap("filtervalue", "status=active"));
        
        List<String> alerts = new ArrayList<>();
        JsonNode result = response.path("result");
        
        if (result.isArray()) {
            for (JsonNode node : result) {
                String errorCode = node.path("error_code").asText("");
                String errorText = node.path("error_text").asText("");
                String severity = node.path("severity").asText("");
                
                alerts.add(String.format("[%s] CMMVC%s: %s", severity, errorCode, errorText));
            }
        }
        
        return alerts;
    }
    
    /**
     * Obtiene métricas de rendimiento del pool (IOPS, throughput, latencia)
     * @param poolName Nombre del pool
     * @return Nodo JSON con métricas
     * @throws IOException Si falla la consulta
     */
    public JsonNode getPoolMetrics(String poolName) throws IOException {
        log.debug("Consultando métricas del pool {}", poolName);
        
        // Las métricas detalladas requieren comandos específicos de estadísticas
        Map<String, Object> params = new HashMap<>();
        params.put("mdiskgrp", poolName);
        params.put("stats", true);
        
        return apiClient.post("lsmdiskgrp", params);
    }
    
    /**
     * Verifica si hay drives con problemas de salud
     * @return Lista de drives con alertas
     * @throws IOException Si falla la consulta
     */
    public List<String> getUnhealthyDrives() throws IOException {
        log.debug("Verificando salud de drives");
        
        JsonNode response = apiClient.post("lsdrive", 
            Collections.singletonMap("filtervalue", "status=failed"));
        
        List<String> unhealthyDrives = new ArrayList<>();
        JsonNode result = response.path("result");
        
        if (result.isArray()) {
            for (JsonNode node : result) {
                String driveId = node.path("id").asText("");
                String driveName = node.path("name").asText("");
                String status = node.path("status").asText("");
                
                unhealthyDrives.add(String.format("Drive %s (%s): %s", driveId, driveName, status));
            }
        }
        
        return unhealthyDrives;
    }
    
    /**
     * Parsea la respuesta de lsmdiskgrp a StoragePoolInfo
     */
    private StoragePoolInfo parsePoolInfo(JsonNode response) {
        JsonNode pool = response.path("result");
        if (pool.isArray() && pool.size() > 0) {
            pool = pool.get(0);
        }
        
        StoragePoolInfo info = new StoragePoolInfo();
        info.setId(pool.path("id").asText(""));
        info.setName(pool.path("name").asText(config.getStoragePool()));
        info.setTotalCapacity(pool.path("total_capacity").asLong(0L));
        info.setUsedCapacity(pool.path("used_capacity").asLong(0L));
        info.setFreeCapacity(pool.path("free_capacity").asLong(0L));
        
        // Calcular porcentaje de uso
        if (info.getTotalCapacity() > 0) {
            info.setUsagePercent((double) info.getUsedCapacity() / info.getTotalCapacity() * 100.0);
        }
        
        // Tipo de RAID (DRAID)
        info.setRaidType(pool.path("raid_type").asText("unknown"));
        info.setDriveCount(pool.path("drive_count").asInt(0));
        info.setDriveType(pool.path("drive_class").asText("unknown"));
        
        // Data Reduction
        boolean compressed = pool.path("compressed").asBoolean(false);
        boolean deduplicated = pool.path("deduplicated").asBoolean(false);
        info.setDataReductionEnabled(compressed || deduplicated);
        
        // Calcular ratio de reducción estimado
        long physicalUsed = pool.path("physical_used_capacity").asLong(0L);
        if (physicalUsed > 0 && info.getUsedCapacity() > 0) {
            info.setReductionRatio((double) info.getUsedCapacity() / physicalUsed);
        }
        
        info.setStatus(pool.path("status").asText("unknown"));
        info.setEasyTierEnabled(pool.path("tier_enabled").asBoolean(false));
        info.setUtilityModel(config.getUtilityModel());
        
        // Para modelos Utility, la capacidad facturada puede diferir
        if (config.getUtilityModel()) {
            info.setBilledCapacity(pool.path("provisioned_capacity").asLong(0L));
        }
        
        return info;
    }
}
