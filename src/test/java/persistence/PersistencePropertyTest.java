package persistence;

import com.mylrucachelib.LRUCache;
import com.mylrucachelib.persistence.StringSerializer;
import net.jqwik.api.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
public class PersistencePropertyTest {

    @Property
    void anyStringMapCanBePersisted(@ForAll("stringMaps") Map<String, String> data) throws IOException {
        Path tempDir = Files.createTempDirectory("jqwik_test");
        String path = tempDir.resolve("prop_test_" + System.nanoTime() + ".dump").toString();

        try {
            LRUCache<String, String> original = new LRUCache<>(data.size() + 10, 1);
            original.enablePersistence(path, new StringSerializer(), new StringSerializer());
            data.forEach(original::put);
            original.saveSnapshot();
            LRUCache<String, String> restored = new LRUCache<>(data.size() + 10, 1);
            restored.enablePersistence(path, new StringSerializer(), new StringSerializer());

            assertEquals(original.size(), restored.size());
            data.forEach((k, v) -> {
                assertEquals(v, restored.get(k), "Value mismatch for key: " + k);
            });
        } finally {
            // FIX: Cleanup manually to prevent disk clutter
            deleteDirectoryRecursively(tempDir);
        }
    }

    private void deleteDirectoryRecursively(Path path) {
        try (Stream<Path> walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try { Files.delete(p); } catch (IOException ignored) {}
                    });
        } catch (IOException ignored) {}
    }

    @Provide
    Arbitrary<Map<String, String>> stringMaps() {
        // generate maps with Unicode strings, empty strings, long strings
        return Arbitraries.maps(
                Arbitraries.strings().ofMinLength(1).ofMaxLength(50), // keys
                Arbitraries.strings().all().ofMaxLength(100) // values (any unicode)
        ).ofMinSize(0).ofMaxSize(100);
    }
}
