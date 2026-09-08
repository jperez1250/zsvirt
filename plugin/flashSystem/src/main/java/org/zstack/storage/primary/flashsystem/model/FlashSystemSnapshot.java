package org.zstack.storage.primary.flashsystem.model;

/** Array-facing FlashCopy snapshot identity. */
public class FlashSystemSnapshot {
    private String id;
    private String name;
    private String sourceVolumeId;
    private String status;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSourceVolumeId() { return sourceVolumeId; }
    public void setSourceVolumeId(String sourceVolumeId) { this.sourceVolumeId = sourceVolumeId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
