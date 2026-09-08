package com.zstack.storage.flashsystem.api;

import com.zstack.storage.flashsystem.service.FlashSystemService;
import com.zstack.storage.flashsystem.service.FlashSystemService.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * API REST para gestión de IBM FlashSystem 7300 / Storage Virtualize 8.7 desde ZStack
 * 
 * Endpoints:
 * - POST   /api/flashsystem/test         - Verificar conexión
 * - GET    /api/flashsystem/capacity     - Obtener capacidad del pool
 * - POST   /api/flashsystem/volumes      - Crear volumen
 * - GET    /api/flashsystem/volumes      - Listar volúmenes
 * - DELETE /api/flashsystem/volumes/{id} - Eliminar volumen
 * - POST   /api/flashsystem/snapshots    - Crear snapshot
 * - GET    /api/flashsystem/snapshots    - Listar snapshots
 * - POST   /api/flashsystem/snapshots/{id}/restore - Restaurar snapshot
 * - DELETE /api/flashsystem/snapshots/{id}        - Eliminar snapshot
 * - POST   /api/flashsystem/hosts        - Registrar host
 * - POST   /api/flashsystem/hosts/map    - Mapear volumen a host
 * - GET    /api/flashsystem/alerts       - Verificar alertas
 */
@RestController
@RequestMapping("/api/flashsystem")
@CrossOrigin(origins = "*")
public class FlashSystemApiController {
    
    @Autowired
    private FlashSystemService flashSystemService;
    
