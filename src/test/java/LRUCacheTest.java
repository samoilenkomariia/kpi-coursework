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
        for (char c = 'A'; c <= 'P'; c++) {
            cache.put(c, c);
        }
        int size = cache.size();
        assertEquals(5, size, "expected size %d, got %d".formatted(5, size));
        System.out.println("testing basic put and get: " + cache);
        for (char c = 'L'; c <= 'P'; c++) {
            char val = cache.get(c);
            assertEquals(c, val, "expected %s, got %s".formatted(c, val));
        }
    }
}
