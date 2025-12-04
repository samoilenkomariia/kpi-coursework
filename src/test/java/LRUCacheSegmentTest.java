import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class LRUCacheSegmentTest {

    @Test
    void testZeroCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new LRUCacheSegment<>(0));
    }

    @Test
    void testPut_Size() {
        LRUCacheSegment<Character,Character> cache = new LRUCacheSegment<>(3);
        for (char a = 'A'; a < 'F';  a++) {
            cache.put(a, a);
        }
        System.out.println("testing size " + cache);
        int s = cache.size();
        assertEquals(3, s, "expected %d, got %d".formatted(3, s));
    }

    @Test
    void testPut_Eviction() {
        LRUCacheSegment<Character,Character> cache = new LRUCacheSegment<>(3);
        for (char a = 'A'; a < 'F';  a++) {
            cache.put(a, a);
        }
        System.out.println("testing eviction " + cache);
        assertNull(cache.get('A'), "key A should've been evicted");
        assertNull(cache.get('B'), "key B should've been evicted");
    }

    @Test
    void testOneCapacity() {
        LRUCacheSegment<Character,Character> cache = new LRUCacheSegment<>(1);
        for (char a = 'A'; a <= 'G'; a++) {
            cache.put(a, a);
        }
        System.out.println("testing capacity=1 " + cache);
        int size = cache.size();
        assertEquals(1, size, "expected %d, got %d".formatted(1, size));
        assertEquals('G', cache.get('G'), "expected 'G, got %s".formatted('G'));
    }
}
