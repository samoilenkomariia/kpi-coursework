package benchmark;

import com.mylrucachelib.LRUCache;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@BenchmarkMode({Mode.Throughput, Mode.SampleTime}) // SampleTime captures p99 latency
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(2)
@Threads(32)
public class LRUCacheHotSpotBenchmark {
    private LRUCache<Integer, Integer> cache;

    @Param({"100000"}) // 100k items
    private int capacity;

    private final double hotTrafficFraction = 0.8;
    private int hotKeyRange;

    @Setup
    public void setup() {
        cache = new LRUCache<>(capacity, 16);
        for (int i = 0; i < capacity; i++) {
            cache.put(i, i);
        }
        // first 20% of keys are HOT
        this.hotKeyRange = (int) (capacity * 0.2);
    }

    @Benchmark
    public void testHotSpotWorkload(Blackhole bh) {
        int key;
        double random = ThreadLocalRandom.current().nextDouble();

        // determine key selection: Zipfian-like approximation
        if (random < hotTrafficFraction) {
            // 80% chance pick from the hot range
            key = ThreadLocalRandom.current().nextInt(hotKeyRange);
        } else {
            key = hotKeyRange + ThreadLocalRandom.current().nextInt(capacity - hotKeyRange);
        }

        if (ThreadLocalRandom.current().nextDouble() < 0.1) {
            cache.put(key, key);
        } else {
            bh.consume(cache.get(key));
        }
    }
}
