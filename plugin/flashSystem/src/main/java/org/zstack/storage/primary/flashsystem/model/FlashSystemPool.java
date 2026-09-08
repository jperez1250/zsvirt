package org.zstack.storage.primary.flashsystem.model;

/** Array-facing storage-pool capacity and state. */
public class FlashSystemPool {
    private String id;
    private String name;
    private long totalCapacity;
    private long usedCapacity;
    private long freeCapacity;
    private String status;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public long getTotalCapacity() { return totalCapacity; }
    public void setTotalCapacity(long totalCapacity) { this.totalCapacity = totalCapacity; }
    public long getUsedCapacity() { return usedCapacity; }
    public void setUsedCapacity(long usedCapacity) { this.usedCapacity = usedCapacity; }
    public long getFreeCapacity() { return freeCapacity; }
    public void setFreeCapacity(long freeCapacity) { this.freeCapacity = freeCapacity; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
