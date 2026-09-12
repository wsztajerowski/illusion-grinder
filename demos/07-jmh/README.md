# 07-jmh — Circle IV: what correctness costs

[JMH](https://github.com/openjdk/jmh) (OpenJDK) measures throughput properly:
forked JVMs, warm-up iterations, dead-code elimination defences (`Blackhole`),
and coordinated producer/consumer groups (`Control`).

The rule this module illustrates: **only benchmark code you have already proven
correct.** Accurate numbers for a broken implementation are worse than no
numbers — they make the wrong choice look attractive.

## Benchmarks

| Class | Topology | Subject |
|---|---|---|
| `VolatileLamportBufferBenchmark` | 1 producer + 1 consumer (`@Group("spsc")`) | `VolatileLamportBuffer` at its design point |
| `LockBasedLamportBufferBenchmark` | 2 producers + 2 consumers (`@Group("mpmc")`) | `LockBasedLamportBuffer` at its design point |
| `ApplesToApplesLamportBufferBenchmark` | 1 producer + 1 consumer, `@Param({"VOLATILE","LOCK"})` | both implementations under an identical workload |

All three run `Mode.Throughput` in ops/s, `@Fork(1)`, 5×1s warm-up and
5×1s measurement iterations, with `@Param({"64","1024"})` for capacity.

The apples-to-apples comparison is workload-equal (same topology, same
capacity), but the implementations have different design targets — `VOLATILE`
is SPSC-only, `LOCK` is MPMC-capable. Read the numbers in that context: this is
"what does the lock cost when you don't need it", not "lock-free is always
faster".

## Reference results

From [`../results/jmh/jmh-output.json`](../results/jmh/jmh-output.json)
(1 producer + 1 consumer, aggregate throughput):

| Capacity | VOLATILE | LOCK | Ratio |
|---|---|---|---|
| 64 | 16,814,467 ops/s | 4,277,676 ops/s | 3.9× |
| 1024 | 18,750,040 ops/s | 5,942,622 ops/s | 3.2× |

Roughly **3× higher throughput** for the lock-free variant on an SPSC
workload — which only matters because [`05-fray`](../05-fray) and
[`06-jcstress`](../06-jcstress) established it is actually correct there.

## Build and run

```bash
# Build the shaded benchmark runner
mvn -f demos/pom.xml -pl 07-jmh -am package

# Run all benchmarks (full protocol — expect ~10 minutes)
java -jar demos/07-jmh/target/benchmarks.jar

# Run one benchmark class
java -jar demos/07-jmh/target/benchmarks.jar ".*VolatileLamportBufferBenchmark.*"
java -jar demos/07-jmh/target/benchmarks.jar ".*LockBasedLamportBufferBenchmark.*"

# Run the apples-to-apples comparison only
java -jar demos/07-jmh/target/benchmarks.jar ".*ApplesToApplesLamportBufferBenchmark.*"

# Quick sanity check with the GC profiler (not publication-quality numbers)
java -jar demos/07-jmh/target/benchmarks.jar ".*ApplesToApples.*" -wi 3 -i 3 -f 1 -prof gc

# Pin a single parameter combination
java -jar demos/07-jmh/target/benchmarks.jar ".*ApplesToApples.*" -p capacity=1024 -p implementation=LOCK

# Export results for later comparison
java -jar demos/07-jmh/target/benchmarks.jar -rf json -rff target/jmh-output.json
java -jar demos/07-jmh/target/benchmarks.jar -rf csv  -rff target/jmh-output.csv

# List everything the jar contains
java -jar demos/07-jmh/target/benchmarks.jar -l
```

Useful flags: `-wi` (warm-up iterations), `-i` (measurement iterations),
`-f` (forks), `-t` (threads), `-p key=value` (parameter override),
`-prof <gc|stack|perf|...>` (profiler), `-rf`/`-rff` (result format and file),
`-l`/`-lp` (list benchmarks / list with parameters).

Benchmarking notes: close other applications, disable turbo/thermal-throttling
noise where you can, and never compare numbers across machines. The JAR is
self-contained (`maven-shade-plugin`, main class `org.openjdk.jmh.Main`), so it
can be copied to a dedicated benchmarking box and run there.