    /**
     * Verificar conexión con FlashSystem
     */
    @PostMapping("/test")
    public ResponseEntity<Map<String, Object>> testConnection(
            @RequestBody ConnectionRequest request) {
        
        Map<String, Object> response = new HashMap<>();
        boolean success = flashSystemService.testConnection(
            request.getBaseUrl(),
            request.getUsername(),
            request.getPassword()
        );
        
        response.put("success", success);
        response.put("message", success ? "Conexión exitosa" : "Error de conexión");
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Obtener capacidad del storage pool
     */
    @GetMapping("/capacity")
    public ResponseEntity<StorageCapacity> getCapacity(
            @RequestParam String baseUrl,
            @RequestParam String username,
            @RequestParam String password,
            @RequestParam String poolName) {
        
        StorageCapacity capacity = flashSystemService.getStorageCapacity(
            baseUrl, username, password, poolName
        );
        
        return ResponseEntity.ok(capacity);
    }
    
    /**
     * Crear volumen
     */
    @PostMapping("/volumes")
    public ResponseEntity<VolumeInfo> createVolume(@RequestBody CreateVolumeRequest request) {
        
        VolumeOptions options = new VolumeOptions();
        options.setThinProvisioning(request.isThinProvisioning());
        options.setCompression(request.isCompression());
        options.setDeduplication(request.isDeduplication());
        
        VolumeInfo volume = flashSystemService.createVolume(
            request.getBaseUrl(),
            request.getUsername(),
            request.getPassword(),
            request.getVolumeName(),
            request.getSizeGB(),
            request.getPoolName(),
            options
        );
        
        return ResponseEntity.ok(volume);
    }
    
    /**
     * Listar volúmenes
     */
    @GetMapping("/volumes")
    public ResponseEntity<List<VolumeInfo>> listVolumes(
            @RequestParam String baseUrl,
            @RequestParam String username,
            @RequestParam String password,
            @RequestParam(required = false) String poolName,
            @RequestParam(required = false) String volumeGroupName) {
        
        List<VolumeInfo> volumes = flashSystemService.listVolumes(
            baseUrl, username, password, poolName, volumeGroupName
        );
        
        return ResponseEntity.ok(volumes);
    }
    
    /**
     * Eliminar volumen
     */
    @DeleteMapping("/volumes/{volumeId}")
    public ResponseEntity<Void> deleteVolume(
            @RequestParam String baseUrl,
            @RequestParam String username,
            @RequestParam String password,
            @PathVariable String volumeId) {
        
        flashSystemService.deleteVolume(baseUrl, username, password, volumeId);
        return ResponseEntity.ok().build();
    }
    
    /**
     * Crear snapshot
     */
    @PostMapping("/snapshots")
    public ResponseEntity<SnapshotInfo> createSnapshot(@RequestBody CreateSnapshotRequest request) {
        
        SnapshotInfo snapshot = flashSystemService.createSnapshot(
            request.getBaseUrl(),
            request.getUsername(),
            request.getPassword(),
            request.getSourceVolumeId(),
            request.getSnapshotName(),
            request.isSafeguarded()
        );
        
        return ResponseEntity.ok(snapshot);
    }
    
    /**
     * Listar snapshots
     */
    @GetMapping("/snapshots")
    public ResponseEntity<List<SnapshotInfo>> listSnapshots(
            @RequestParam String baseUrl,
            @RequestParam String username,
            @RequestParam String password,
            @RequestParam String volumeId) {
        
        List<SnapshotInfo> snapshots = flashSystemService.listSnapshots(
            baseUrl, username, password, volumeId
        );
        
        return ResponseEntity.ok(snapshots);
    }
    
    /**
     * Restaurar snapshot
     */
    @PostMapping("/snapshots/{snapshotId}/restore")
    public ResponseEntity<Void> restoreSnapshot(
            @RequestParam String baseUrl,
            @RequestParam String username,
            @RequestParam String password,
            @PathVariable String snapshotId) {
        
        flashSystemService.restoreSnapshot(baseUrl, username, password, snapshotId);
        return ResponseEntity.ok().build();
    }
    
    /**
     * Eliminar snapshot
     */
    @DeleteMapping("/snapshots/{snapshotId}")
    public ResponseEntity<Void> deleteSnapshot(
            @RequestParam String baseUrl,
            @RequestParam String username,
            @RequestParam String password,
            @PathVariable String snapshotId) {
        
        flashSystemService.deleteSnapshot(baseUrl, username, password, snapshotId);
        return ResponseEntity.ok().build();
    }
    
    /**
     * Registrar host
     */
    @PostMapping("/hosts")
    public ResponseEntity<Map<String, Object>> registerHost(@RequestBody RegisterHostRequest request) {
        
        flashSystemService.registerHost(
            request.getBaseUrl(),
            request.getUsername(),
            request.getPassword(),
            request.getHostname(),
            request.getHostGroup(),
            request.getWwpns()
        );
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Host registrado correctamente");
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Mapear volumen a host
     */
    @PostMapping("/hosts/map")
    public ResponseEntity<Map<String, Object>> mapVolumeToHost(@RequestBody MapVolumeRequest request) {
        
        flashSystemService.mapVolumeToHost(
            request.getBaseUrl(),
            request.getUsername(),
            request.getPassword(),
            request.getVolumeId(),
            request.getHostId(),
            request.getLun()
        );
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Volumen mapeado correctamente");
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Verificar alertas del sistema
     */
    @GetMapping("/alerts")
    public ResponseEntity<AlertStatus> checkAlerts(
            @RequestParam String baseUrl,
            @RequestParam String username,
            @RequestParam String password) {
        
        AlertStatus status = flashSystemService.checkAlerts(baseUrl, username, password);
        return ResponseEntity.ok(status);
    }
    
    // Request DTOs
    
    public static class ConnectionRequest {
        private String baseUrl;
        private String username;
        private String password;
        
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }
    
    public static class CreateVolumeRequest {
        private String baseUrl;
        private String username;
        private String password;
        private String volumeName;
        private long sizeGB;
        private String poolName;
        private boolean thinProvisioning = true;
        private boolean compression = true;
        private boolean deduplication = false;
        
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getVolumeName() { return volumeName; }
        public void setVolumeName(String volumeName) { this.volumeName = volumeName; }
        public long getSizeGB() { return sizeGB; }
        public void setSizeGB(long sizeGB) { this.sizeGB = sizeGB; }
        public String getPoolName() { return poolName; }
        public void setPoolName(String poolName) { this.poolName = poolName; }
        public boolean isThinProvisioning() { return thinProvisioning; }
        public void setThinProvisioning(boolean thinProvisioning) { this.thinProvisioning = thinProvisioning; }
        public boolean isCompression() { return compression; }
        public void setCompression(boolean compression) { this.compression = compression; }
        public boolean isDeduplication() { return deduplication; }
        public void setDeduplication(boolean deduplication) { this.deduplication = deduplication; }
    }
    
    public static class CreateSnapshotRequest {
        private String baseUrl;
        private String username;
        private String password;
        private String sourceVolumeId;
        private String snapshotName;
        private boolean safeguarded = false;
        
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getSourceVolumeId() { return sourceVolumeId; }
        public void setSourceVolumeId(String sourceVolumeId) { this.sourceVolumeId = sourceVolumeId; }
        public String getSnapshotName() { return snapshotName; }
        public void setSnapshotName(String snapshotName) { this.snapshotName = snapshotName; }
        public boolean isSafeguarded() { return safeguarded; }
        public void setSafeguarded(boolean safeguarded) { this.safeguarded = safeguarded; }
    }
    
    public static class RegisterHostRequest {
        private String baseUrl;
        private String username;
        private String password;
        private String hostname;
        private String hostGroup;
        private List<String> wwpns;
        
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getHostname() { return hostname; }
        public void setHostname(String hostname) { this.hostname = hostname; }
        public String getHostGroup() { return hostGroup; }
        public void setHostGroup(String hostGroup) { this.hostGroup = hostGroup; }
        public List<String> getWwpns() { return wwpns; }
        public void setWwpns(List<String> wwpns) { this.wwpns = wwpns; }
    }
    
    public static class MapVolumeRequest {
        private String baseUrl;
        private String username;
        private String password;
        private String volumeId;
        private String hostId;
        private int lun;
        
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getVolumeId() { return volumeId; }
        public void setVolumeId(String volumeId) { this.volumeId = volumeId; }
        public String getHostId() { return hostId; }
        public void setHostId(String hostId) { this.hostId = hostId; }
        public int getLun() { return lun; }
        public void setLun(int lun) { this.lun = lun; }
    }
}
