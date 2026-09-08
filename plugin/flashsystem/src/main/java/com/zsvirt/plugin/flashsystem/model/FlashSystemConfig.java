package com.zsvirt.plugin.flashsystem.model;

import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

/**
 * Configuración del almacenamiento IBM FlashSystem 7300
 * Optimizado para Storage Virtualize 8.7
 */
@Data
@Builder
@Jacksonized
public class FlashSystemConfig {
    
    /** Dirección IP o hostname del sistema FlashSystem */
    private String managementIp;
    
    /** Puerto REST API (por defecto 7443) */
    @Builder.Default
    private Integer apiPort = 7443;
    
    /** Usuario de servicio para automatización (NO usar Superuser) */
    private String username;
    
    /** Contraseña del usuario */
    private String password;
    
    /** Pool de almacenamiento (mdiskgrp) */
    private String storagePool;
    
    /** Grupo de hosts para mapeo automático */
    private String hostGroup;
    
    /** Timeout de sesión en minutos (10-120) */
    @Builder.Default
    private Integer sessionTimeout = 60;
    
    /** Habilitar verificación SSL */
    @Builder.Default
    private Boolean verifySsl = true;
    
    /** Ruta al certificado CA (opcional) */
    private String caCertificatePath;
    
    /** Habilitar Two Person Integrity (TPI) para operaciones críticas */
    @Builder.Default
    private Boolean enableTpi = false;
    
    /** Tipo de provisioning: thin o thick */
    @Builder.Default
    private String provisioningType = "thin";
    
    /** Habilitar Data Reduction Pool (deduplicación + compresión) */
    @Builder.Default
    private Boolean enableDataReduction = true;
    
    /** Soporte para modelos Utility (Capacity on Demand) */
    @Builder.Default
    private Boolean utilityModel = false;
}
