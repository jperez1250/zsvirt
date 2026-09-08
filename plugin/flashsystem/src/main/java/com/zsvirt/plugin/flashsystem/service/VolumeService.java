package com.zsvirt.plugin.flashsystem.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.zsvirt.plugin.flashsystem.api.FlashSystemApiClient;
import com.zsvirt.plugin.flashsystem.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Servicio principal para gestión de volúmenes en IBM FlashSystem 7300
 * Implementa operaciones CRUD de volúmenes con soporte para DRAID, thin provisioning y Data Reduction
 */
@Slf4j
@Service
public class VolumeService {
    
    private final FlashSystemApiClient apiClient;
    private final FlashSystemConfig config;
    
    public VolumeService(FlashSystemApiClient apiClient, FlashSystemConfig config) {
        this.apiClient = apiClient;
        this.config = config;
    }
    
    /**
     * Crea un nuevo volumen en FlashSystem
     * @param vmid ID de la VM propietaria
     * @param name Nombre del volumen
     * @param sizeBytes Tamaño en bytes
     * @return Información del volumen creado
     * @throws IOException Si falla la creación
     */
    public VolumeInfo createVolume(String vmid, String name, long sizeBytes) throws IOException {
        log.info("Creando volumen {} para VM {} con tamaño {} bytes", name, vmid, sizeBytes);
        
        long sizeMB = sizeBytes / (1024 * 1024);
        
        Map<String, Object> params = new HashMap<>();
        params.put("name", name);
        params.put("mdiskgrp", config.getStoragePool());
        params.put("size", sizeMB);
        params.put("unit", "mb");
        params.put("rsize", config.getProvisioningType().equals("thin") ? 0 : 100);
        
        // Habilitar Data Reduction si está configurado
        if (config.getEnableDataReduction()) {
            params.put("compressed", true);
            params.put("deduplicated", true);
        }
        
        // Crear volumen
        JsonNode response = apiClient.post("mkvdisk", params);
        String volumeId = extractIdFromResponse(response);
        
        // Mapear al host group
        mapVolumeToHost(volumeId);
        
        // Obtener información completa del volumen
        return getVolumeInfo(volumeId);
    }
    
    /**
     * Elimina un volumen existente
     * @param volumeName Nombre del volumen
     * @throws IOException Si falla la eliminación
     */
    public void deleteVolume(String volumeName) throws IOException {
        log.info("Eliminando volumen {}", volumeName);
        
        // Primero desmapear del host group
        unmapVolumeFromHost(volumeName);
        
        // Eliminar volumen
        apiClient.post("rmvdisk", Collections.singletonMap("vdisk_id", volumeName));
        
        log.info("Volumen {} eliminado exitosamente", volumeName);
    }
    
    /**
     * Obtiene información detallada de un volumen
     * @param volumeId ID o nombre del volumen
     * @return Información del volumen
     * @throws IOException Si falla la consulta
     */
    public VolumeInfo getVolumeInfo(String volumeId) throws IOException {
        log.debug("Consultando información del volumen {}", volumeId);
        
        JsonNode response = apiClient.post("lsvdisk", 
            Collections.singletonMap("vdisk_id", volumeId));
        
        return parseVolumeInfo(response);
    }
    
    /**
     * Lista todos los volúmenes del pool configurado
     * @return Lista de volúmenes
     * @throws IOException Si falla la consulta
     */
    public List<VolumeInfo> listVolumes() throws IOException {
        log.debug("Listando volúmenes del pool {}", config.getStoragePool());
        
        Map<String, Object> params = new HashMap<>();
        params.put("mdiskgrp", config.getStoragePool());
        params.put("bytes", true);
        
        JsonNode response = apiClient.post("lsvdisk", params);
        
        return parseVolumeList(response);
    }
    
    /**
     * Lista volúmenes de una VM específica
     * @param vmid ID de la VM
     * @return Lista de volúmenes
     * @throws IOException Si falla la consulta
     */
    public List<VolumeInfo> listVolumesByVm(String vmid) throws IOException {
        log.debug("Listando volúmenes para VM {}", vmid);
        
        List<VolumeInfo> allVolumes = listVolumes();
        
        return allVolumes.stream()
            .filter(v -> vmid.equals(v.getVmId()))
            .collect(Collectors.toList());
    }
    
