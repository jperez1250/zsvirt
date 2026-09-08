package com.zstack.storage.flashsystem.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Modelo para volumen IBM FlashSystem 7300 / Storage Virtualize 8.7
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class FlashSystemVolume {
    private String id;
    private String name;
    private long capacity;
    private String mdiskGrpName;
    private String ioGroupName;
    private boolean thin;
    private boolean compressed;
    private boolean deduplicated;
    private String status;
    private String typeName;
    private String volumeGroupId;
    private String volumeGroupName;
    
    @JsonProperty("id")
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    @JsonProperty("name")
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    @JsonProperty("capacity")
    public long getCapacity() { return capacity; }
    public void setCapacity(long capacity) { this.capacity = capacity; }
    
    @JsonProperty("mdisk_grp_name")
    public String getMdiskGrpName() { return mdiskGrpName; }
    public void setMdiskGrpName(String mdiskGrpName) { this.mdiskGrpName = mdiskGrpName; }
    
    @JsonProperty("io_group_name")
    public String getIoGroupName() { return ioGroupName; }
    public void setIoGroupName(String ioGroupName) { this.ioGroupName = ioGroupName; }
    
    @JsonProperty("thin")
    public boolean isThin() { return thin; }
    public void setThin(boolean thin) { this.thin = thin; }
    
    @JsonProperty("compressed")
    public boolean isCompressed() { return compressed; }
    public void setCompressed(boolean compressed) { this.compressed = compressed; }
    
    @JsonProperty("deduplicated")
    public boolean isDeduplicated() { return deduplicated; }
    public void setDeduplicated(boolean deduplicated) { this.deduplicated = deduplicated; }
    
    @JsonProperty("status")
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    @JsonProperty("type_name")
    public String getTypeName() { return typeName; }
    public void setTypeName(String typeName) { this.typeName = typeName; }
    
    @JsonProperty("volume_group_id")
    public String getVolumeGroupId() { return volumeGroupId; }
    public void setVolumeGroupId(String volumeGroupId) { this.volumeGroupId = volumeGroupId; }
    
    @JsonProperty("volume_group_name")
    public String getVolumeGroupName() { return volumeGroupName; }
    public void setVolumeGroupName(String volumeGroupName) { this.volumeGroupName = volumeGroupName; }
}
