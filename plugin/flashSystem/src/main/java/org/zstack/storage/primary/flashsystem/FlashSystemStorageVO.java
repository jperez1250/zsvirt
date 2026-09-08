package org.zstack.storage.primary.flashsystem;

import org.zstack.header.storage.primary.PrimaryStorageVO;
import org.zstack.header.vo.ResourceType;

import javax.persistence.*;

/**
 * IBM FlashSystem Primary Storage Value Object
 * 
 * Extends base PrimaryStorageVO with FlashSystem-specific configuration
 */
@Entity
@Table(name = "FlashSystemPrimaryStorageVO")
@ResourceType(name = "FlashSystemPrimaryStorage")
public class FlashSystemStorageVO extends PrimaryStorageVO {
    
    /**
     * Management IP address or hostname of the FlashSystem storage array
     */
    @Column
    private String managementIp;
    
    /**
     * Username for FlashSystem REST API authentication
     */
    @Column
    private String username;
    
    /**
     * Encrypted password for FlashSystem REST API authentication
     */
    @Column
    private String password;
    
    /**
     * Storage pool name on FlashSystem (mdiskgrp)
     */
    @Column
    private String storagePool;
    
    /**
     * Host group name for automatic host mapping
     */
    @Column
    private String hostGroup;
    
    /**
     * Storage protocol: iSCSI or FC
     */
    @Column
    private String protocol = FlashSystemConstant.ISCSI_PROTOCOL;
    
    /**
     * iSCSI IQN target name (for iSCSI protocol)
     */
    @Column
    private String iqnTarget;
    
    /**
     * REST API port (default: 7443)
     */
    @Column
    private Integer restApiPort = FlashSystemConstant.DEFAULT_REST_PORT;
    
    // Getters and Setters
    
    public String getManagementIp() {
        return managementIp;
    }
    
    public void setManagementIp(String managementIp) {
        this.managementIp = managementIp;
    }
    
    public String getUsername() {
        return username;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }
    
    public String getPassword() {
        return password;
    }
    
    public void setPassword(String password) {
        this.password = password;
    }
    
    public String getStoragePool() {
        return storagePool;
    }
    
    public void setStoragePool(String storagePool) {
        this.storagePool = storagePool;
    }
    
    public String getHostGroup() {
        return hostGroup;
    }
    
    public void setHostGroup(String hostGroup) {
        this.hostGroup = hostGroup;
    }
    
    public String getProtocol() {
        return protocol;
    }
    
    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }
    
    public String getIqnTarget() {
        return iqnTarget;
    }
    
    public void setIqnTarget(String iqnTarget) {
        this.iqnTarget = iqnTarget;
    }
    
    public Integer getRestApiPort() {
        return restApiPort;
    }
    
    public void setRestApiPort(Integer restApiPort) {
        this.restApiPort = restApiPort;
    }
}
