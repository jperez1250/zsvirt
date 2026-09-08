# Plugin IBM FlashSystem 7300 para ZSVirt

Plugin de integración de almacenamiento para IBM FlashSystem 7300 con Storage Virtualize 8.7, diseñado para ZSVirt/ZStack.

## Características Principales

### Soporte para Hardware FlashSystem 7300
- Control enclosure NVMe de 2U (Modelos 924/U7D)
- Hasta 1.5 TB de caché (768 GB por canister)
- 40 núcleos de procesamiento total
- Conectividad: FC 32Gbps, Ethernet 10/25/100Gbps, NVMe-oF
- Expansion enclosures: Modelos 12G, 24G, 92G

### Características de Storage Virtualize 8.7
- **DRAID (Distributed RAID)**: DRAID 1, DRAID 5 (RPQ), DRAID 6
- **Data Reduction**: Compresión hardware 3:1 (FCM), deduplicación 5:1 (DRP)
- **Thin/Thick Provisioning**: Aprovisionamiento flexible
- **Easy Tier**: Auto-tiering inteligente entre SCM/FCM/NVMe y HDD
- **Safeguarded Snapshots**: Instantáneas inmutables para protección contra ransomware

### Seguridad Avanzada
- Autenticación JWT con tokens renovables (10-120 minutos)
- Soporte para certificados TLS/SSL personalizados
- Two Person Integrity (TPI) para operaciones críticas
- Roles diferenciados (Monitor, Admin, Security Admin, Superuser)
- **NO usar Superuser para automatización**

### Capacidades del Plugin
- ✅ Creación automática de volúmenes con mapeo a hosts
- ✅ Eliminación segura de volúmenes con unmapping automático
- ✅ Listado de volúmenes por VM o pool completo
- ✅ Snapshots FlashCopy (estándar y Safeguarded)
- ✅ Rollback desde snapshots
- ✅ Monitoreo de capacidad en tiempo real
- ✅ Alertas de salud del sistema
- ✅ Soporte para modelos Utility (Capacity on Demand)
- ✅ Métricas de Data Reduction

## Requisitos

- Java 11+
- Maven 3.6+
- ZSVirt/ZStack 4.2.0+
- IBM FlashSystem 7300 con Storage Virtualize 8.7+
- Acceso de red al puerto REST API (7443 por defecto)

## Instalación

### 1. Compilar el plugin

```bash
cd /workspace/plugin/flashsystem
mvn clean package
```

### 2. Copiar el JAR a ZSVirt

```bash
cp target/zsvirt-flashsystem-plugin-1.0.0.jar /opt/zstack/lib/plugins/
```

### 3. Configurar el plugin

Editar `/etc/zstack/conf/flashsystem.properties`:

```properties
# Dirección IP o hostname del FlashSystem
flashsystem.management-ip=192.168.1.100

# Puerto REST API (por defecto 7443)
flashsystem.api-port=7443

# Usuario de servicio (NO usar Superuser)
flashsystem.username=zsvirt_admin
flashsystem.password=<password_encriptado>

# Pool de almacenamiento (mdiskgrp)
flashsystem.storage-pool=Pool_SSD

# Grupo de hosts para mapeo automático
flashsystem.host-group=proxmox_cluster

# Timeout de sesión en minutos (10-120)
flashsystem.session-timeout=60

# Verificación SSL (recomendado: true en producción)
flashsystem.verify-ssl=true

# Ruta al certificado CA (opcional, si verify-ssl=true)
flashsystem.ca-cert-path=/etc/ssl/certs/flashsystem-ca.pem

# Habilitar Two Person Integrity (para Safeguarded Snapshots)
flashsystem.enable-tpi=false

# Tipo de provisioning: thin o thick
flashsystem.provisioning-type=thin

# Habilitar Data Reduction (deduplicación + compresión)
flashsystem.enable-data-reduction=true

# Modelo Utility (Capacity on Demand)
flashsystem.utility-model=false
```

### 4. Reiniciar servicios ZSVirt

```bash
systemctl restart zstack-management-node
```

## Uso

### Crear volumen para VM

```bash
zsvirt-cli storage create-volume \
  --storage-id flashsystem-prod \
  --vmid 106 \
  --name vm-106-disk-0 \
  --size 100G
```

### Listar volúmenes

```bash
# Todos los volúmenes
zsvirt-cli storage list-volumes --storage-id flashsystem-prod

# Volúmenes de una VM específica
zsvirt-cli storage list-volumes --storage-id flashsystem-prod --vmid 106
```

### Eliminar volumen

```bash
zsvirt-cli storage delete-volume flashsystem-prod:vm-106-disk-0
```

### Crear snapshot

```bash
# Snapshot estándar
zsvirt-cli vm snapshot-create --vmid 106 --name pre-upgrade

# Safeguarded Snapshot (requiere TPI)
zsvirt-cli vm snapshot-create --vmid 106 --name safeguarded-snap --safeguarded
```

