package org.zstack.storage.primary.flashsystem;

import org.zstack.header.configuration.PythonClass;

/**
 * Constants for IBM FlashSystem Primary Storage Plugin
 * 
 * Based on IBM Storage FlashSystem integration with Proxmox VE
 * Adapted for ZStack/ZSVirt architecture
 */
@PythonClass
public interface FlashSystemConstant {
    
    // Storage type identifier
    String FLASHSYSTEM_PRIMARY_STORAGE_TYPE = "FlashSystem";
    
    // iSCSI protocol for data path
    String ISCSI_PROTOCOL = "iSCSI";
    
    // Fibre Channel protocol for data path  
    String FC_PROTOCOL = "FC";
    
    // REST API configuration
    int DEFAULT_REST_PORT = 7443;
    String REST_API_VERSION = "rest/v1";
    
    // Authentication endpoints
    String AUTH_ENDPOINT = "/auth";
    
    // Volume management endpoints
    String MKVDISK_ENDPOINT = "mkvdisk";
    String RMVDISK_ENDPOINT = "rmvdisk";
    String LSVOLUME_ENDPOINT = "lsvdisk";
    String CHVOLUME_ENDPOINT = "chvolume";
    
    // Snapshot endpoints
    String MKSNAPSHOT_ENDPOINT = "mkvolumesnapshot";
    String RMSNAPSHOT_ENDPOINT = "rmsnapshot";
    String LSVOLUMESNAPSHOT_ENDPOINT = "lsvolumesnapshot";
    String RESTORESNAPSHOT_ENDPOINT = "restorefromsnapshot";
    
    // Host management endpoints
    String MKHOST_ENDPOINT = "mkhost";
    String MKVDISKHOSTMAP_ENDPOINT = "mkvdiskhostmap";
    String RMVDISKHOSTMAP_ENDPOINT = "rmvdiskhostmap";
    String MKHOSTGROUP_ENDPOINT = "mkhostgroup";
    String ADDHOSTTOGROUP_ENDPOINT = "addhosttogroup";
    
    // Pool/Capacity endpoints
    String LSMDISKGRP_ENDPOINT = "lsmdiskgrp";
    
    // Volume group endpoints for policy-based replication
    String MKVOLUMEGROUP_ENDPOINT = "mkvolumegroup";
    String CHVOLUME_VOLUMEGROUP_ENDPOINT = "chvolume -volumegroup";
    
    // Content types supported
    String CONTENT_TYPE_IMAGE = "Image";
    String CONTENT_TYPE_ROOT = "Root";
    String CONTENT_TYPE_DATA = "Data";
    
    // Default timeout for API calls (seconds)
    int DEFAULT_API_TIMEOUT = 30;
    
    // Multipath device prefix
    String MULTIPATH_PREFIX = "/dev/mapper/mpath-";
    
    // Device by-id prefix
    String DEVICE_BY_ID_PREFIX = "/dev/disk/by-id/wwn-0x";
}
