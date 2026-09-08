package org.zstack.storage.primary.flashsystem;

import org.springframework.beans.factory.annotation.Autowire;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;
import org.zstack.core.asyncbatch.While;
import org.zstack.core.cloudbus.CloudBus;
import org.zstack.core.cloudbus.CloudBusCallBack;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.core.db.Q;
import org.zstack.header.core.Completion;
import org.zstack.header.core.NoErrorCompletion;
import org.zstack.header.errorcode.ErrorCode;
import org.zstack.header.host.*;
import org.zstack.header.message.MessageReply;
import org.zstack.header.storage.primary.*;
import org.zstack.header.storage.snapshot.VolumeSnapshotInventory;
import org.zstack.header.vm.VmInstanceSpec;
import org.zstack.header.volume.*;
import org.zstack.storage.primary.PrimaryStorageBase;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.zstack.core.Platform.operr;

/**
 * IBM FlashSystem Primary Storage Implementation
 * 
 * Handles volume lifecycle operations (create, delete, snapshot) via FlashSystem REST API
 */
@Configurable(preConstruction = true, autowire = Autowire.BY_TYPE)
public class FlashSystemPrimaryStorage extends PrimaryStorageBase {
    
    private static final CLogger logger = Utils.getLogger(FlashSystemPrimaryStorage.class);
    
    @Autowired
    private DatabaseFacade dbf;
    
    @Autowired
    private CloudBus bus;
    
    @Autowired
    private FlashSystemApiClient apiClient;
    
    public FlashSystemPrimaryStorage() {
    }
    
    public FlashSystemPrimaryStorage(FlashSystemStorageVO vo) {
        super(vo);
    }
    
    /**
     * Get FlashSystem storage configuration
     */
    private FlashSystemStorageVO getFlashSystemConfig() {
        return dbf.findByUuid(self.getUuid(), FlashSystemStorageVO.class);
    }
    
    @Override
    protected void handle(AllocatePrimaryStorageMsg msg) {
        // FlashSystem uses thin provisioning, so we don't need to pre-allocate
        AllocatePrimaryStorageReply reply = new AllocatePrimaryStorageReply();
        bus.reply(msg, reply);
    }
    
    @Override
    protected void handle(final InstantiateVolumeOnPrimaryStorageMsg msg) {
        FlashSystemStorageVO scfg = getFlashSystemConfig();
        if (scfg == null) {
            InstantiateVolumeOnPrimaryStorageReply reply = new InstantiateVolumeOnPrimaryStorageReply();
            reply.setError(operr("FlashSystem storage[uuid:%s] configuration not found", self.getUuid()));
            bus.reply(msg, reply);
            return;
        }
        
        try {
            VolumeInventory vol = msg.getVolume();
            String volumeName = buildVolumeName(vol.getUuid());
            
            // Create volume on FlashSystem via REST API
            Map<String, Object> createParams = new HashMap<>();
            createParams.put("name", volumeName);
            createParams.put("mdiskgrp", scfg.getStoragePool());
            createParams.put("size", getSizeInMB(vol.getSize()));
            createParams.put("unit", "mb");
            createParams.put("thinprovisioned", true); // Enable thin provisioning
            
            com.fasterxml.jackson.databind.JsonNode response = apiClient.post(scfg, 
                FlashSystemConstant.MKVDISK_ENDPOINT, createParams);
            
            if (response != null && response.has("id")) {
                String wwid = response.path("id").asText();
                
                // Map volume to host group for automatic access
                mapVolumeToHostGroup(scfg, volumeName);
                
                // Build install path (WWID for multipath device mapping)
                String installPath = buildInstallPath(wwid, scfg.getProtocol());
                
                InstantiateVolumeOnPrimaryStorageReply reply = new InstantiateVolumeOnPrimaryStorageReply();
                reply.setFormat(vol.getFormat());
                reply.setInstallPath(installPath);
                reply.setActualSize(vol.getSize());
                reply.setVolumeUuid(vol.getUuid());
                
                logger.info(String.format("Successfully created FlashSystem volume[name:%s, wwid:%s, size:%dMB]", 
                    volumeName, wwid, getSizeInMB(vol.getSize())));
                
                bus.reply(msg, reply);
            } else {
                throw new Exception("No volume ID returned from FlashSystem");
            }
            
        } catch (Exception e) {
            logger.error(String.format("Failed to instantiate volume on FlashSystem: %s", e.getMessage()), e);
            InstantiateVolumeOnPrimaryStorageReply reply = new InstantiateVolumeOnPrimaryStorageReply();
            reply.setError(operr("Failed to create FlashSystem volume: %s", e.getMessage()));
            bus.reply(msg, reply);
        }
    }
    
