import com.mylrucachelib.ILRUCache;
import com.mylrucachelib.LRUCache;
import com.mylrucachelib.LRUCacheSegment;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

public class LRUCacheTest {

    @Test
    void testZeroCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new LRUCache<>(0, 10));
    }

    @Test
    void testZeroConcurrentLevel() {
        assertThrows(IllegalArgumentException.class, () -> new LRUCache<>(10, 0));
    }

    @Test
    void testBasicPutAndGet() {
        LRUCache<Character,Character> cache = new LRUCache<>(5, 3);
        for (char c = 'A'; c <= 'Z'; c++) {
            cache.put(c, c);
        }
        int size = cache.size();
        assertEquals(5, size, "expected size %d, got %d".formatted(5, size));
        System.out.println("testing basic put and get: " + cache);
        char mru = cache.get('Z');
        assertEquals('Z', mru, "expected MRU element to be Z, but got %s".formatted(mru));
    }

    @Test
    void testConcLvlBiggerThanCapacity() {
        LRUCache<Character,Character> cache = new LRUCache<>(5, 16);
        for (char c = 'A'; c <= 'Z'; c++) {
            cache.put(c, c);
        }
        System.out.println("testing concurrency lvl > capacity: " + cache);
        assertEquals(5, cache.size(), "expected size 5, got %d".formatted( cache.size()));
    }

    @Test
    void testConcLvlEqualCapacity() {
        LRUCache<Character,Character> cache = new LRUCache<>(5, 5);
        for (char c = 'A'; c <= 'Z'; c++) {
            cache.put(c, c);
        }
        System.out.print("testing concurrency lvl == capacity " + cache);
        assertEquals(5, cache.size(), "expected size 5, got %d".formatted( cache.size()));

    }

    @Test
    void testConcurrentCapacityInvariance() throws InterruptedException {
        int capacity = 100;
        LRUCache<String,Integer> cache = new LRUCache<>(capacity, 16);
        int threads = 50;
        int operationsPerThread = 10000;
        ExecutorService service = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicBoolean failed = new AtomicBoolean(false);

        for (int i = 0; i < threads; i++) {
            final int id = i;
            service.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        String key = "key-" + id;
                        cache.put(key, j);
                        if (j % 2 == 0) cache.get(key);
                    }
                } catch (Exception e) {
                    failed.set(true);
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        service.shutdown();
        assertFalse(failed.get(), "Exception(s) occurred during thread execution");
        int actualSize = cache.size();
        assertTrue(actualSize <= capacity, "Cache size must not exceed set capacity %d, actual %d".formatted(capacity, actualSize));
    }

    private long runBenchmark(ILRUCache<Integer,Integer> cache, int nThreads, int nOperations) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(nThreads);
        int opPerThread = nOperations / nThreads;
        CountDownLatch latch = new CountDownLatch(nThreads);
        long start = System.currentTimeMillis();
        for (int i = 0; i < nThreads; i++) {
            pool.submit(() -> {
                try {
                    for(int j = 0; j < opPerThread; j++) {
                        cache.put(j, j);
                        cache.get(j);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        long end = System.currentTimeMillis();
        pool.shutdown();
        return end - start;
    }

    @Test
    void benchmarkShardingVsSingleLock() throws InterruptedException {
        int operations = 20000000;
        int threads = 100;

        System.out.println("Benchmark sharding vs single lock");
        for (int i = 0; i < 5; i++) {
            LRUCacheSegment<Integer, Integer> singleLockCache = new LRUCacheSegment<>(1000);
            long timeSingle = runBenchmark(singleLockCache, threads, operations);
            System.out.println("Single lock Cache time(ms): " + timeSingle);

            LRUCache<Integer, Integer> shardedCache = new LRUCache<>(1000, 512);
            long timeSharded = runBenchmark(shardedCache, threads, operations);
            System.out.println("Sharded Cache time(ms): " + timeSharded);
            System.out.printf("Speedup: %.2fx%n", (double) timeSingle / timeSharded);
        }
    }
}
