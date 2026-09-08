package org.zstack.storage.primary.flashsystem.model;

/**
 * Array-facing representation of a Storage Virtualize vdisk.
 *
 * This deliberately contains no ZStack inventory fields: it is the boundary
 * model used by the FlashSystem REST client.
 */
public class FlashSystemVolume {
    private String id;
    private String name;
    private String wwid;
    private long capacity;
    private String storagePool;
    private String status;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getWwid() { return wwid; }
    public void setWwid(String wwid) { this.wwid = wwid; }
    public long getCapacity() { return capacity; }
    public void setCapacity(long capacity) { this.capacity = capacity; }
    public String getStoragePool() { return storagePool; }
    public void setStoragePool(String storagePool) { this.storagePool = storagePool; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
