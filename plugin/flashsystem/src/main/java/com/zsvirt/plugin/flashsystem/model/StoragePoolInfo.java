package com.zsvirt.plugin.flashsystem.model;

import lombok.Data;

/**
 * Información de capacidad y estado del pool de almacenamiento
 */
@Data
public class StoragePoolInfo {
    
    /** ID del pool */
    private String id;
    
    /** Nombre del pool */
    private String name;
    
    /** Capacidad total en bytes */
    private long totalCapacity;
    
    /** Capacidad utilizada en bytes */
    private long usedCapacity;
    
    /** Capacidad disponible en bytes */
    private long freeCapacity;
    
    /** Porcentaje de uso */
    private double usagePercent;
    
    /** Tipo de RAID (DRAID 1, DRAID 5, DRAID 6) */
    private String raidType;
    
    /** Número de drives en el pool */
    private int driveCount;
    
    /** Tipo de drives (FCM, NVMe, SCM, HDD) */
    private String driveType;
    
    /** Data Reduction habilitado */
    private boolean dataReductionEnabled;
    
    /** Factor de reducción estimado */
    private double reductionRatio;
    
    /** Estado del pool */
    private String status;
    
    /** Easy Tier habilitado */
    private boolean easyTierEnabled;
    
    /** Soporte para modelo Utility (Capacity on Demand) */
    private boolean utilityModel;
    
    /** Capacidad facturada (para modelos Utility) */
    private long billedCapacity;
}
