package org.zstack.storage.primary.flashsystem.kvm;

import org.springframework.beans.factory.annotation.Autowire;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.header.core.Completion;
import org.zstack.header.errorcode.ErrorCode;
import org.zstack.header.host.HostInventory;
import org.zstack.header.storage.primary.HypervisorBackend;
import org.zstack.header.vm.VmInstanceSpec;
import org.zstack.header.volume.VolumeInventory;
import org.zstack.kvm.KVMAgentCommands;
import org.zstack.kvm.KVMConstant;
import org.zstack.kvm.KVMHostInventory;
import org.zstack.storage.primary.flashsystem.FlashSystemStorageVO;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;

import java.util.HashMap;
import java.util.Map;

import static org.zstack.core.Platform.operr;

/**
 * FlashSystem KVM Hypervisor Backend
 * 
 * Handles KVM-specific volume operations for FlashSystem storage
 */
@Configurable(preConstruction = true, autowire = Autowire.BY_TYPE)
public class FlashSystemKvmBackend implements HypervisorBackend {
    
    private static final CLogger logger = Utils.getLogger(FlashSystemKvmBackend.class);
    
    @Autowired
    private DatabaseFacade dbf;
    
    @Override
    public String getHypervisorType() {
        return KVMConstant.KVM_HYPERVISOR_TYPE;
    }
    
    @Override
    public void handle(org.zstack.header.storage.primary.DownloadVolumeTemplateToPrimaryStorageMsg msg, 
                       Completion completion) {
        // Not implemented - FlashSystem volumes are created directly via REST API
        completion.fail(operr("DownloadVolumeTemplate not supported for FlashSystem storage"));
    }
    
    @Override
    public void handle(org.zstack.header.storage.primary.InstantiateVolumeOnPrimaryStorageMsg msg, 
                       Completion completion) {
        // Volume instantiation is handled by FlashSystemPrimaryStorage
        // This method can be used for KVM-specific setup if needed
        completion.success();
    }
    
    @Override
    public void handle(org.zstack.header.storage.primary.DeleteVolumeOnPrimaryStorageMsg msg, 
                       Completion completion) {
        // Volume deletion is handled by FlashSystemPrimaryStorage
        completion.success();
    }
    
    @Override
    public void handle(org.zstack.header.storage.primary.CreateTemporaryVolumeFromSnapshotMsg msg, 
                       Completion completion) {
        // Create a temporary volume from snapshot using FlashSystem FlashCopy
        completion.fail(operr("CreateTemporaryVolumeFromSnapshot not yet implemented for FlashSystem"));
    }
    
    @Override
    public void handle(org.zstack.header.storage.primary.MergeVolumeSnapshotOnPrimaryStorageMsg msg, 
                       Completion completion) {
        // Snapshots on FlashSystem are managed via FlashCopy, no merge needed
        completion.success();
    }
    
    @Override
    public void beforeStartVm(KVMHostInventory host, VmInstanceSpec spec, KVMAgentCommands.StartVmCmd cmd) {
        // Ensure all FlashSystem volumes are accessible via multipath
        for (VolumeInventory vol : spec.getAllVolumes()) {
            if (vol.getInstallPath() != null && vol.getInstallPath().startsWith("/dev/mapper/mpath-")) {
                logger.debug(String.format("Ensuring multipath device %s is active for VM %s", 
                    vol.getInstallPath(), spec.getVmInventory().getUuid()));
                
                // Add volume to KVM command
                // The actual multipath activation happens at the host level
            }
        }
    }
    
    @Override
    public void afterStartVm(KVMHostInventory host, VmInstanceSpec spec, KVMAgentCommands.StartVmCmd cmd, 
                             KVMAgentCommands.StartVmResponse rsp) {
        // No post-start actions needed
    }
    
    @Override
    public void beforeStopVm(KVMHostInventory host, VmInstanceSpec spec, KVMAgentCommands.StopVmCmd cmd) {
        // No pre-stop actions needed
    }
    
    @Override
    public void afterStopVm(KVMHostInventory host, VmInstanceSpec spec, KVMAgentCommands.StopVmCmd cmd, 
                            KVMAgentCommands.StopVmResponse rsp) {
        // No post-stop actions needed
    }
    
    @Override
    public void beforeMigrateVm(KVMHostInventory srcHost, KVMHostInventory destHost, VmInstanceSpec spec, 
                                KVMAgentCommands.MigrateVmCmd cmd) {
        // For shared storage like FlashSystem, migration doesn't require volume movement
        logger.debug(String.format("VM migration on shared FlashSystem storage from %s to %s", 
            srcHost.getManagementIp(), destHost.getManagementIp()));
    }
    
    @Override
    public void afterMigrateVm(KVMHostInventory srcHost, KVMHostInventory destHost, VmInstanceSpec spec, 
                               KVMAgentCommands.MigrateVmCmd cmd, KVMAgentCommands.MigrateVmResponse rsp) {
        // No post-migration actions needed for shared storage
    }
    
    @Override
    public void attachVolume(KVMHostInventory host, VolumeInventory vol, Completion completion) {
        // Volume is already accessible via multipath on shared storage
        logger.debug(String.format("Volume %s attached to host %s via FlashSystem shared storage", 
            vol.getUuid(), host.getManagementIp()));
        completion.success();
    }
    
    @Override
    public void detachVolume(KVMHostInventory host, VolumeInventory vol, Completion completion) {
        // Volume remains on shared storage, no action needed
        logger.debug(String.format("Volume %s detached from host %s (storage remains on FlashSystem)", 
            vol.getUuid(), host.getManagementIp()));
        completion.success();
    }
}
