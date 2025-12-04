public class LRUCache<K,V> {
    private final LRUCacheSegment<K,V>[] segments;
    private final int segmentMask;

    public LRUCache(int capacity, int concurrencyLevel) {
        if (capacity <= 0 || concurrencyLevel <= 0) {
            throw new IllegalArgumentException("Capacity and concurrency level must be positive");
        }
        int validConcurrencyLevel = 1;
        while (validConcurrencyLevel < concurrencyLevel) { // ensure cap is closest bigger power of 2
            validConcurrencyLevel *= 2;
        }
        this.segments = new LRUCacheSegment[validConcurrencyLevel];
        this.segmentMask = validConcurrencyLevel - 1;
        int segmentCap = (int) Math.ceil((double) capacity / validConcurrencyLevel);
        for (int i = 0; i  < validConcurrencyLevel; i++) {
            segments[i] = new LRUCacheSegment<>(segmentCap);
        }
    }

    private int getSegmentIndex(K key) {
        int hash = key == null ? 0 : key.hashCode();
        return (hash ^ (hash >>> 16)) & segmentMask;
    }

    public void put(K key, V value) {
        segments[getSegmentIndex(key)].put(key, value);
    }

    public V get(K key) {
        return segments[getSegmentIndex(key)].get(key);
    }

    public int size() {
        int size = 0;
        for (var s : segments) {
            size += s.size();
        }
        return size;
    }
}
