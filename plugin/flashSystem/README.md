# IBM FlashSystem 7300 & Storage Virtualize 8.7 Plugin para ZStack/ZSVirt

## Descripción General

Plugin de almacenamiento empresarial para integrar **IBM FlashSystem 7300** con **Storage Virtualize 8.7** en plataformas de virtualización **ZStack/ZSVirt**. Este plugin automatiza la gestión de volúmenes, snapshots FlashCopy, host mapping y monitoreo de alertas mediante la API REST de IBM.

## Características Principales

### Hardware Soportado
- **IBM FlashSystem 7300** (Machine Type 4657, Models 924/U7D)
- Control enclosure 2U NVMe con hasta 1.5 TB cache
- Escalabilidad scale-up/scale-out hasta 32 PB
- Conectividad: 32 Gbps FC, 10/25/100 Gbps Ethernet (iSCSI, NVMe-oF)

### Funcionalidades de Software
- ✅ **DRAID** (Distributed RAID 1/5/6) con reconstrucción rápida
- ✅ **Thin Provisioning** con compresión hardware (3:1) y software (5:1)
- ✅ **Snapshots FlashCopy** inmutables (Safeguarded) para protección ransomware
- ✅ **Replicación Policy-Based** (Metro/Global Mirror Sync/Async)
- ✅ **Easy Tier** autotiering entre NVMe/SSD/HDD
- ✅ **Detección AI Ransomware** a nivel de drive
- ✅ **Monitoreo en tiempo real** de capacidad y alertas

## Arquitectura del Plugin

```
┌─────────────────────────────────────────────────────────────┐
│                    ZStack/ZSVirt Platform                    │
│  ┌──────────────────────────────────────────────────────┐   │
│  │           FlashSystemApiController (REST API)         │   │
│  │  - /api/flashsystem/test                              │   │
│  │  - /api/flashsystem/volumes                           │   │
│  │  - /api/flashsystem/snapshots                         │   │
│  │  - /api/flashsystem/hosts                             │   │
│  │  - /api/flashsystem/alerts                            │   │
│  └──────────────────────────────────────────────────────┘   │
│                          │                                   │
│  ┌──────────────────────────────────────────────────────┐   │
│  │              FlashSystemService                       │   │
│  │  - Gestión de volúmenes y pools                       │   │
│  │  - Snapshots Safeguarded                              │   │
│  │  - Host registration (FC/iSCSI)                       │   │
│  │  - Monitoreo de alertas                               │   │
│  └──────────────────────────────────────────────────────┘   │
│                          │                                   │
│  ┌──────────────────────────────────────────────────────┐   │
│  │            FlashSystemRestClient                      │   │
│  │  - Autenticación JWT con caché (10-120 min)          │   │
│  │  - HTTPS REST API calls                               │   │
│  │  - Token refresh automático                           │   │
│  └──────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
                            │ HTTPS (port 7443)
                            ▼
┌─────────────────────────────────────────────────────────────┐
│              IBM FlashSystem 7300 / SV 8.7                   │
│  ┌──────────────────────────────────────────────────────┐   │
│  │              REST API (port 7443)                     │   │
│  │  - /rest/v1/auth                                      │   │
│  │  - /rest/v1/lsvdisk, mkvdisk, rmvdisk                │   │
│  │  - /rest/v1/mkfcmap, lsvolumesnapshot                │   │
│  │  - /rest/v1/mkhost, mkvdiskhostmap                   │   │
│  │  - /rest/v1/lsmdiskgrp, lseventlog                   │   │
│  └──────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
                            │ FC/iSCSI (Data Path)
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                    KVM Hypervisor                            │
│  - Multipath device mapping (/dev/mapper/mpath-*)           │
│  - LUN discovery and rescan                                  │
│  - Volume attachment to VMs                                  │
└─────────────────────────────────────────────────────────────┘
```

## Estructura del Código