    @Override
    protected void handle(final DeleteVolumeOnPrimaryStorageMsg msg) {
        FlashSystemStorageVO scfg = getFlashSystemConfig();
        if (scfg == null) {
            DeleteVolumeOnPrimaryStorageReply reply = new DeleteVolumeOnPrimaryStorageReply();
            reply.setError(operr("FlashSystem storage[uuid:%s] configuration not found", self.getUuid()));
            bus.reply(msg, reply);
            return;
        }
        
        try {
            VolumeInventory vol = msg.getVolume();
            String volumeName = extractVolumeName(vol.getInstallPath());
            
            if (volumeName != null) {
                // Unmap volume from host group first
                unmapVolumeFromHostGroup(scfg, volumeName);
                
                // Delete volume from FlashSystem
                apiClient.delete(scfg, FlashSystemConstant.RMVDISK_ENDPOINT + "/" + volumeName);
                
                logger.info(String.format("Successfully deleted FlashSystem volume[name:%s]", volumeName));
            }
            
            DeleteVolumeOnPrimaryStorageReply reply = new DeleteVolumeOnPrimaryStorageReply();
            bus.reply(msg, reply);
            
        } catch (Exception e) {
            logger.error(String.format("Failed to delete FlashSystem volume: %s", e.getMessage()), e);
            DeleteVolumeOnPrimaryStorageReply reply = new DeleteVolumeOnPrimaryStorageReply();
            reply.setError(operr("Failed to delete FlashSystem volume: %s", e.getMessage()));
            bus.reply(msg, reply);
        }
    }
    
    @Override
    protected void handle(TakeSnapshotMsg msg) {
        FlashSystemStorageVO scfg = getFlashSystemConfig();
        if (scfg == null) {
            TakeSnapshotReply reply = new TakeSnapshotReply();
            reply.setError(operr("FlashSystem storage[uuid:%s] configuration not found", self.getUuid()));
            bus.reply(msg, reply);
            return;
        }
        
        try {
            VolumeSnapshotInventory snapshot = msg.getSnapshot();
            String volumeName = extractVolumeName(snapshot.getVolumeInstallPath());
            String snapshotName = buildSnapshotName(snapshot.getUuid());
            
            // Create snapshot on FlashSystem using FlashCopy
            Map<String, Object> snapshotParams = new HashMap<>();
            snapshotParams.put("name", snapshotName);
            snapshotParams.put("vdisk_id", volumeName);
            
            com.fasterxml.jackson.databind.JsonNode response = apiClient.post(scfg, 
                FlashSystemConstant.MKSNAPSHOT_ENDPOINT, snapshotParams);
            
            if (response != null && response.has("id")) {
                String snapshotId = response.path("id").asText();
                
                TakeSnapshotReply reply = new TakeSnapshotReply();
                reply.setSnapshotInventory(snapshot);
                // Store FlashSystem snapshot ID in metadata
                Map<String, String> meta = new HashMap<>();
                meta.put("flashSystemSnapshotId", snapshotId);
                reply.setMetadata(meta);
                
                logger.info(String.format("Successfully created FlashSystem snapshot[name:%s, id:%s]", 
                    snapshotName, snapshotId));
                
                bus.reply(msg, reply);
            } else {
                throw new Exception("No snapshot ID returned from FlashSystem");
            }
            
        } catch (Exception e) {
            logger.error(String.format("Failed to create FlashSystem snapshot: %s", e.getMessage()), e);
            TakeSnapshotReply reply = new TakeSnapshotReply();
            reply.setError(operr("Failed to create FlashSystem snapshot: %s", e.getMessage()));
            bus.reply(msg, reply);
        }
    }
    
