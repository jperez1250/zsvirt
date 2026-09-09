package org.zstack.storage.primary.flashsystem;

/**
 * Interface for FlashSystem Primary Storage Hypervisor Specific Messages
 * Following the pattern of SMPPrimaryStorageHypervisorSpecificMessage
 */
public interface FlashSystemPrimaryStorageHypervisorSpecificMessage {
    String getHypervisorType();
}
