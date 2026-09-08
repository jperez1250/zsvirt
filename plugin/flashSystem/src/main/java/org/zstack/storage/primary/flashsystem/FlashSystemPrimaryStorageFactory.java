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
import org.zstack.storage.primary.flashsystem.model.FlashSystemPool;
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
     * Create a primary storage instance from VO and API message
     */
    @Override
    public PrimaryStorageInventory createPrimaryStorage(PrimaryStorageVO vo, APIAddPrimaryStorageMsg msg) {
        if (!(vo instanceof FlashSystemStorageVO)) {
            throw new IllegalArgumentException(String.format(
                "FlashSystemPrimaryStorageFactory only handles FlashSystemStorageVO, but got %s", 
                vo.getClass().getName()
            ));
        }
        
        FlashSystemStorageVO flashVo = (FlashSystemStorageVO) vo;
        
        // Validate storage configuration before creating
        try {
            validateStorageConfiguration(flashVo);
            
            // Get initial capacity from FlashSystem
            FlashSystemPool pool = apiClient.getPool(flashVo);
            if (pool != null) {
                PrimaryStorageCapacityUpdater updater = new PrimaryStorageCapacityUpdater(vo.getUuid());
                updater.run(new PrimaryStorageCapacityUpdaterCallback() {
                    @Override
                    public PrimaryStorageCapacityVO call(PrimaryStorageCapacityVO cap) {
                        cap.setTotalCapacity(pool.getTotalCapacity());
                        cap.setAvailableCapacity(pool.getFreeCapacity());
                        return cap;
                    }
                });
            }
        } catch (Exception e) {
            logger.warn(String.format("Failed to initialize FlashSystem storage[uuid:%s] capacity: %s", 
                vo.getUuid(), e.getMessage()));
        }
        
        FlashSystemPrimaryStorage ps = new FlashSystemPrimaryStorage(flashVo);
        return ps.getInventory();
    }
    
    /**
     * Get primary storage instance from VO
     */
    @Override
    public PrimaryStorage getPrimaryStorage(PrimaryStorageVO vo) {
        if (!(vo instanceof FlashSystemStorageVO)) {
            throw new IllegalArgumentException(String.format(
                "FlashSystemPrimaryStorageFactory only handles FlashSystemStorageVO, but got %s", 
                vo.getClass().getName()
            ));
        }
        return new FlashSystemPrimaryStorage((FlashSystemStorageVO) vo);
    }
    
    /**
     * Get inventory by UUID
     */
    @Override
    public PrimaryStorageInventory getInventory(String uuid) {
        FlashSystemStorageVO vo = Q.New(FlashSystemStorageVO.class).eq(FlashSystemStorageVO_.uuid, uuid).find();
        if (vo == null) {
            return null;
        }
        return new FlashSystemPrimaryStorage(vo).getInventory();
    }
    
    /**
     * Validate storage protocol (FC or iSCSI)
     */
    @Override
    public void validateStorageProtocol(String protocol) {
        if (!FlashSystemConstant.FC_PROTOCOL.equals(protocol) && 
            !FlashSystemConstant.ISCSI_PROTOCOL.equals(protocol)) {
            throw new IllegalArgumentException(String.format(
                "FlashSystem only supports FC or iSCSI protocol, but got %s", protocol));
        }
    }
    
    /**
     * Validate FlashSystem storage configuration
     */
    private void validateStorageConfiguration(FlashSystemStorageVO scfg) {
        if (scfg.getManagementIp() == null || scfg.getManagementIp().isEmpty()) {
            throw new IllegalArgumentException("FlashSystem management IP is required");
        }
        if (scfg.getUsername() == null || scfg.getUsername().isEmpty()) {
            throw new IllegalArgumentException("FlashSystem username is required");
        }
        if (scfg.getPassword() == null || scfg.getPassword().isEmpty()) {
            throw new IllegalArgumentException("FlashSystem password is required");
        }
        if (scfg.getStoragePool() == null || scfg.getStoragePool().isEmpty()) {
            throw new IllegalArgumentException("FlashSystem storage pool is required");
        }
        
        // Test connection and credentials
        try {
            apiClient.authenticate(scfg);
            logger.info(String.format("Successfully validated FlashSystem connection[ip:%s, pool:%s]", 
                scfg.getManagementIp(), scfg.getStoragePool()));
        } catch (Exception e) {
            throw new IllegalStateException(String.format(
                "Failed to connect to FlashSystem[%s]: %s", scfg.getManagementIp(), e.getMessage()), e);
        }
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
    
    @Override
    public void startVmOnKvmSuccess(KVMHostInventory host, VmInstanceSpec spec) {
        // No post-start actions needed
    }
    
    @Override
    public void startVmOnKvmFailed(KVMHostInventory host, VmInstanceSpec spec, ErrorCode err) {
        logger.warn(String.format("VM %s failed to start on KVM host %s: %s", 
            spec.getVmInventory().getUuid(), host.getManagementIp(), err));
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
            FlashSystemPool pool = apiClient.getPool(scfg);
            if (pool != null) {
                long totalCapacity = pool.getTotalCapacity();
                long availableCapacity = pool.getFreeCapacity();
                
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
