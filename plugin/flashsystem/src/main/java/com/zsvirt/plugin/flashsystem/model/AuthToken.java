package com.zsvirt.plugin.flashsystem.model;

import lombok.Data;
import java.time.Instant;

/**
 * Token de autenticación JWT para API REST de IBM Storage Virtualize
 */
@Data
public class AuthToken {
    
    /** Token JWT */
    private String token;
    
    /** Timestamp de creación */
    private Instant createdAt;
    
    /** Timestamp de expiración */
    private Instant expiresAt;
    
    /** Timeout configurado en minutos */
    private int timeoutMinutes;
    
    /**
     * Verifica si el token ha expirado
     * @param bufferMinutos Minutos de margen antes de la expiración
     * @return true si el token está expirado o próximo a expirar
     */
    public boolean isExpired(int bufferMinutos) {
        if (expiresAt == null) {
            return true;
        }
        Instant threshold = Instant.now().plusSeconds(bufferMinutos * 60);
        return expiresAt.isBefore(threshold);
    }
    
    /**
     * Verifica si el token es válido
     * @return true si el token no ha expirado
     */
    public boolean isValid() {
        return !isExpired(5); // 5 minutos de buffer por defecto
    }
}
