package persistence;

import com.mylrucachelib.LRUCache;
import com.mylrucachelib.TimeSource;
import com.mylrucachelib.persistence.StringSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

public class PersistenceTest {
    @TempDir
    Path tempDir;

    @Test
    void testSaveAndLoad() throws IOException {
        File dumpFile = tempDir.resolve("test.dump").toFile();
        String path = dumpFile.getAbsolutePath();

        LRUCache<String, String> cache = new LRUCache<>(10, 2);
        cache.enablePersistence(path, new StringSerializer(), new StringSerializer());
        cache.put("key1", "value1");
        cache.put("key2", "value2");
        cache.saveSnapshot();

        LRUCache<String, String> newCache = new LRUCache<>(10, 2);
        newCache.enablePersistence(path, new StringSerializer(), new StringSerializer());

        assertEquals(2, newCache.size());
        assertEquals("value1", cache.get("key1"));
        assertEquals("value2", cache.get("key2"));
    }

    @Test
    void testExpiredItemsNotLoading() throws IOException {
        File dumpFile = tempDir.resolve("expiry.dump").toFile();
        AtomicLong fakeTime = new AtomicLong(1000);
        TimeSource clock = fakeTime::get;

        LRUCache<String,String> cache = new LRUCache<>(10, 2, clock);
        cache.enablePersistence(dumpFile.getAbsolutePath(), new StringSerializer(), new StringSerializer());
        cache.put("1000", "val2", 1000);
        cache.put("100", "val2", 100);
        cache.saveSnapshot();

        fakeTime.set(1500);
        LRUCache<String,String> newCache = new LRUCache<>(10, 2, clock);
        newCache.enablePersistence(dumpFile.getAbsolutePath(), new StringSerializer(), new StringSerializer());
        assertNull(newCache.get("100"), "Expired item is not supposed to be loaded");
        assertEquals("val2", newCache.get("1000"));
    }

    @Test
    void testCorruptedFileThrowsException() throws IOException {
        File dumpFile = tempDir.resolve("corrupted.dump").toFile();
        LRUCache<String,String> cache = new LRUCache<>(10, 2);
        cache.enablePersistence(dumpFile.getAbsolutePath(), new StringSerializer(), new StringSerializer());
        for (int i = 0; i < 10; i++) {
            cache.put("key" + i, "value" + i);
        }
        cache.saveSnapshot();

        try (RandomAccessFile raf = new RandomAccessFile(dumpFile, "rw")) {
            raf.seek(dumpFile.length()-10);
            raf.write(0xFF); // -1
        }
        LRUCache<String, String> newCache = new LRUCache<>(10, 2);
        assertThrows(UncheckedIOException.class, () -> {
            newCache.enablePersistence(dumpFile.getAbsolutePath(), new StringSerializer(), new StringSerializer());
        });
    }

    @Test
    void testEmptyCachePersistence() throws IOException {
        String path = tempDir.resolve("empty.dump").toString();
        LRUCache<String, String> cache = new LRUCache<>(10, 1);
        cache.enablePersistence(path, new StringSerializer(), new StringSerializer());
        cache.saveSnapshot();
        LRUCache<String, String> newCache = new LRUCache<>(10, 1);
        newCache.enablePersistence(path, new StringSerializer(), new StringSerializer());

        assertEquals(0, newCache.size());
    }
}
