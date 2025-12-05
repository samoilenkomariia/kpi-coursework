import org.junit.jupiter.api.Test;
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
}
