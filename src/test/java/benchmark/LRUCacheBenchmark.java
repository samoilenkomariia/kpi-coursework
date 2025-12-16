package benchmark;

import com.mylrucachelib.LRUCache;
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
public class LRUCacheBenchmark {
    private LRUCache<Integer, Integer> cache;

    @Param({"100", "10000", "1000000"})
    private int capacity;

    @Setup
    public void setup() {
        cache = new LRUCache<>(capacity, 16);
        for (int i = 0; i < capacity; i++) {
            cache.put(i, i);
        }
    }

    @Benchmark
    public void testPut(Blackhole bh) {
        int id = ThreadLocalRandom.current().nextInt(capacity*2);
        cache.put(id, id);
    }

    @Benchmark
    public void testGet(Blackhole bh) {
        int id = ThreadLocalRandom.current().nextInt(capacity);
        bh.consume(cache.get(id));
    }

    public static void main(String[] args) throws Exception {
        org.openjdk.jmh.Main.main(args);
    }
}
