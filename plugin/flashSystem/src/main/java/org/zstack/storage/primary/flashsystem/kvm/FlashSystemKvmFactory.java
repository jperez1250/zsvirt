package org.zstack.storage.primary.flashsystem.kvm;

import org.springframework.beans.factory.annotation.Autowire;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.header.host.HypervisorType;
import org.zstack.header.storage.primary.PrimaryStorageVO;
import org.zstack.header.storage.primary.HypervisorBackend;
import org.zstack.header.storage.primary.HypervisorBackendFactory;
import org.zstack.kvm.KVMConstant;
import org.zstack.storage.primary.flashsystem.FlashSystemHypervisorBackend;
import org.zstack.storage.primary.flashsystem.FlashSystemHypervisorFactory;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;

/**
 * FlashSystem KVM Hypervisor Factory
 * 
 * Provides KVM backend for FlashSystem storage
 * Implements both HypervisorBackendFactory and FlashSystemHypervisorFactory for automatic discovery by ZStack
 */
@Configurable(preConstruction = true, autowire = Autowire.BY_TYPE)
public class FlashSystemKvmFactory implements HypervisorBackendFactory, FlashSystemHypervisorFactory {
    
    private static final CLogger logger = Utils.getLogger(FlashSystemKvmFactory.class);
    
    @Autowired
    private DatabaseFacade dbf;
    
    @Autowired
    private FlashSystemKvmBackend kvmBackend;
    
    @Override
    public String getHypervisorType() {
        return KVMConstant.KVM_HYPERVISOR_TYPE;
    }
    
    @Override
    public HypervisorBackend getHypervisorBackend() {
        logger.debug("Returning FlashSystemKvmBackend for KVM hypervisor");
        return kvmBackend;
    }
    
    @Override
    public FlashSystemHypervisorBackend getHypervisorBackend(PrimaryStorageVO vo) {
        logger.debug(String.format("Returning FlashSystemKvmBackend for PrimaryStorage %s", vo.getUuid()));
        return kvmBackend;
    }
}
