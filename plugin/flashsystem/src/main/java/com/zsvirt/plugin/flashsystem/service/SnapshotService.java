package com.zsvirt.plugin.flashsystem.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.zsvirt.plugin.flashsystem.api.FlashSystemApiClient;
import com.zsvirt.plugin.flashsystem.model.FlashSystemConfig;
import com.zsvirt.plugin.flashsystem.model.SnapshotInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;

/**
 * Servicio para gestión de snapshots (FlashCopy) en IBM FlashSystem 7300
 * Soporta Safeguarded Snapshots inmutables y operaciones estándar de snapshot
 */
@Slf4j
@Service
public class SnapshotService {
    
    private final FlashSystemApiClient apiClient;
    private final FlashSystemConfig config;
    
    public SnapshotService(FlashSystemApiClient apiClient, FlashSystemConfig config) {
        this.apiClient = apiClient;
        this.config = config;
    }
    
    /**
     * Crea un snapshot de un volumen
     * @param volumeName Nombre del volumen origen
     * @param snapshotName Nombre del snapshot
     * @param description Descripción opcional
     * @param safeguarded Si es true, crea un Safeguarded Snapshot inmutable
     * @return Información del snapshot creado
     * @throws IOException Si falla la creación
     */
    public SnapshotInfo createSnapshot(String volumeName, String snapshotName, 
                                       String description, boolean safeguarded) throws IOException {
        log.info("Creando snapshot {} del volumen {} (safeguarded: {})", snapshotName, volumeName, safeguarded);
        
        Map<String, Object> params = new HashMap<>();
        params.put("fromvdisk_id", volumeName);
        params.put("vdisk_name", snapshotName);
        
        // Para Safeguarded Snapshots se requiere configuración adicional
        if (safeguarded) {
            if (!config.getEnableTpi()) {
                log.warn("TPI no está habilitado, pero se solicita Safeguarded Snapshot");
            }
            params.put("safeguarded", true);
        }
        
        JsonNode response = apiClient.post("mkvdiskcopy", params);
        
        // Obtener información del snapshot creado
        return getSnapshotInfo(snapshotName);
    }
    
    /**
     * Elimina un snapshot existente
     * @param snapshotName Nombre del snapshot
     * @throws IOException Si falla la eliminación
     */
    public void deleteSnapshot(String snapshotName) throws IOException {
        log.info("Eliminando snapshot {}", snapshotName);
        
        // Verificar si es Safeguarded (requiere TPI y aprobación de dos personas)
        SnapshotInfo info = getSnapshotInfo(snapshotName);
        if (info.isSafeguarded()) {
            if (config.getEnableTpi()) {
                log.warn("Snapshot {} es Safeguarded, requiere aprobación TPI para eliminación", snapshotName);
                // En producción, aquí se iniciaría el proceso TPI
            } else {
                log.error("No se puede eliminar Safeguarded Snapshot sin TPI habilitado");
                throw new IOException("No se puede eliminar Safeguarded Snapshot sin Two Person Integrity habilitado");
            }
        }
        
        apiClient.post("rmvdisk", Collections.singletonMap("vdisk_id", snapshotName));
        log.info("Snapshot {} eliminado exitosamente", snapshotName);
    }
    
    /**
     * Obtiene información de un snapshot específico
     * @param snapshotName Nombre del snapshot
     * @return Información del snapshot
     * @throws IOException Si falla la consulta
     */
    public SnapshotInfo getSnapshotInfo(String snapshotName) throws IOException {
        log.debug("Consultando información del snapshot {}", snapshotName);
        
        JsonNode response = apiClient.post("lsvdisk", 
            Collections.singletonMap("vdisk_id", snapshotName));
        
        return parseSnapshotInfo(response, snapshotName);
    }
    
    /**
     * Lista todos los snapshots de un volumen
     * @param volumeName Nombre del volumen origen
     * @return Lista de snapshots
     * @throws IOException Si falla la consulta
     */
    public List<SnapshotInfo> listSnapshotsByVolume(String volumeName) throws IOException {
        log.debug("Listando snapshots del volumen {}", volumeName);
        
        // Listar todos los volúmenes y filtrar los que son copias del volumen origen
        Map<String, Object> params = new HashMap<>();
        params.put("mdiskgrp", config.getStoragePool());
        
        JsonNode response = apiClient.post("lsvdisk", params);
        
        List<SnapshotInfo> snapshots = new ArrayList<>();
        JsonNode result = response.path("result");
        
        if (result.isArray()) {
            for (JsonNode node : result) {
                String masterName = node.path("master_vdisk_name").asText("");
                if (volumeName.equals(masterName)) {
                    snapshots.add(parseSnapshotInfo(node, node.path("name").asText("")));
                }
            }
        }
        
        return snapshots;
    }
    
    /**
     * Restaura un volumen desde un snapshot (rollback)
     * @param volumeName Nombre del volumen destino
     * @param snapshotName Nombre del snapshot origen
     * @throws IOException Si falla la restauración
     */
    public void restoreFromSnapshot(String volumeName, String snapshotName) throws IOException {
        log.info("Restaurando volumen {} desde snapshot {}", volumeName, snapshotName);
        
        // La restauración se hace reemplazando el volumen con el snapshot
        Map<String, Object> params = new HashMap<>();
        params.put("fromvdisk_id", snapshotName);
        params.put("todisk_id", volumeName);
        
        apiClient.post("restorevdiskcopy", params);
        log.info("Volumen {} restaurado exitosamente desde {}", volumeName, snapshotName);
    }
    
    /**
     * Parsea la respuesta de la API a SnapshotInfo
     */
    private SnapshotInfo parseSnapshotInfo(JsonNode response, String snapshotName) {
        JsonNode snapshot = response.path("result");
        if (snapshot.isArray() && snapshot.size() > 0) {
            snapshot = snapshot.get(0);
        }
        
        SnapshotInfo info = new SnapshotInfo();
        info.setId(snapshot.path("id").asText(""));
        info.setName(snapshotName);
        info.setSourceVolumeId(snapshot.path("master_vdisk_id").asText(""));
        info.setSourceVolumeName(snapshot.path("master_vdisk_name").asText(""));
        info.setStatus(snapshot.path("status").asText("unknown"));
        info.setSize(snapshot.path("capacity").asLong(0L));
        info.setSafeguarded(snapshot.path("safeguarded").asBoolean(false));
        info.setCreateTime(snapshot.path("create_time").asLong(System.currentTimeMillis()));
        
        // Extraer VM ID del nombre si sigue el patrón vm-{vmid}-disk-*
        if (snapshotName.matches("vm-\\d+-disk-.*-snap.*")) {
            String[] parts = snapshotName.split("-");
            if (parts.length >= 2) {
                info.setVmId(parts[1]);
            }
        }
        
        return info;
    }
}
