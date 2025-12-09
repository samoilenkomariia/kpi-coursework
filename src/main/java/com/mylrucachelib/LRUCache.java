package com.mylrucachelib;

import java.util.Arrays;

public class LRUCache<K,V> implements ILRUCache<K,V> {
    private final LRUCacheSegment<K,V>[] segments;
    private final int segmentMask;

    public LRUCache(int capacity, int concurrencyLevel) {
        if (concurrencyLevel <= 0) {
            throw new IllegalArgumentException("Illegal initial concurrency level: " + concurrencyLevel);
        }
        if (capacity <= 0) {
            throw new IllegalArgumentException("Illegal initial capacity: " + capacity);
        }
        int validConcurrencyLevel = 1;
        while (validConcurrencyLevel < concurrencyLevel) {
            validConcurrencyLevel *= 2; // cap should be closest bigger power of 2
        }
        while (validConcurrencyLevel > capacity) {
            validConcurrencyLevel /= 2;
        }
        this.segments = new LRUCacheSegment[validConcurrencyLevel];
        this.segmentMask = validConcurrencyLevel - 1;

        calculateCapacity(capacity, validConcurrencyLevel);
    }

    private void calculateCapacity(int cap, int concLvl) {
        int baseCap = cap / concLvl;
        int remainingItems = cap % concLvl;
        for (int i = 0; i < concLvl; i++) {
            if (remainingItems > 0) {
                segments[i] = new LRUCacheSegment<>(baseCap + 1);
                remainingItems--;
            }
            else segments[i] = new LRUCacheSegment<>(baseCap);
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

    @Override
    public String toString() {
        return Arrays.toString(segments);
    }
}
