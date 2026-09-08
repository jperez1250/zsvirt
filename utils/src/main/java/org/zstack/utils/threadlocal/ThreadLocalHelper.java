package org.zstack.utils.threadlocal;

import java.util.HashMap;
import java.util.Map;

public class ThreadLocalHelper {
    private static final ThreadLocal<Map<String, Object>> _local = ThreadLocal.withInitial(() -> new HashMap<>(4));
    
    public static <T> void set(String key, T value) {
       Map<String, Object> map = _local.get();
       map.put(key, value);
    }
    
    @SuppressWarnings("unchecked")
    public static <T> T get(String key) {
       Map<String, Object> map = _local.get();
       return (T) map.get(key);
    }
    
    /**
     * Remove the value for the given key from the current thread's map.
     * Returns the removed value, or null if no value was present.
     */
    public static <T> T remove(String key) {
        Map<String, Object> map = _local.get();
        if (map != null) {
            @SuppressWarnings("unchecked")
            T value = (T) map.remove(key);
            return value;
        }
        return null;
    }
    
    /**
     * Clear all entries from the current thread's map.
     * Call this method when the thread is done to prevent memory leaks.
     */
    public static void clear() {
        _local.remove();
    }
}
