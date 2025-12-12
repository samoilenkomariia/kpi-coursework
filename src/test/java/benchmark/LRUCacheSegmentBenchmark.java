package benchmark;

import com.mylrucachelib.LRUCacheSegment;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(3)
@Threads(64)
public class LRUCacheSegmentBenchmark {
    private LRUCacheSegment<String, String> cache;

    @Param({"100", "10000", "1000000"})
    private int capacity;

    @Setup
    public void setup() {
        cache = new LRUCacheSegment<>(capacity);
        for (int i = 0; i < capacity; i++) {
            cache.put("key" + i, "value" + i);
        }
    }

    @Benchmark
    public void testPut(Blackhole bh) {
        int id = ThreadLocalRandom.current().nextInt(capacity*2);
        cache.put("key" + id, "value" + id);
    }

    @Benchmark
    public void testGet(Blackhole bh) {
        int id = ThreadLocalRandom.current().nextInt(capacity);
        bh.consume(cache.get("key" + id));
    }
}
