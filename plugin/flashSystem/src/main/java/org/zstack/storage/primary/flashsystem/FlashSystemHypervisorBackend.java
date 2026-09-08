package org.zstack.storage.primary.flashsystem;

import org.zstack.header.host.HypervisorType;

/**
 * Interface for FlashSystem Hypervisor Backend implementations
 */
public interface FlashSystemHypervisorBackend {
    
    /**
     * Get the hypervisor type (e.g., "KVM")
     */
    String getHypervisorType();
    
    /**
     * Get the hypervisor backend instance
     */
    Object getHypervisorBackend();
}
