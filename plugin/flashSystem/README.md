# IBM FlashSystem Primary Storage Plugin for ZStack/ZSVirt

## Overview

This plugin integrates IBM Storage FlashSystem with ZStack/ZSVirt virtualization platform, enabling automated storage provisioning, host mapping, and lifecycle management through the IBM Storage Virtualize REST API.

Based on the architecture described in the IBM Redpaper "Integrate Proxmox Virtual Environment with IBM Storage FlashSystem", adapted for ZStack's architecture.

## Architecture

### Control Path (Management)
- ZStack communicates with FlashSystem via REST API (port 7443)
- Volume creation, deletion, snapshots managed through FlashSystem API
- Host registration and mapping automated via host groups

### Data Path (I/O)
- Direct Fibre Channel (FC) or iSCSI connectivity between ZStack hosts and FlashSystem
- Multipath device mapper provides redundancy and uniform device naming
- Block devices exposed as `/dev/mapper/mpath-<WWID>`

## Features

- **Automated Volume Provisioning**: Create volumes directly from ZStack UI/API
- **Thin Provisioning**: Leverage FlashSystem thin provisioning capabilities  
- **Snapshot Support**: Integration with IBM FlashCopy for fast snapshots
- **Host Group Mapping**: Automatic volume mapping to host groups
- **Capacity Reporting**: Real-time capacity information from FlashSystem pools
- **Shared Storage**: Support for VM live migration on shared FlashSystem storage

## Configuration

### Adding FlashSystem Storage

```bash
# Via ZStack CLI
zstack-cli AddPrimaryStorage \
  name=flashsystem-prod \
  zoneUuid=<zone-uuid> \
  url=FlashSystem:/// \
  type=FlashSystem \
  managementIp=192.168.1.100 \
  username=admin \
  password=<password> \
  storagePool=Pool_SSD \
  hostGroup=proxmox_cluster \
  protocol=iSCSI
```

### Configuration Parameters

| Parameter | Description | Required |
|-----------|-------------|----------|
| `managementIp` | FlashSystem management IP or hostname | Yes |
| `username` | REST API username | Yes |
| `password` | REST API password (encrypted) | Yes |
| `storagePool` | FlashSystem mdiskgrp name | Yes |
| `hostGroup` | Host group for auto-mapping | No |
| `protocol` | iSCSI or FC (default: iSCSI) | No |
| `iqnTarget` | iSCSI target IQN (for iSCSI) | No |
| `restApiPort` | REST API port (default: 7443) | No |

## REST API Endpoints Used

### Authentication
- `POST /rest/v1/auth` - Obtain JWT token

### Volume Management
- `POST /rest/v1/mkvdisk` - Create volume
- `DELETE /rest/v1/rmvdisk/{name}` - Delete volume
- `GET /rest/v1/lsvdisk/{name}` - List volume details

### Snapshot Management  
- `POST /rest/v1/mkvolumesnapshot` - Create snapshot
- `DELETE /rest/v1/rmsnapshot/{name}` - Delete snapshot
- `GET /rest/v1/lsvolumesnapshot` - List snapshots
- `POST /rest/v1/restorefromsnapshot` - Restore from snapshot

### Host Management
- `POST /rest/v1/addhostmdiskgrpmapping` - Map volume to host group
- `POST /rest/v1/rmhostmdiskgrpmapping` - Unmap volume from host group

### Capacity Reporting
- `GET /rest/v1/lsmdiskgrp/{pool}` - Get pool capacity

## Installation

### Prerequisites

1. ZStack/ZSVirt 5.0.0 or later
2. IBM FlashSystem with Storage Virtualize software
3. Network connectivity between ZStack management node and FlashSystem
4. FC or iSCSI connectivity between ZStack hosts and FlashSystem
5. Multipath configured on all KVM hosts

### Build

```bash
cd /workspace/plugin/flashSystem
mvn clean package
```

### Deploy

1. Copy the JAR to ZStack plugins directory:
```bash
cp target/flashSystem-5.0.0.jar /usr/share/zstack/zstack-server/webapps/zstack/WEB-INF/lib/
```

2. Restart ZStack services:
```bash
systemctl restart zstack-server
```

3. Verify plugin loaded:
```bash
zstack-cli QueryPrimaryStorageType names=FlashSystem
```

## Usage Examples

### Create Volume for VM

```bash
# Create a 100GB volume for VM 106
zstack-cli CreateDataVolume \
  name=vm-106-disk-0 \
  primaryStorageUuid=<flashsystem-storage-uuid> \
  size=107374182400 \
  vmInstanceUuid=<vm-uuid>
```

### Create Snapshot

```bash
# Create snapshot before upgrade
zstack-cli CreateVolumeSnapshot \
  name=pre-upgrade \
  volumeUuid=<volume-uuid>
```

### Restore from Snapshot

```bash
# Rollback to pre-upgrade state
zstack-cli RecoverVolumeFromSnapshot \
  snapshotUuid=<snapshot-uuid>
```

## Troubleshooting

### Common Issues

1. **Authentication Failed**
   - Verify username/password
   - Check network connectivity to FlashSystem management IP
   - Ensure SSL certificates are trusted (self-signed certs accepted by default)

2. **Volume Creation Failed**
   - Verify storage pool exists and has capacity
   - Check host group configuration
   - Review FlashSystem API logs

3. **Multipath Device Not Found**
   - Ensure multipathd service is running on KVM hosts
   - Verify FC/iSCSI connectivity
   - Check zoning and LUN masking on storage array

### Logs

Plugin logs: `/var/log/zstack/zstack-server.log`

Search for: `FlashSystem`, `FlashSystemApiClient`, `FlashSystemPrimaryStorage`

## Advanced Configuration

### Policy-Based Replication

Configure PBR on FlashSystem for disaster recovery:

```bash
# On FlashSystem CLI
mkvolumegroup proxmox_vg
chvolume -volumegroup proxmox_vg vol_<uuid>
# Configure replication policy via FlashSystem GUI or CLI
```

Note: PBR configuration must be done on FlashSystem, not through ZStack.

### Performance Tuning

- Enable write-back cache on FlashSystem volumes
- Use dedicated management network for REST API traffic
- Configure appropriate queue depths on KVM hosts
- Monitor FlashSystem performance metrics

## Security Considerations

- Passwords are encrypted using ZStack's crypto facade
- REST API uses HTTPS with token-based authentication
- Tokens are cached and refreshed automatically
- Self-signed SSL certificates are accepted (configure proper certs for production)

## Limitations

- Template download not supported (volumes created directly on FlashSystem)
- Volume migration between different storage types requires copy
- Some advanced FlashSystem features require manual configuration on array

## Support

For issues related to this plugin:
1. Check ZStack server logs
2. Verify FlashSystem REST API accessibility
3. Review multipath configuration on KVM hosts
4. Contact ZStack support or IBM Storage support as appropriate

## References

- [IBM Storage FlashSystem Documentation](https://www.ibm.com/support/pages/flashsystem)
- [IBM Storage Virtualize REST API Guide](https://www.ibm.com/docs/en/storage-virtualize)
- [ZStack Plugin Development Guide](https://www.zstack.io/)
- IBM Redpaper: "Integrate Proxmox VE with IBM Storage FlashSystem"

## Version History

- **1.0.0**: Initial release
  - Basic volume lifecycle management
  - iSCSI and FC protocol support
  - Snapshot integration via FlashCopy
  - Host group auto-mapping
  - Capacity reporting