    /**
     * Mapea un volumen al grupo de hosts configurado
     */
    private void mapVolumeToHost(String volumeId) throws IOException {
        if (config.getHostGroup() != null && !config.getHostGroup().isEmpty()) {
            log.debug("Mapeando volumen {} al host group {}", volumeId, config.getHostGroup());
            
            Map<String, Object> params = new HashMap<>();
            params.put("vdisk_id", volumeId);
            params.put("host", config.getHostGroup());
            
            apiClient.post("mkvdiskhostmap", params);
        }
    }
    
    /**
     * Desmappa un volumen del grupo de hosts
     */
    private void unmapVolumeFromHost(String volumeId) throws IOException {
        if (config.getHostGroup() != null && !config.getHostGroup().isEmpty()) {
            log.debug("Desmappando volumen {} del host group {}", volumeId, config.getHostGroup());
            
            Map<String, Object> params = new HashMap<>();
            params.put("vdisk_id", volumeId);
            params.put("host", config.getHostGroup());
            
            try {
                apiClient.post("rmvdiskhostmap", params);
            } catch (Exception e) {
                log.warn("No se pudo desmappar el volumen {}, puede que no esté mapeado: {}", volumeId, e.getMessage());
            }
        }
    }
    
    /**
     * Extrae el ID de la respuesta de creación
     */
    private String extractIdFromResponse(JsonNode response) {
        // La respuesta típica es: {"result": [{"id": "0"}]}
        JsonNode result = response.path("result");
        if (result.isArray() && result.size() > 0) {
            return result.get(0).path("id").asText();
        }
        // Alternativa: buscar directamente el ID
        return response.path("id").asText();
    }
    
    /**
     * Parsea la respuesta de lsvdisk a VolumeInfo
     */
    private VolumeInfo parseVolumeInfo(JsonNode response) {
        JsonNode volume = response.path("result");
        if (volume.isArray() && volume.size() > 0) {
            volume = volume.get(0);
        }
        
        VolumeInfo info = new VolumeInfo();
        info.setId(volume.path("id").asText(""));
        info.setName(volume.path("name").asText(""));
        info.setCapacity(volume.path("capacity").asLong(0L));
        info.setUsedCapacity(volume.path("used_capacity").asLong(0L));
        info.setStoragePool(volume.path("mdisk_grp_name").asText(""));
        info.setProvisioningType(volume.path("se_copy").asBoolean(false) ? "thin" : "thick");
        info.setStatus(volume.path("status").asText("unknown"));
        info.setWwid(volume.path("udid").asText(""));
        info.setDataReductionEnabled(volume.path("compressed").asBoolean(false) || volume.path("deduplicated").asBoolean(false));
        
        // Calcular ratio de reducción si hay datos
        if (info.getDataReductionEnabled() && info.getUsedCapacity() > 0) {
            // Estimación basada en capacidad virtual vs física
            info.setReductionRatio((double) info.getCapacity() / Math.max(info.getUsedCapacity(), 1));
        }
        
        info.setCreateTime(volume.path("create_time").asLong(System.currentTimeMillis()));
        
        // Extraer VM ID del nombre si sigue el patrón vm-{vmid}-disk-*
        String name = info.getName();
        if (name.matches("vm-\\d+-disk-.*")) {
            String[] parts = name.split("-");
            if (parts.length >= 2) {
                info.setVmId(parts[1]);
            }
        }
        
        return info;
    }
    
    /**
     * Parsea la lista de volúmenes
     */
    private List<VolumeInfo> parseVolumeList(JsonNode response) {
        List<VolumeInfo> volumes = new ArrayList<>();
        JsonNode result = response.path("result");
        
        if (result.isArray()) {
            for (JsonNode node : result) {
                volumes.add(parseVolumeInfo(node));
            }
        }
        
        return volumes;
    }
}
