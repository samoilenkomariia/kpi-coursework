package com.mylrucachelib;

import com.mylrucachelib.persistence.Serializer;
import com.mylrucachelib.persistence.SnapshotManager;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class LRUCache<K,V> {
    private final LRUCacheSegment<K,V>[] segments;
    private final int segmentMask;
    private final ScheduledExecutorService janitor;
    private final TimeSource clock;
    private SnapshotManager<K,V> snapshotManager;
    private Thread shutdownHook;

    public LRUCache(int capacity, int concurrencyLevel) {
        this(capacity, concurrencyLevel, System::currentTimeMillis);
    }

    public LRUCache(int capacity, int concurrencyLevel, TimeSource clock) {
        if (concurrencyLevel <= 0) {
            throw new IllegalArgumentException("Illegal initial concurrency level: " + concurrencyLevel);
        }
        if (capacity <= 0) {
            throw new IllegalArgumentException("Illegal initial capacity: " + capacity);
        }
        this.clock = clock;
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
        this.janitor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "LRU-Janitor");
            t.setDaemon(true);
            return t;
        });
        this.janitor.scheduleAtFixedRate(this::performCleanup, 1, 1, TimeUnit.SECONDS);
    }
    private void performCleanup() {
        for (var segment : segments) {
            segment.cleanupExpired(20);
        }
    }
    public void shutdown() {
        janitor.shutdown();
    }

    private void calculateCapacity(int cap, int concLvl) {
        int baseCap = cap / concLvl;
        int remainingItems = cap % concLvl;
        for (int i = 0; i < concLvl; i++) {
            if (remainingItems > 0) {
                segments[i] = new LRUCacheSegment<>(baseCap + 1, clock);
                remainingItems--;
            }
            else segments[i] = new LRUCacheSegment<>(baseCap, clock);
        }
    }

    private int getSegmentIndex(K key) {
        int hash = key == null ? 0 : key.hashCode();
        return (hash ^ (hash >>> 16)) & segmentMask;
    }

    public void put (K key, V value, long ttlMs) {
        segments[getSegmentIndex(key)].put(key, value, ttlMs);
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

    public boolean checkSizeInvariance() {
        for (var s : segments) {
            if (!s.checkSizeInvariance()) return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return Arrays.toString(segments);
    }

    public void forEach(LRUCacheSegment.EntryConsumer<K,V> action) {
        for (var segment : segments) {
            segment.forEach(action);
        }
    }
    
    public void enablePersistence(String filePath, Serializer<K> keySerializer, Serializer<V> valueSerializer) {
        this.snapshotManager = new SnapshotManager<>(filePath, keySerializer, valueSerializer, clock);
        try {
            this.snapshotManager.load(this);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load snapshot ", e);
        }
    }
    
    public void saveSnapshot() throws IOException {
        if (snapshotManager != null) {
            snapshotManager.save(this);
        }
    }
    
    public void addShutdownHook() {
        this.shutdownHook = new Thread(() -> {
            try {
                saveSnapshot();
                System.out.println("Cache snapshot saved");
            } catch (IOException e) {
                System.err.println("Failed to save snapshot on shutdown: " + e.getMessage());
            }
        });
        Runtime.getRuntime().addShutdownHook(this.shutdownHook);
    }

    public void removeShutdownHook() {
        if (this.shutdownHook != null) {
            try {
                Runtime.getRuntime().removeShutdownHook(this.shutdownHook);
            } catch (IllegalStateException ignored) {}
            this.shutdownHook = null;
        }
    }
}
