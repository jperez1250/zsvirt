package org.zstack.storage.primary.flashsystem;

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
import org.zstack.header.vm.metadata.UpdateVmInstanceMetadataOnPrimaryStorageMsg;
import org.zstack.header.vm.metadata.UpdateVmInstanceMetadataOnPrimaryStorageReply;
import org.zstack.header.volume.BatchSyncVolumeSizeOnPrimaryStorageMsg;
import org.zstack.header.volume.BatchSyncVolumeSizeOnPrimaryStorageReply;
import org.zstack.header.volume.VolumeInventory;
import org.zstack.kvm.KVMAgentCommands;
import org.zstack.kvm.KVMHostInventory;
import org.zstack.storage.primary.EstimateVolumeTemplateSizeOnPrimaryStorageMsg;
import org.zstack.storage.primary.EstimateVolumeTemplateSizeOnPrimaryStorageReply;

/**
 * Abstract base class for FlashSystem Hypervisor Backend implementations
 * Following the pattern of SharedBlockHypervisorBackend
 */
public abstract class FlashSystemHypervisorBackend extends FlashSystemPrimaryStorageBase {
    
    public FlashSystemHypervisorBackend() {
        super();
    }

    public FlashSystemHypervisorBackend(PrimaryStorageVO self) {
        super(self);
    }

    // Volume lifecycle operations
    abstract void handle(InstantiateVolumeOnPrimaryStorageMsg msg, ReturnValueCompletion<InstantiateVolumeOnPrimaryStorageReply> completion);
    
    abstract void handle(DownloadVolumeTemplateToPrimaryStorageMsg msg, ReturnValueCompletion<DownloadVolumeTemplateToPrimaryStorageReply> completion);
    
    abstract void handle(DeleteVolumeOnPrimaryStorageMsg msg, ReturnValueCompletion<DeleteVolumeOnPrimaryStorageReply> completion);
    
    abstract void handle(DownloadDataVolumeToPrimaryStorageMsg msg, ReturnValueCompletion<DownloadDataVolumeToPrimaryStorageReply> completion);
    
    abstract void handle(GetInstallPathForDataVolumeDownloadMsg msg, ReturnValueCompletion<GetInstallPathForDataVolumeDownloadReply> completion);
    
    abstract void handle(DeleteVolumeBitsOnPrimaryStorageMsg msg, ReturnValueCompletion<DeleteVolumeBitsOnPrimaryStorageReply> completion);
    
    abstract void handle(DeleteBitsOnPrimaryStorageMsg msg, ReturnValueCompletion<DeleteBitsOnPrimaryStorageReply> completion);
    
    abstract void handle(DownloadIsoToPrimaryStorageMsg msg, ReturnValueCompletion<DownloadIsoToPrimaryStorageReply> completion);
    
    abstract void handle(DeleteIsoFromPrimaryStorageMsg msg, ReturnValueCompletion<DeleteIsoFromPrimaryStorageReply> completion);
    
    // Snapshot operations
    abstract void handle(CheckSnapshotMsg msg, Completion completion);
    
    abstract void handle(TakeSnapshotMsg msg, ReturnValueCompletion<TakeSnapshotReply> completion);
    
    abstract void handle(DeleteSnapshotOnPrimaryStorageMsg msg, ReturnValueCompletion<DeleteSnapshotOnPrimaryStorageReply> completion);
    
    abstract void handle(RevertVolumeFromSnapshotOnPrimaryStorageMsg msg, ReturnValueCompletion<RevertVolumeFromSnapshotOnPrimaryStorageReply> completion);
    
    abstract void handle(ReInitRootVolumeFromTemplateOnPrimaryStorageMsg msg, ReturnValueCompletion<ReInitRootVolumeFromTemplateOnPrimaryStorageReply> completion);
    
