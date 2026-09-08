package com.zsvirt.plugin.flashsystem.model;

import lombok.Data;
import java.util.List;

/**
 * Información de snapshot (FlashCopy) en IBM FlashSystem
 */
@Data
public class SnapshotInfo {
    
    /** ID único del snapshot */
    private String id;
    
    /** Nombre del snapshot */
    private String name;
    
    /** ID del volumen padre */
    private String sourceVolumeId;
    
    /** Nombre del volumen padre */
    private String sourceVolumeName;
    
    /** Estado del snapshot */
    private String status;
    
    /** Timestamp de creación */
    private long createTime;
    
    /** Tamaño en bytes */
    private long size;
    
    /** Es un Safeguarded Snapshot (inmutable) */
    private boolean safeguarded;
    
    /** Descripción opcional */
    private String description;
    
    /** VM ID asociada (para ZSVirt) */
    private String vmId;
}
