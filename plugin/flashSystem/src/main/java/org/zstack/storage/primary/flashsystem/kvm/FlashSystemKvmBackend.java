package org.zstack.storage.primary.flashsystem.kvm;

import org.springframework.beans.factory.annotation.Autowire;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.header.cluster.ClusterConnectionStatus;
import org.zstack.header.core.Completion;
import org.zstack.header.core.ReturnValueCompletion;
import org.zstack.header.errorcode.ErrorCode;
import org.zstack.header.host.*;
import org.zstack.header.image.ImageInventory;
import org.zstack.header.storage.primary.*;
import org.zstack.header.storage.snapshot.ShrinkVolumeSnapshotOnPrimaryStorageMsg;
import org.zstack.header.storage.snapshot.ShrinkVolumeSnapshotOnPrimaryStorageReply;
import org.zstack.header.storage.snapshot.VolumeSnapshotInventory;
import org.zstack.header.vm.VmInstanceSpec;
import org.zstack.header.volume.BatchSyncVolumeSizeOnPrimaryStorageMsg;
import org.zstack.header.volume.BatchSyncVolumeSizeOnPrimaryStorageReply;
import org.zstack.header.volume.VolumeInventory;
import org.zstack.kvm.*;
import org.zstack.storage.primary.EstimateVolumeTemplateSizeOnPrimaryStorageMsg;
import org.zstack.storage.primary.EstimateVolumeTemplateSizeOnPrimaryStorageReply;
import org.zstack.storage.primary.flashsystem.FlashSystemHypervisorBackend;
import org.zstack.storage.primary.flashsystem.FlashSystemPrimaryStorageHypervisorSpecificMessage;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;

import static org.zstack.core.Platform.operr;

/**
 * FlashSystem KVM Hypervisor Backend Implementation
 * 
 * Handles KVM-specific volume operations for FlashSystem storage
 */
@Configurable(preConstruction = true, autowire = Autowire.BY_TYPE)
public class FlashSystemKvmBackend extends FlashSystemHypervisorBackend {
    
    private static final CLogger logger = Utils.getLogger(FlashSystemKvmBackend.class);
    
    @Autowired
    private DatabaseFacade dbf;
    
    public FlashSystemKvmBackend() {
        super();
    }
    
    public FlashSystemKvmBackend(PrimaryStorageVO self) {
        super(self);
    }
    
    @Override
    public String getHypervisorType() {
        return KVMConstant.KVM_HYPERVISOR_TYPE;
    }
    
    @Override
    void handle(InstantiateVolumeOnPrimaryStorageMsg msg, ReturnValueCompletion<InstantiateVolumeOnPrimaryStorageReply> completion) {
        // Volume instantiation is handled by FlashSystemPrimaryStorage via REST API
        // This method can be used for KVM-specific setup if needed
        logger.debug(String.format("Instantiating volume %s on FlashSystem storage", msg.getVolumeInventory().getUuid()));
        InstantiateVolumeOnPrimaryStorageReply reply = new InstantiateVolumeOnPrimaryStorageReply();
        completion.success(reply);
    }
    
    @Override
    void handle(DownloadVolumeTemplateToPrimaryStorageMsg msg, ReturnValueCompletion<DownloadVolumeTemplateToPrimaryStorageReply> completion) {
        // Not implemented - FlashSystem volumes are created directly via REST API
        completion.fail(operr("DownloadVolumeTemplate not supported for FlashSystem storage"));
    }
    
    @Override
    void handle(DeleteVolumeOnPrimaryStorageMsg msg, ReturnValueCompletion<DeleteVolumeOnPrimaryStorageReply> completion) {
        // Volume deletion is handled by FlashSystemPrimaryStorage via REST API
        logger.debug(String.format("Deleting volume %s from FlashSystem storage", msg.getVolumeUuid()));
        DeleteVolumeOnPrimaryStorageReply reply = new DeleteVolumeOnPrimaryStorageReply();
        completion.success(reply);
    }
    
    @Override
    void handle(DownloadDataVolumeToPrimaryStorageMsg msg, ReturnValueCompletion<DownloadDataVolumeToPrimaryStorageReply> completion) {
        completion.fail(operr("DownloadDataVolume not supported for FlashSystem storage"));
    }
    
    @Override
    void handle(GetInstallPathForDataVolumeDownloadMsg msg, ReturnValueCompletion<GetInstallPathForDataVolumeDownloadReply> completion) {
        completion.fail(operr("GetInstallPathForDataVolumeDownload not supported for FlashSystem storage"));
    }
    