    @Override
    protected void handle(DeleteSnapshotMsg msg) {
        FlashSystemStorageVO scfg = getFlashSystemConfig();
        if (scfg == null) {
            DeleteSnapshotReply reply = new DeleteSnapshotReply();
            reply.setError(operr("FlashSystem storage[uuid:%s] configuration not found", self.getUuid()));
            bus.reply(msg, reply);
            return;
        }
        
        try {
            VolumeSnapshotInventory snapshot = msg.getSnapshot();
            String snapshotName = buildSnapshotName(snapshot.getUuid());
            
            // Delete snapshot from FlashSystem
            apiClient.delete(scfg, FlashSystemConstant.RMSNAPSHOT_ENDPOINT + "/" + snapshotName);
            
            logger.info(String.format("Successfully deleted FlashSystem snapshot[name:%s]", snapshotName));
            
            DeleteSnapshotReply reply = new DeleteSnapshotReply();
            bus.reply(msg, reply);
            
        } catch (Exception e) {
            logger.error(String.format("Failed to delete FlashSystem snapshot: %s", e.getMessage()), e);
            DeleteSnapshotReply reply = new DeleteSnapshotReply();
            reply.setError(operr("Failed to delete FlashSystem snapshot: %s", e.getMessage()));
            bus.reply(msg, reply);
        }
    }
    
    /**
     * Map volume to host group for automatic access by all hosts
     */
    private void mapVolumeToHostGroup(FlashSystemStorageVO scfg, String volumeName) {
        if (scfg.getHostGroup() != null && !scfg.getHostGroup().isEmpty()) {
            Map<String, Object> params = new HashMap<>();
            params.put("vdisk_id", volumeName);
            params.put("hostcluster", scfg.getHostGroup());
            
            apiClient.post(scfg, FlashSystemConstant.ADDHOSTENDPOINT, params);
            logger.debug(String.format("Mapped volume[%s] to host group[%s]", volumeName, scfg.getHostGroup()));
        }
    }
    
    /**
     * Unmap volume from host group
     */
    private void unmapVolumeFromHostGroup(FlashSystemStorageVO scfg, String volumeName) {
        if (scfg.getHostGroup() != null && !scfg.getHostGroup().isEmpty()) {
            Map<String, Object> params = new HashMap<>();
            params.put("vdisk_id", volumeName);
            params.put("hostcluster", scfg.getHostGroup());
            
            apiClient.post(scfg, FlashSystemConstant.RMHOSTENDPOINT, params);
            logger.debug(String.format("Unmapped volume[%s] from host group[%s]", volumeName, scfg.getHostGroup()));
        }
    }
    
    /**
     * Build volume name from UUID
     */
    private String buildVolumeName(String uuid) {
        return "vol_" + uuid.replace("-", "");
    }
    
    /**
     * Build snapshot name from UUID
     */
    private String buildSnapshotName(String uuid) {
        return "snap_" + uuid.replace("-", "");
    }
    
    /**
     * Extract volume name from install path
     */
    private String extractVolumeName(String installPath) {
        if (installPath == null) {
            return null;
        }
        // Install path format: /dev/mapper/mpath-<wwid> or similar
        // We need to query FlashSystem to get the volume name from WWID
        // For now, return null - this should be enhanced to query FlashSystem
        return null;
    }
    
    /**
     * Build install path from WWID and protocol
     */
    private String buildInstallPath(String wwid, String protocol) {
        if (FlashSystemConstant.FC_PROTOCOL.equals(protocol)) {
            return FlashSystemConstant.MULTIPATH_PREFIX + wwid;
        } else {
            // iSCSI also uses multipath
            return FlashSystemConstant.MULTIPATH_PREFIX + wwid;
        }
    }
    
    /**
     * Convert bytes to MB
     */
    private long getSizeInMB(long bytes) {
        return bytes / (1024 * 1024);
    }
}
