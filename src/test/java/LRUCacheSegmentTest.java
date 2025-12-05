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
    void testGetNonExistentKey() {
        LRUCacheSegment<Character,Character> cache = new LRUCacheSegment<>(3);
        assertNull(cache.get('A'));
        for (char i = 'B'; i <= 'F'; i++) {
            cache.put(i, i);
        }
        assertNull(cache.get('A'));
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

    @Test
    void testGetPromotionToMRU() {
        LRUCacheSegment<Character,Character> cache = new LRUCacheSegment<>(3);
        for (char a = 'A'; a <= 'C'; a++) {
            cache.put(a, a);
        }
        var mru = cache.get('A');
        cache.put('D', 'D');
        assertNotNull(cache.get(mru), "MRU element is expected to be present after eviction");
        assertNull(cache.get('B'), "LRU element should've been evicted, but was found");
    }

    @Test
    void testPutExistingKey() {
        LRUCacheSegment<Character,Character> cache = new LRUCacheSegment<>(3);
        for (char a = 'A'; a <= 'C'; a++) {
            cache.put(a, a);
        }
        cache.put('A', 'Z');
        char actual = cache.get('A');
        assertEquals('Z', actual, "expected Z, got %s".formatted(actual));
    }
}