    @Override
    void handle(DeleteVolumeBitsOnPrimaryStorageMsg msg, ReturnValueCompletion<DeleteVolumeBitsOnPrimaryStorageReply> completion) {
        DeleteVolumeBitsOnPrimaryStorageReply reply = new DeleteVolumeBitsOnPrimaryStorageReply();
        completion.success(reply);
    }
    
    @Override
    void handle(DeleteBitsOnPrimaryStorageMsg msg, ReturnValueCompletion<DeleteBitsOnPrimaryStorageReply> completion) {
        DeleteBitsOnPrimaryStorageReply reply = new DeleteBitsOnPrimaryStorageReply();
        completion.success(reply);
    }
    
    @Override
    void handle(DownloadIsoToPrimaryStorageMsg msg, ReturnValueCompletion<DownloadIsoToPrimaryStorageReply> completion) {
        completion.fail(operr("DownloadIso not supported for FlashSystem storage"));
    }
    
    @Override
    void handle(DeleteIsoFromPrimaryStorageMsg msg, ReturnValueCompletion<DeleteIsoFromPrimaryStorageReply> completion) {
        completion.fail(operr("DeleteIso not supported for FlashSystem storage"));
    }
    
    @Override
    void handle(CheckSnapshotMsg msg, Completion completion) {
        completion.fail(operr("CheckSnapshot not yet implemented for FlashSystem"));
    }
    
    @Override
    void handle(TakeSnapshotMsg msg, ReturnValueCompletion<TakeSnapshotReply> completion) {
        completion.fail(operr("TakeSnapshot not yet implemented for FlashSystem"));
    }
    
    @Override
    void handle(DeleteSnapshotOnPrimaryStorageMsg msg, ReturnValueCompletion<DeleteSnapshotOnPrimaryStorageReply> completion) {
        completion.fail(operr("DeleteSnapshot not yet implemented for FlashSystem"));
    }
    
    @Override
    void handle(RevertVolumeFromSnapshotOnPrimaryStorageMsg msg, ReturnValueCompletion<RevertVolumeFromSnapshotOnPrimaryStorageReply> completion) {
        completion.fail(operr("RevertVolumeFromSnapshot not yet implemented for FlashSystem"));
    }
    
    @Override
    void handle(ReInitRootVolumeFromTemplateOnPrimaryStorageMsg msg, ReturnValueCompletion<ReInitRootVolumeFromTemplateOnPrimaryStorageReply> completion) {
        completion.fail(operr("ReInitRootVolumeFromTemplate not supported for FlashSystem"));
    }
    
    @Override
    void handle(CreateVolumeFromVolumeSnapshotOnPrimaryStorageMsg msg, ReturnValueCompletion<CreateVolumeFromVolumeSnapshotOnPrimaryStorageReply> completion) {
        completion.fail(operr("CreateVolumeFromVolumeSnapshot not yet implemented for FlashSystem"));
    }
    
    @Override
    void stream(VolumeSnapshotInventory from, VolumeInventory to, boolean fullRebase, Completion completion) {
        completion.fail(operr("Snapshot streaming not yet implemented for FlashSystem"));
    }
    
    @Override
    void handle(CreateTemporaryVolumeFromSnapshotMsg msg, ReturnValueCompletion<CreateTemporaryVolumeFromSnapshotReply> completion) {
        completion.fail(operr("CreateTemporaryVolumeFromSnapshot not yet implemented for FlashSystem"));
    }
    
    @Override
    void handle(UploadBitsToBackupStorageMsg msg, ReturnValueCompletion<UploadBitsToBackupStorageReply> completion) {
        completion.fail(operr("UploadBitsToBackupStorage not supported for FlashSystem"));
    }
    
    @Override
    void deleteBits(String path, Completion completion) {
        completion.success();
    }
    
    @Override
    void deleteBits(String path, boolean folder, Completion completion) {
        completion.success();
    }
    
    @Override
    void handle(CreateImageCacheFromVolumeOnPrimaryStorageMsg msg, ReturnValueCompletion<CreateImageCacheFromVolumeOnPrimaryStorageReply> completion) {
        completion.fail(operr("CreateImageCacheFromVolume not supported for FlashSystem"));
    }
    
    @Override
    void handle(CreateImageCacheFromVolumeSnapshotOnPrimaryStorageMsg msg, ReturnValueCompletion<CreateImageCacheFromVolumeSnapshotOnPrimaryStorageReply> completion) {
        completion.fail(operr("CreateImageCacheFromVolumeSnapshot not supported for FlashSystem"));
    }
    