### Listar snapshots

```bash
zsvirt-cli vm snapshot-list --vmid 106
```

### Restaurar desde snapshot (rollback)

```bash
zsvirt-cli vm snapshot-rollback --vmid 106 --snapshot-name pre-upgrade
```

### Eliminar snapshot

```bash
zsvirt-cli vm snapshot-delete --vmid 106 --snapshot-name pre-upgrade
```

### Ver estado del almacenamiento

```bash
# Capacidad del pool
zsvirt-cli storage status flashsystem-prod

# Alertas activas
zsvirt-cli storage alerts flashsystem-prod
```

## Arquitectura

### Path de Control (Management)
1. Administrador inicia operación desde UI/API de ZSVirt
2. ZSVirt valida y enruta al plugin FlashSystem
3. Plugin autentica con FlashSystem vía REST API (JWT)
4. Ejecuta comando específico (mkvdisk, rmvdisk, etc.)
5. Retorna resultado a ZSVirt

### Path de Datos (Data)
1. Volúmenes expuestos vía Fibre Channel o iSCSI
2. Multipath device mapper en nodos ZSVirt
3. Dispositivos bloque disponibles como `/dev/mapper/mpath-*`
4. VMs acceden directamente sin intermediarios

## APIs REST Soportadas

| Operación | Endpoint REST | Comando CLI equivalente |
|-----------|--------------|------------------------|
| Autenticación | POST /rest/v1/auth | - |
| Crear volumen | POST /rest/v1/mkvdisk | svctask mkvdisk |
| Eliminar volumen | POST /rest/v1/rmvdisk | svctask rmvdisk |
| Listar volúmenes | POST /rest/v1/lsvdisk | svcinfo lsvdisk |
| Mapear volumen | POST /rest/v1/mkvdiskhostmap | svctask mkvdiskhostmap |
| Desmappar volumen | POST /rest/v1/rmvdiskhostmap | svctask rmvdiskhostmap |
| Crear snapshot | POST /rest/v1/mkvdiskcopy | svctask mkvdiskcopy |
| Eliminar snapshot | POST /rest/v1/rmvdisk | svctask rmvdisk |
| Restaurar snapshot | POST /rest/v1/restorevdiskcopy | svctask restorevdiskcopy |
| Info pool | POST /rest/v1/lsmdiskgrp | svcinfo lsmdiskgrp |
| Estado sistema | POST /rest/v1/lssystem | svcinfo lssystem |
| Alertas | POST /rest/v1/lseventlog | svcinfo lseventlog |

## Manejo de Errores

El plugin interpreta códigos de error CMMVC de Storage Virtualize:

- **CMMVC5753E**: Volumen no existe
- **CMMVC6017E**: Volumen ya está mapeado
- **CMMVC8710E**: Token expirado (manejado automáticamente)
- **CMMVC9351E**: Requiere Two Person Integrity

## Mejores Prácticas

1. **Seguridad**: Nunca usar Superuser para automatización
2. **Certificados**: Usar CA corporativa en producción
3. **TPI**: Habilitar para proteger Safeguarded Snapshots
4. **Monitoreo**: Configurar alertas proactivas
5. **Backup**: Mantener backups fuera del array principal
6. **Capacity Planning**: Monitorear uso de pool regularmente

## Soporte para Modelos Utility

Para sistemas FlashSystem 7300 modelo U7D (Utility):

- Facturación basada en capacidad promedio mensual
- Base subscription: 30-40% sin costo extra
- El plugin reporta capacidad provisionada vs utilizada
- Integración con IBM Storage Insights para telemetría

## Integración con IBM Storage Insights

El plugin complementa (no reemplaza) IBM Storage Insights:

- Storage Insights: Monitoreo global, predictivo, Call Home
- Plugin: Gestión operativa desde ZSVirt

Recomendado usar ambos para visibilidad completa.

## Limitaciones Conocidas

- No soporta replicación Metro/Global Mirror (configurar directamente en FlashSystem)
- Policy-Based Replication debe configurarse vía GUI/CLI de FlashSystem
- Máximo 32 PB de capacidad virtualizada por cluster

## Changelog

### v1.0.0
- Implementación inicial
- Soporte completo para FlashSystem 7300 + SV 8.7
- Autenticación JWT con refresh automático
- Gestión de volúmenes y snapshots
- Safeguarded Snapshots con TPI
- Monitoreo de capacidad y alertas
- Soporte SSL/TLS personalizado

## Licencia

Apache License 2.0

## Autores

Desarrollado para integración ZSVirt con IBM FlashSystem

## Recursos Adicionales

- [IBM FlashSystem 7300 Product Guide](https://www.redbooks.ibm.com/redpapers/pdfs/redp5617.pdf)
- [Storage Virtualize REST API Documentation](https://www.redbooks.ibm.com/redpapers/pdfs/redp5736.pdf)
- [ZSVirt Plugin Development Guide](https://zsvirt.io/docs/plugins)
