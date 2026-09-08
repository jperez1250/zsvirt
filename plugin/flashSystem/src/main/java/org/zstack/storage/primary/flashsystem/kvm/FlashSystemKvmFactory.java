package org.zstack.storage.primary.flashsystem.kvm;

import org.springframework.beans.factory.annotation.Autowire;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.header.host.HypervisorType;
import org.zstack.storage.primary.flashsystem.FlashSystemHypervisorBackend;

/**
 * FlashSystem KVM Hypervisor Factory
 * 
 * Provides KVM backend for FlashSystem storage
 */
@Configurable(preConstruction = true, autowire = Autowire.BY_TYPE)
public class FlashSystemKvmFactory implements FlashSystemHypervisorBackend {
    
    @Autowired
    private DatabaseFacade dbf;
    
    @Autowired
    private FlashSystemKvmBackend kvmBackend;
    
    @Override
    public String getHypervisorType() {
        return "KVM";
    }
    
    @Override
    public FlashSystemKvmBackend getHypervisorBackend() {
        return kvmBackend;
    }
}