    @Override
    void handle(CreateTemplateFromVolumeOnPrimaryStorageMsg msg, ReturnValueCompletion<CreateTemplateFromVolumeOnPrimaryStorageReply> completion) {
        completion.fail(operr("CreateTemplateFromVolume not supported for FlashSystem"));
    }
    
    @Override
    void connectByClusterUuid(String clusterUuid, boolean rescan, ReturnValueCompletion<ClusterConnectionStatus> completion) {
        // For shared storage, connection is always successful if host has multipath configured
        logger.debug(String.format("Connecting FlashSystem storage to cluster %s", clusterUuid));
        completion.success(new ClusterConnectionStatus());
    }
    
    @Override
    void disconnectByClusterUuid(String clusterUuid, Completion completion) {
        logger.debug(String.format("Disconnecting FlashSystem storage from cluster %s", clusterUuid));
        completion.success();
    }
    
    @Override
    void handle(SyncVolumeSizeOnPrimaryStorageMsg msg, ReturnValueCompletion<SyncVolumeSizeOnPrimaryStorageReply> completion) {
        SyncVolumeSizeOnPrimaryStorageReply reply = new SyncVolumeSizeOnPrimaryStorageReply();
        completion.success(reply);
    }
    
    @Override
    void handle(EstimateVolumeTemplateSizeOnPrimaryStorageMsg msg, ReturnValueCompletion<EstimateVolumeTemplateSizeOnPrimaryStorageReply> completion) {
        completion.fail(operr("EstimateVolumeTemplateSize not supported for FlashSystem"));
    }
    
    @Override
    void handle(BatchSyncVolumeSizeOnPrimaryStorageMsg msg, ReturnValueCompletion<BatchSyncVolumeSizeOnPrimaryStorageReply> completion) {
        BatchSyncVolumeSizeOnPrimaryStorageReply reply = new BatchSyncVolumeSizeOnPrimaryStorageReply();
        completion.success(reply);
    }
    
    @Override
    void handle(BackupVolumeSnapshotFromPrimaryStorageToBackupStorageMsg msg, ReturnValueCompletion<BackupVolumeSnapshotFromPrimaryStorageToBackupStorageReply> completion) {
        completion.fail(operr("BackupVolumeSnapshot not yet implemented for FlashSystem"));
    }
    
    @Override
    void handle(AskInstallPathForNewSnapshotMsg msg, ReturnValueCompletion<AskInstallPathForNewSnapshotReply> completion) {
        completion.fail(operr("AskInstallPathForNewSnapshot not yet implemented for FlashSystem"));
    }
    
    @Override
    void downloadImageToCache(ImageInventory img, ReturnValueCompletion<String> completion) {
        completion.fail(operr("Image cache not supported for FlashSystem"));
    }
    
    @Override
    void getPhysicalCapacity(PrimaryStorageInventory inv, ReturnValueCompletion<PhysicalCapacityUsage> completion) {
        PhysicalCapacityUsage usage = new PhysicalCapacityUsage();
        usage.totalPhysicalSize = inv.getTotalCapacity();
        usage.availablePhysicalSize = inv.getAvailableCapacity();
        completion.success(usage);
    }
    
    @Override
    void handleHypervisorSpecificMessage(FlashSystemPrimaryStorageHypervisorSpecificMessage msg) {
        logger.debug(String.format("Handling hypervisor-specific message: %s", msg.getClass().getName()));
    }
    
    @Override
    void beforeSnapshotTake(KVMHostInventory host, TakeSnapshotOnHypervisorMsg msg, KVMAgentCommands.TakeSnapshotCmd cmd, Completion completion) {
        completion.fail(operr("Snapshot not yet implemented for FlashSystem"));
    }
    
    @Override
    void afterSnapshotTake(KVMHostInventory host, TakeSnapshotOnHypervisorMsg msg, KVMAgentCommands.TakeSnapshotCmd cmd, KVMAgentCommands.TakeSnapshotResponse rsp) {
        // No-op
    }
    
    @Override
    void afterSnapshotTakeFailed(KVMHostInventory host, TakeSnapshotOnHypervisorMsg msg, KVMAgentCommands.TakeSnapshotCmd cmd, KVMAgentCommands.TakeSnapshotResponse rsp, ErrorCode err) {
        logger.warn(String.format("Snapshot take failed: %s", err));
    }
}
