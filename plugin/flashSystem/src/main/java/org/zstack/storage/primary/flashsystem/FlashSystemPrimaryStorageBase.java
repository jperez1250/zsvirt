package org.zstack.storage.primary.flashsystem;

import org.springframework.beans.factory.annotation.Autowire;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;
import org.zstack.core.Platform;
import org.zstack.core.cloudbus.CloudBus;
import org.zstack.core.componentloader.PluginRegistry;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.core.db.Q;
import org.zstack.header.core.Completion;
import org.zstack.header.core.ReturnValueCompletion;
import org.zstack.header.errorcode.ErrorCode;
import org.zstack.header.host.HostInventory;
import org.zstack.header.message.MessageReply;
import org.zstack.header.storage.primary.*;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;

import java.util.List;

/**
 * Base class for FlashSystem Primary Storage implementations
 * Following the pattern of SMPPrimaryStorageBase and SharedBlockGroupPrimaryStorageBase
 */
@Configurable(preConstruction = true, autowire = Autowire.BY_TYPE)
public class FlashSystemPrimaryStorageBase extends PrimaryStorageBase {
    
    private static final CLogger logger = Utils.getLogger(FlashSystemPrimaryStorageBase.class);
    
    @Autowired
    protected PluginRegistry pluginRgty;
    
    public FlashSystemPrimaryStorageBase() {
        super();
    }
    
    public FlashSystemPrimaryStorageBase(PrimaryStorageVO self) {
        super(self);
    }
    
    /**
     * Get hypervisor backend from registered factories
     */
    protected FlashSystemHypervisorBackend getHypervisorBackend(HypervisorType hvType) {
        List<FlashSystemHypervisorFactory> factories = pluginRgty.getExtensionList(FlashSystemHypervisorFactory.class);
        if (factories == null || factories.isEmpty()) {
            throw new PlatformException(String.format("No FlashSystem hypervisor factory found for type: %s", hvType));
        }
        
        for (FlashSystemHypervisorFactory factory : factories) {
            if (factory.getHypervisorType().equals(hvType.toString())) {
                return factory.getHypervisorBackend(self);
            }
        }
        
        throw new PlatformException(String.format("No FlashSystem hypervisor backend found for type: %s", hvType));
    }
    
    /**
     * Check if host is connected to this FlashSystem storage
     */
    protected boolean isHostConnected(String hostUuid) {
        PrimaryStorageHostStatus status = getHostStatus(hostUuid);
        return status == PrimaryStorageHostStatus.Connected;
    }
    
    /**
     * Get host status
     */
    protected PrimaryStorageHostStatus getHostStatus(String hostUuid) {
        PrimaryStorageHostRefVO ref = Q.New(PrimaryStorageHostRefVO.class)
                .eq(PrimaryStorageHostRefVO_.primaryStorageUuid, self.getUuid())
                .eq(PrimaryStorageHostRefVO_.hostUuid, hostUuid)
                .find();
        
        return ref != null ? ref.getStatus() : PrimaryStorageHostStatus.Disconnected;
    }
}
