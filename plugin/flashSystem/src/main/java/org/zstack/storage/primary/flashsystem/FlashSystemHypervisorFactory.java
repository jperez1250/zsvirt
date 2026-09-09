package org.zstack.storage.primary.flashsystem;

import org.zstack.header.storage.primary.PrimaryStorageVO;

/**
 * Interface for FlashSystem Hypervisor Factory implementations
 * Following the pattern of SMP HypervisorFactory and SharedBlockHypervisorFactory
 */
public interface FlashSystemHypervisorFactory {
    String getHypervisorType();

    FlashSystemHypervisorBackend getHypervisorBackend(PrimaryStorageVO vo);
}