```
plugin/flashSystem/
├── src/main/java/com/zstack/storage/flashsystem/
│   ├── api/
│   │   └── FlashSystemApiController.java      # API REST endpoints
│   ├── client/
│   │   └── FlashSystemRestClient.java         # Cliente HTTP REST
│   ├── model/
│   │   ├── AuthTokenResponse.java             # Token autenticación
│   │   ├── FlashSystemVolume.java             # Modelo volumen
│   │   ├── FlashSystemPool.java               # Modelo storage pool
│   │   └── FlashSystemSnapshot.java           # Modelo snapshot
│   └── service/
│       └── FlashSystemService.java            # Lógica de negocio
├── pom.xml                                     # Dependencias Maven
└── README.md                                   # Este archivo
```

## Instalación

### Requisitos Previos
- Java 11+
- Maven 3.6+
- ZStack/ZSVirt 4.x+
- IBM FlashSystem 7300 con Storage Virtualize 8.7+
- Conectividad de red al puerto 7443 (HTTPS)

### Pasos de Instalación

1. **Compilar el plugin:**
```bash
cd /workspace/plugin/flashSystem
mvn clean package
```

2. **Copiar el JAR a ZStack:**
```bash
cp target/flashsystem-plugin-1.0.jar /usr/share/zstack/lib/
```

3. **Configurar Spring Boot:**
```yaml
# application.yml
flashsystem:
  api:
    timeout-minutes: 60
    ssl-verify: false
```

4. **Reiniciar servicios ZStack:**
```bash
systemctl restart zstack-server
systemctl restart zstack-management-node
```

## Uso de la API REST

### 1. Verificar Conexión
```bash
curl -X POST http://localhost:8080/api/flashsystem/test \
  -H "Content-Type: application/json" \
  -d '{
    "baseUrl": "https://192.168.1.100",
    "username": "admin",
    "password": "password"
  }'
```

### 2. Obtener Capacidad del Pool
```bash
curl -X GET "http://localhost:8080/api/flashsystem/capacity?baseUrl=https://192.168.1.100&username=admin&password=password&poolName=Pool_SSD"
```

### 3. Crear Volumen con Compresión
```bash
curl -X POST http://localhost:8080/api/flashsystem/volumes \
  -H "Content-Type: application/json" \
  -d '{
    "baseUrl": "https://192.168.1.100",
    "username": "admin",
    "password": "password",
    "volumeName": "vm-106-disk-0",
    "sizeGB": 100,
    "poolName": "Pool_SSD",
    "thinProvisioning": true,
    "compression": true,
    "deduplication": false
  }'
```

### 4. Crear Snapshot Safeguarded (Inmutable)
```bash
curl -X POST http://localhost:8080/api/flashsystem/snapshots \
  -H "Content-Type: application/json" \
  -d '{
    "baseUrl": "https://192.168.1.100",
    "username": "admin",
    "password": "password",
    "sourceVolumeId": "vol-12345",
    "snapshotName": "pre-upgrade-snapshot",
    "safeguarded": true
  }'
```

### 5. Registrar Host con WWPNs (Fibre Channel)
```bash
curl -X POST http://localhost:8080/api/flashsystem/hosts \
  -H "Content-Type: application/json" \
  -d '{
    "baseUrl": "https://192.168.1.100",
    "username": "admin",
    "password": "password",
    "hostname": "proxmox-node-01",
    "hostGroup": "proxmox_cluster",
    "wwpns": ["500507680B123456", "500507680B654321"]
  }'
```

### 6. Verificar Alertas del Sistema
```bash
curl -X GET "http://localhost:8080/api/flashsystem/alerts?baseUrl=https://192.168.1.100&username=admin&password=password"
```

## Comandos CLI Equivalentes (IBM Storage Virtualize)

