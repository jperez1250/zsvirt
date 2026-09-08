package com.zsvirt.plugin.flashsystem.config;

import com.zsvirt.plugin.flashsystem.api.FlashSystemApiClient;
import com.zsvirt.plugin.flashsystem.model.FlashSystemConfig;
import com.zsvirt.plugin.flashsystem.security.AuthTokenManager;
import com.zsvirt.plugin.flashsystem.service.SnapshotService;
import com.zsvirt.plugin.flashsystem.service.StorageMonitoringService;
import com.zsvirt.plugin.flashsystem.service.VolumeService;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;

/**
 * Configuración Spring del plugin IBM FlashSystem 7300 para ZSVirt
 */
@Slf4j
@Configuration
public class FlashSystemPluginConfig {
    
    @Value("${flashsystem.management-ip:#{null}}")
    private String managementIp;
    
    @Value("${flashsystem.api-port:7443}")
    private Integer apiPort;
    
    @Value("${flashsystem.username:#{null}}")
    private String username;
    
    @Value("${flashsystem.password:#{null}}")
    private String password;
    
    @Value("${flashsystem.storage-pool:#{null}}")
    private String storagePool;
    
    @Value("${flashsystem.host-group:#{null}}")
    private String hostGroup;
    
    @Value("${flashsystem.session-timeout:60}")
    private Integer sessionTimeout;
    
    @Value("${flashsystem.verify-ssl:true}")
    private Boolean verifySsl;
    
    @Value("${flashsystem.ca-cert-path:#{null}}")
    private String caCertPath;
    
    @Value("${flashsystem.enable-tpi:false}")
    private Boolean enableTpi;
    
    @Value("${flashsystem.provisioning-type:thin}")
    private String provisioningType;
    
    @Value("${flashsystem.enable-data-reduction:true}")
    private Boolean enableDataReduction;
    
    @Value("${flashsystem.utility-model:false}")
    private Boolean utilityModel;
    
    /**
     * Crea la configuración base de FlashSystem desde propiedades
     */
    @Bean
    public FlashSystemConfig flashSystemConfig() {
        log.info("Inicializando configuración de IBM FlashSystem 7300");
        
        if (managementIp == null || storagePool == null) {
            log.warn("Configuración incompleta: se requieren management-ip y storage-pool");
        }
        
        return FlashSystemConfig.builder()
            .managementIp(managementIp)
            .apiPort(apiPort)
            .username(username)
            .password(password)
            .storagePool(storagePool)
            .hostGroup(hostGroup)
            .sessionTimeout(sessionTimeout)
            .verifySsl(verifySsl)
            .caCertificatePath(caCertPath)
            .enableTpi(enableTpi)
            .provisioningType(provisioningType)
            .enableDataReduction(enableDataReduction)
            .utilityModel(utilityModel)
            .build();
    }
    
    /**
     * Crea el cliente HTTP OkHttp con configuración SSL personalizada
     */
    @Bean
    public OkHttpClient okHttpClient(FlashSystemConfig config) throws IOException {
        log.info("Configurando cliente HTTP para FlashSystem");
        
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true);
        
        // Configurar SSL si verifySsl es true
        if (config.getVerifySsl()) {
            if (config.getCaCertificatePath() != null && !config.getCaCertificatePath().isEmpty()) {
                // Usar certificado CA personalizado
                SSLContext sslContext = createSslContextWithCaCert(config.getCaCertificatePath());
                builder.sslSocketFactory(sslContext.getSocketFactory(), getX509TrustManager(sslContext));
                log.info("SSL configurado con certificado CA: {}", config.getCaCertificatePath());
            } else {
                // Usar trust store por defecto de Java
                log.info("SSL configurado con trust store por defecto de Java");
            }
        } else {
            // SSL sin verificación (solo para desarrollo/laboratorio)
            log.warn("Verificación SSL DESHABILITADA - solo usar en entornos de prueba");
            SSLContext sslContext = createUnsafeSslContext();
            builder.sslSocketFactory(sslContext.getSocketFactory(), getAllTrustingTrustManager());
            builder.hostnameVerifier((hostname, session) -> true);
        }
        
        return builder.build();
    }
    
    /**
     * Crea el gestor de tokens de autenticación
     */
    @Bean
    public AuthTokenManager authTokenManager(FlashSystemConfig config, OkHttpClient httpClient) {
        log.info("Inicializando gestor de tokens JWT");
        return new AuthTokenManager(config, httpClient);
    }
    
    /**
     * Crea el cliente API REST
     */
    @Bean
    public FlashSystemApiClient apiClient(FlashSystemConfig config, 
                                          OkHttpClient httpClient,
                                          AuthTokenManager authTokenManager) {
        log.info("Inicializando cliente API REST para FlashSystem {}:{}", 
            config.getManagementIp(), config.getApiPort());
        return new FlashSystemApiClient(config, httpClient, authTokenManager);
    }
    
    /**
     * Crea el servicio de volúmenes
     */
    @Bean
    public VolumeService volumeService(FlashSystemApiClient apiClient, FlashSystemConfig config) {
        return new VolumeService(apiClient, config);
    }
    
    /**
     * Crea el servicio de snapshots
     */
    @Bean
    public SnapshotService snapshotService(FlashSystemApiClient apiClient, FlashSystemConfig config) {
        return new SnapshotService(apiClient, config);
    }
    
    /**
     * Crea el servicio de monitoreo
     */
    @Bean
    public StorageMonitoringService storageMonitoringService(FlashSystemApiClient apiClient, 
                                                             FlashSystemConfig config) {
        return new StorageMonitoringService(apiClient, config);
    }
    
    /**
     * Crea un SSLContext con certificado CA personalizado
     */
    private SSLContext createSslContextWithCaCert(String certPath) throws IOException {
        try {
            // Cargar certificado CA
            FileInputStream certInputStream = new FileInputStream(certPath);
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate caCert = (X509Certificate) cf.generateCertificate(certInputStream);
            
            // Crear KeyStore con el certificado CA
            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null, null);
            keyStore.setCertificateEntry("ca", caCert);
            
            // Crear TrustManagerFactory
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(keyStore);
            
            // Crear SSLContext
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, tmf.getTrustManagers(), new SecureRandom());
            
            return sslContext;
        } catch (Exception e) {
            throw new IOException("Error al cargar certificado CA: " + e.getMessage(), e);
        }
    }
    
    /**
     * Crea un SSLContext que no verifica certificados (INSEGURO)
     */
    private SSLContext createUnsafeSslContext() {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{getAllTrustingTrustManager()}, new SecureRandom());
            return sslContext;
        } catch (Exception e) {
            throw new RuntimeException("Error al crear SSLContext inseguro", e);
        }
    }
    
    /**
     * TrustManager que confía en todos los certificados (INSEGURO)
     */
    private X509TrustManager getAllTrustingTrustManager() {
        return new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) {}
            
            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) {}
            
            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        };
    }
    
    /**
     * Obtiene el X509TrustManager desde un SSLContext
     */
    private X509TrustManager getX509TrustManager(SSLContext sslContext) {
        TrustManager[] trustManagers = sslContext.getDefaultSSLParameters().getTrustManagers();
        for (TrustManager tm : trustManagers) {
            if (tm instanceof X509TrustManager) {
                return (X509TrustManager) tm;
            }
        }
        throw new IllegalStateException("No se encontró X509TrustManager");
    }
}