    abstract void handle(CreateVolumeFromVolumeSnapshotOnPrimaryStorageMsg msg, ReturnValueCompletion<CreateVolumeFromVolumeSnapshotOnPrimaryStorageReply> completion);
    
    abstract void stream(VolumeSnapshotInventory from, VolumeInventory to, boolean fullRebase, Completion completion);
    
    abstract void handle(CreateTemporaryVolumeFromSnapshotMsg msg, ReturnValueCompletion<CreateTemporaryVolumeFromSnapshotReply> completion);
    
    abstract void handle(UploadBitsToBackupStorageMsg msg, ReturnValueCompletion<UploadBitsToBackupStorageReply> completion);
    
    abstract void deleteBits(String path, Completion completion);
    
    abstract void deleteBits(String path, boolean folder, Completion completion);
    
    // Image cache operations
    abstract void handle(CreateImageCacheFromVolumeOnPrimaryStorageMsg msg, ReturnValueCompletion<CreateImageCacheFromVolumeOnPrimaryStorageReply> completion);
    
    abstract void handle(CreateImageCacheFromVolumeSnapshotOnPrimaryStorageMsg msg, ReturnValueCompletion<CreateImageCacheFromVolumeSnapshotOnPrimaryStorageReply> completion);
    
    abstract void handle(CreateTemplateFromVolumeOnPrimaryStorageMsg msg, ReturnValueCompletion<CreateTemplateFromVolumeOnPrimaryStorageReply> completion);
    
    // Connection management
    abstract void connectByClusterUuid(String clusterUuid, boolean rescan, ReturnValueCompletion<ClusterConnectionStatus> completion);
    
    abstract void disconnectByClusterUuid(String clusterUuid, Completion completion);
    
    // Capacity and sync operations
    abstract void handle(SyncVolumeSizeOnPrimaryStorageMsg msg, ReturnValueCompletion<SyncVolumeSizeOnPrimaryStorageReply> completion);
    
    abstract void handle(EstimateVolumeTemplateSizeOnPrimaryStorageMsg msg, ReturnValueCompletion<EstimateVolumeTemplateSizeOnPrimaryStorageReply> completion);
    
    abstract void handle(BatchSyncVolumeSizeOnPrimaryStorageMsg msg, ReturnValueCompletion<BatchSyncVolumeSizeOnPrimaryStorageReply> completion);
    
    abstract void handle(BackupVolumeSnapshotFromPrimaryStorageToBackupStorageMsg msg, ReturnValueCompletion<BackupVolumeSnapshotFromPrimaryStorageToBackupStorageReply> completion);
    
    abstract void handle(AskInstallPathForNewSnapshotMsg msg, ReturnValueCompletion<AskInstallPathForNewSnapshotReply> completion);
    
    // Image cache download
    abstract void downloadImageToCache(ImageInventory img, ReturnValueCompletion<String> completion);
    
    // Physical capacity
    abstract void getPhysicalCapacity(PrimaryStorageInventory inv, ReturnValueCompletion<PhysicalCapacityUsage> completion);
    
    // Hypervisor-specific messages
    abstract void handleHypervisorSpecificMessage(FlashSystemPrimaryStorageHypervisorSpecificMessage msg);
    
    // Snapshot extension points
    abstract void beforeSnapshotTake(KVMHostInventory host, TakeSnapshotOnHypervisorMsg msg, KVMAgentCommands.TakeSnapshotCmd cmd, Completion completion);
    
    abstract void afterSnapshotTake(KVMHostInventory host, TakeSnapshotOnHypervisorMsg msg, KVMAgentCommands.TakeSnapshotCmd cmd, KVMAgentCommands.TakeSnapshotResponse rsp);
    
    abstract void afterSnapshotTakeFailed(KVMHostInventory host, TakeSnapshotOnHypervisorMsg msg, KVMAgentCommands.TakeSnapshotCmd cmd, KVMAgentCommands.TakeSnapshotResponse rsp, ErrorCode err);
}