| Operación | API REST | CLI Command |
|-----------|----------|-------------|
| Autenticar | POST /rest/v1/auth | - |
| Listar volúmenes | GET /rest/v1/lsvdisk | `lsvdisk` |
| Crear volumen | POST /rest/v1/mkvdisk | `mkvdisk -name <vol> -mdiskgrp <pool>` |
| Eliminar volumen | DELETE /rest/v1/rmvdisk/:id | `rmvdisk <vol>` |
| Crear snapshot | POST /rest/v1/mkfcmap | `mkfcmap -source <vol> -target <snap>` |
| Listar snapshots | GET /rest/v1/lsvolumesnapshot | `lsvolumesnapshot` |
| Restaurar snapshot | POST /rest/v1/restorefromsnapshot | `restorefromsnapshot <snap>` |
| Eliminar snapshot | DELETE /rest/v1/rmsnapshot/:id | `rmsnapshot <snap>` |
| Registrar host | POST /rest/v1/mkhost | `mkhost -name <host> -type open` |
| Mapear volumen | POST /rest/v1/mkvdiskhostmap | `mkvdiskhostmap -vdisk <vol> -host <host>` |
| Info pool | GET /rest/v1/lsmdiskgrp | `lsmdiskgrp <pool>` |
| Event log | GET /rest/v1/lseventlog | `lseventlog` |

## Integración con Zabbix (Opcional)

Para monitoreo avanzado, usar los siguientes endpoints:

### Discovery Rules
- **Pools**: `GET /api/lsmdiskgrp`
- **Volúmenes**: `GET /api/lsvdisk`
- **Nodos**: `GET /api/lsnode`
- **Drives**: `GET /api/lsdrive`
- **Puertos**: `GET /api/lsportfc`, `GET /api/lsportethernet`

### Métricas Clave
- **Capacidad global**: `GET /api/lssystem`
- **Endurance de drives**: `GET /api/lsdrive` → `write_endurance_used`
- **Capacidad física usada**: `GET /api/lsdrive` → `physical_used_capacity`

### Alertas
- **Eventos críticos**: `GET /api/lseventlog?severity=critical`
- **Polling recomendado**: cada 60 segundos

## Seguridad

### Autenticación
- Token-based con JWT
- Timeout configurable: 10-120 minutos
- Caché de tokens para reducir overhead

### Roles Soportados
- Monitor (solo lectura)
- Admin (lectura/escritura)
- Security Admin
- Superuser (requiere TPI para operaciones críticas)

### Two Person Integrity (TPI)
Para operaciones sensibles (eliminar snapshots safeguarded), se requiere aprobación de dos administradores.

## Solución de Problemas

### Error de Autenticación
```
Causa: Credenciales incorrectas o token expirado
Solución: Verificar usuario/password y timeout configurado
```

### Error de Conexión SSL
```
Causa: Certificado autofirmado no confiable
Solución: Configurar ssl-verify: false o importar certificado CA
```

### Volumen No Visible en Host
```
Causa: LUN no mapeado o multipath no configurado
Solución: Ejecutar `multipath -r` y verificar mapeo con `mkvdiskhostmap`
```

## Recursos Adicionales

- [IBM FlashSystem 5200 Product Guide](https://www.redbooks.ibm.com/redpapers/pdfs/redp5617.pdf)
- [IBM Storage Virtualize REST API Documentation](https://www.redbooks.ibm.com/redpapers/pdfs/redp5736.pdf)
- [Policy-Based Replication Guide](https://www.redbooks.ibm.com/redpapers/pdfs/redp5704.pdf)
- [Proxmox Storage Plugin Development](https://pve.proxmox.com/wiki/Storage_Plugin_Development)

## Autores

Basado en documentación IBM Redbooks por:
- Saloni Khandelwal (IBM Storage FlashSystem)
- Harishkumar Bhokare (IBM Storage FlashSystem)
- Dr. Pradip Waykos (IBM Storage FlashSystem)

## Licencia

Apache License 2.0

---

© 2026 IBM Corporation. IBM, FlashSystem, FlashCopy y Redbooks son marcas registradas de International Business Machines Corporation.
