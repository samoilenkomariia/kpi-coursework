import net.jqwik.api.*;
import net.jqwik.api.stateful.Action;
import net.jqwik.api.stateful.ActionSequence;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class LRUCachePropertyTest {
    static class Model {
        private final int CAPACITY = 3;
        private final LRUCacheSegment<String, Integer> systemUnderTest;
        private final LinkedHashMap<String, Integer> oracle;
        public Model() {
            this.systemUnderTest = new LRUCacheSegment<>(CAPACITY);
            this.oracle = new LinkedHashMap<>(CAPACITY, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Integer> eldest) {
                    return size() > CAPACITY;
                }
            };
        }
        private void checkInvariant() {
            int expectedSize = oracle.size();
            int actualSize = systemUnderTest.size();
            assertEquals(expectedSize, actualSize, "Size mismatch expected %d, got %d".formatted(expectedSize,actualSize));
            for (Map.Entry<String, Integer> entry : oracle.entrySet()) {
                String key = entry.getKey();
                Integer expected = entry.getValue();
                Integer actual = systemUnderTest.get(key);
                assertEquals(expected, actual, "value mismatch for key %s, expected %d, but got %d".formatted(key, expected,actual));
            }
        }
        public void put(String key, Integer value) {
            systemUnderTest.put(key, value);
            oracle.put(key, value);
            checkInvariant();
        }
        public void get(String key) {
            Integer actual = oracle.get(key);
            Integer expected = oracle.get(key);
            assertEquals(expected, actual, "value mismatch for key %s, expected %d, but got %d".formatted(key, expected,actual));
            checkInvariant();
        }
    }

    @Property
    void lruCacheShouldBehaveLikeLinkedHashMap(
        @ForAll("sequences")ActionSequence<Model> sequence
    ) {
        sequence.run(new Model());
    }
    @Provide
    Arbitrary<ActionSequence<Model>> sequences() {
        return Arbitraries.sequences(
                Arbitraries.oneOf(
                        putAction(),
                        getAction()
                )
        );
    }

    private Arbitrary<Action<Model>> putAction() {
        return Arbitraries.strings().alpha().ofLength(1).flatMap( key ->
                Arbitraries.integers().between(0, 100).map(val ->
                        new Action<Model>() {
                            @Override
                            public Model run(Model model) {
                                model.put(key, val);
                                return model;
                            }
                            @Override
                            public String toString() {
                                return String.format("put(%s, %d)", key, val);
                            }
                        }
                )
        );
    }

    private Arbitrary<Action<Model>> getAction() {
        return Arbitraries.strings().alpha().ofLength(1).map(key ->
                new Action<Model>() {
                    @Override
                    public Model run(Model model) {
                        model.get(key);
                        return model;
                    }

                    @Override
                    public String toString() {
                        return "get(%s)".formatted(key);
                    }
                }
        );
    }

}
