package com.zsvirt.plugin.flashsystem.model;

import lombok.Data;
import java.util.List;

/**
 * Representación de volumen/disco virtual en IBM FlashSystem
 */
@Data
public class VolumeInfo {
    
    /** ID único del volumen */
    private String id;
    
    /** Nombre del volumen */
    private String name;
    
    /** Capacidad en bytes */
    private long capacity;
    
    /** Capacidad utilizada en bytes */
    private long usedCapacity;
    
    /** Pool de almacenamiento (mdiskgrp) */
    private String storagePool;
    
    /** Tipo de provisioning: thin o thick */
    private String provisioningType;
    
    /** Estado del volumen */
    private String status;
    
    /** WWID (World Wide Identifier) para mapeo a dispositivo */
    private String wwid;
    
    /** ID del grupo de hosts mapeado */
    private String hostGroupId;
    
    /** Snapshots asociados */
    private List<String> snapshots;
    
    /** Habilita Data Reduction (deduplicación + compresión) */
    private boolean dataReductionEnabled;
    
    /** Factor de reducción de datos estimado */
    private double reductionRatio;
    
    /** Timestamp de creación */
    private long createTime;
    
    /** VM ID asociada (para ZSVirt) */
    private String vmId;
}
