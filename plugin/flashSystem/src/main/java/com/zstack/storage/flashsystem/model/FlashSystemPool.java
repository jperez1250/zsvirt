package com.zstack.storage.flashsystem.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Modelo para pool de almacenamiento (mdiskgrp) IBM FlashSystem 7300 / Storage Virtualize 8.7
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class FlashSystemPool {
    private String id;
    private String name;
    private long totalCapacity;
    private long usedCapacity;
    private long freeCapacity;
    private int realSize;
    private int virtualSize;
    private String status;
    private String typeName;
    private List<String> dataRedundancy;
    
    @JsonProperty("id")
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    @JsonProperty("name")
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    @JsonProperty("total_capacity")
    public long getTotalCapacity() { return totalCapacity; }
    public void setTotalCapacity(long totalCapacity) { this.totalCapacity = totalCapacity; }
    
    @JsonProperty("used_capacity")
    public long getUsedCapacity() { return usedCapacity; }
    public void setUsedCapacity(long usedCapacity) { this.usedCapacity = usedCapacity; }
    
    @JsonProperty("free_capacity")
    public long getFreeCapacity() { return freeCapacity; }
    public void setFreeCapacity(long freeCapacity) { this.freeCapacity = freeCapacity; }
    
    @JsonProperty("real_size")
    public int getRealSize() { return realSize; }
    public void setRealSize(int realSize) { this.realSize = realSize; }
    
    @JsonProperty("virtual_size")
    public int getVirtualSize() { return virtualSize; }
    public void setVirtualSize(int virtualSize) { this.virtualSize = virtualSize; }
    
    @JsonProperty("status")
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    @JsonProperty("type_name")
    public String getTypeName() { return typeName; }
    public void setTypeName(String typeName) { this.typeName = typeName; }
    
    @JsonProperty("data_redundancy")
    public List<String> getDataRedundancy() { return dataRedundancy; }
    public void setDataRedundancy(List<String> dataRedundancy) { this.dataRedundancy = dataRedundancy; }
}
