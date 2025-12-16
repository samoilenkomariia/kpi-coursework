import com.mylrucachelib.LRUCache;
import com.mylrucachelib.TimeSource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

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
        Assertions.assertTimeoutPreemptively(Duration.ofSeconds(5), () -> assertTrue(cache.checkSizeInvariance(), "Cache did not pass size invariance verification"), "The invariance check went into infinite loop which means it did not pass");
    }
}
