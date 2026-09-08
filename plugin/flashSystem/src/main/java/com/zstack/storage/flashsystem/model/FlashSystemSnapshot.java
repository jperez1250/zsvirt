package com.zstack.storage.flashsystem.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Modelo para snapshot IBM FlashSystem 7300 / Storage Virtualize 8.7 (FlashCopy)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class FlashSystemSnapshot {
    private String id;
    private String name;
    private String sourceVolumeId;
    private String sourceVolumeName;
    private long capacity;
    private String status;
    private boolean safeguarded;
    private String consistencyGroupName;
    
    @JsonProperty("id")
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    @JsonProperty("name")
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    @JsonProperty("source_volume_id")
    public String getSourceVolumeId() { return sourceVolumeId; }
    public void setSourceVolumeId(String sourceVolumeId) { this.sourceVolumeId = sourceVolumeId; }
    
    @JsonProperty("source_volume_name")
    public String getSourceVolumeName() { return sourceVolumeName; }
    public void setSourceVolumeName(String sourceVolumeName) { this.sourceVolumeName = sourceVolumeName; }
    
    @JsonProperty("capacity")
    public long getCapacity() { return capacity; }
    public void setCapacity(long capacity) { this.capacity = capacity; }
    
    @JsonProperty("status")
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    @JsonProperty("safeguarded")
    public boolean isSafeguarded() { return safeguarded; }
    public void setSafeguarded(boolean safeguarded) { this.safeguarded = safeguarded; }
    
    @JsonProperty("consistency_group_name")
    public String getConsistencyGroupName() { return consistencyGroupName; }
    public void setConsistencyGroupName(String consistencyGroupName) { this.consistencyGroupName = consistencyGroupName; }
}
