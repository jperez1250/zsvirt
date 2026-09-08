package org.zstack.storage.primary.flashsystem;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.zstack.core.cloudbus.CloudBus;
import org.zstack.core.componentloader.PluginRegistry;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.core.db.Q;
import org.zstack.header.core.Completion;
import org.zstack.header.errorcode.ErrorCode;
import org.zstack.header.host.HostInventory;
import org.zstack.header.host.HostVO;
import org.zstack.header.host.HypervisorType;
import org.zstack.header.message.MessageReply;
import org.zstack.header.storage.primary.*;
import org.zstack.header.vm.VmInstanceSpec;
import org.zstack.header.volume.VolumeInventory;
import org.zstack.header.volume.VolumeVO;
import org.zstack.kvm.KVMStartVmExtensionPoint;
import org.zstack.kvm.KVMAgentCommands;
import org.zstack.storage.primary.PrimaryStorageBase;
import org.zstack.storage.primary.PrimaryStorageCapacityUpdater;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;

import java.util.*;

import static org.zstack.core.Platform.operr;

/**
 * IBM FlashSystem Primary Storage Factory
 * 
 * Implements the PrimaryStorageFactory interface to integrate FlashSystem storage with ZStack
 */
public class FlashSystemPrimaryStorageFactory implements PrimaryStorageFactory, KVMStartVmExtensionPoint {
    
    private static final CLogger logger = Utils.getLogger(FlashSystemPrimaryStorageFactory.class);
    
    public static final PrimaryStorageType type = new PrimaryStorageType(FlashSystemConstant.FLASHSYSTEM_PRIMARY_STORAGE_TYPE);
    
    static {
        // FlashSystem provides shared block storage via iSCSI or FC
        type.setSupportSharedVolume(true);
        type.setSupportCheckHostStatus(true);
        type.setOrder(800);
    }
    
    @Autowired
    private DatabaseFacade dbf;
    
    @Autowired
    private CloudBus bus;
    
    @Autowired
    private PluginRegistry pluginRgty;
    
    @Autowired
    private FlashSystemApiClient apiClient;
    
    /**
     * Return the storage type
     */
    @Override
    public PrimaryStorageType getPrimaryStorageType() {
        return type;
    }
    
    /**
     * Create a primary storage instance from VO
     */
    @Override
    public PrimaryStorage createPrimaryStorage(PrimaryStorageVO vo) {
        if (!(vo instanceof FlashSystemStorageVO)) {
            throw new IllegalArgumentException(String.format(
                "FlashSystemPrimaryStorageFactory only handles FlashSystemStorageVO, but got %s", 
                vo.getClass().getName()
            ));
        }
        return new FlashSystemPrimaryStorage((FlashSystemStorageVO) vo);
    }
    
    /**
     * Get hypervisor backend for volume operations
     */
    public FlashSystemHypervisorBackend getHypervisorBackend(HypervisorType hvType) {
        for (FlashSystemHypervisorBackend backend : pluginRgty.getExtensionList(FlashSystemHypervisorBackend.class)) {
            if (backend.getHypervisorType().equals(hvType.toString())) {
                return backend;
            }
        }
        throw new UnsupportedOperationException(String.format("No FlashSystem hypervisor backend found for type: %s", hvType));
    }
    
    /**
     * Get API client for FlashSystem communication
     */
    public FlashSystemApiClient getApiClient() {
        return apiClient;
    }
    
    @Override
    public void beforeStartVmOnKvm(KVMHostInventory host, VmInstanceSpec spec, KVMAgentCommands.StartVmCmd cmd) {
        // Check if any volumes are on FlashSystem storage
        List<VolumeInventory> flashSystemVolumes = new ArrayList<>();
        for (VolumeInventory vol : spec.getAllVolumes()) {
            if (isFlashSystemVolume(vol)) {
                flashSystemVolumes.add(vol);
            }
        }
        
        if (flashSystemVolumes.isEmpty()) {
            return;
        }
        
        logger.debug(String.format("Found %d FlashSystem volumes for VM %s", flashSystemVolumes.size(), spec.getVmInventory().getUuid()));
        
        // Ensure multipath is active and volumes are accessible
        // This will be handled by the KVM backend
    }
    
    /**
     * Check if a volume is backed by FlashSystem storage
     */
    private boolean isFlashSystemVolume(VolumeInventory vol) {
        PrimaryStorageInventory ps = vol.getPrimaryStorage();
        return ps != null && FlashSystemConstant.FLASHSYSTEM_PRIMARY_STORAGE_TYPE.equals(ps.getType());
    }
    
    /**
     * Recalculate storage capacity from FlashSystem
     */
    public void recalculateCapacity(String storageUuid, final Completion completion) {
        FlashSystemStorageVO scfg = dbf.findByUuid(storageUuid, FlashSystemStorageVO.class);
        if (scfg == null) {
            completion.fail(operr("FlashSystem storage[uuid:%s] not found", storageUuid));
            return;
        }
        
        try {
            // Query FlashSystem pool capacity via REST API
            Map<String, Object> params = new HashMap<>();
            params.put("mdiskgrp", scfg.getStoragePool());
            
            com.fasterxml.jackson.databind.JsonNode response = apiClient.get(scfg, 
                FlashSystemConstant.LSMDISKGRP_ENDPOINT + "/" + scfg.getStoragePool());
            
            if (response != null && response.has("capacity")) {
                long totalCapacity = response.path("capacity").asLong() * 1024 * 1024; // Convert to bytes
                long usedCapacity = response.path("used_capacity").asLong() * 1024 * 1024;
                long availableCapacity = totalCapacity - usedCapacity;
                
                // Update ZStack capacity
                PrimaryStorageCapacityUpdater updater = new PrimaryStorageCapacityUpdater(storageUuid);
                updater.run(new PrimaryStorageCapacityUpdaterCallback() {
                    @Override
                    public PrimaryStorageCapacityVO call(PrimaryStorageCapacityVO cap) {
                        cap.setTotalCapacity(totalCapacity);
                        cap.setAvailableCapacity(availableCapacity);
                        return cap;
                    }
                });
                
                logger.info(String.format("Recalculated FlashSystem storage[uuid:%s] capacity: total=%d, available=%d", 
                    storageUuid, totalCapacity, availableCapacity));
                
                completion.success();
            } else {
                completion.fail(operr("Failed to get capacity from FlashSystem storage pool: %s", scfg.getStoragePool()));
            }
            
        } catch (Exception e) {
            logger.warn(String.format("Failed to recalculate FlashSystem storage[uuid:%s] capacity: %s", 
                storageUuid, e.getMessage()));
            completion.fail(operr("Failed to recalculate FlashSystem capacity: %s", e.getMessage()));
        }
    }
}
