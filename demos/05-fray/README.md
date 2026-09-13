# 05-fray — Circle II: systematic interleaving exploration

[Fray](https://github.com/cmu-pasta/fray) (CMU PASTA Lab) takes control of the
JVM scheduler. Instead of hoping a race shows up, it *chooses* thread switch
points and replays the program many times, each run a different schedule. When
a run fails, it records the exact schedule so you can replay it under a
debugger.

## How a Fray test is written

```java
@ExtendWith(FrayTestExtension.class)
class MyTest {

    @ConcurrencyTest                       // 1000 iterations, POSScheduler by default
    void fifoOrderUnderConcurrency() throws InterruptedException {
        var buffer = createBuffer(Integer.class, 8);
        var producer = Thread.ofPlatform().name("fray-producer").start(...);
        var consumer = Thread.ofPlatform().name("fray-consumer").start(...);
        producer.join();
        consumer.join();
        assertThat(consumed).containsExactly(1, 2, 3, 4, 5);
    }
}
```

`@ConcurrencyTest` attributes (Fray 0.8.5) and their defaults:

| Attribute | Default | Meaning |
|---|---|---|
| `iterations` | `1000` | how many schedules to explore |
| `scheduler` | `POSScheduler.class` | Partial Order Sampling; also available: `PCTScheduler`, `RandomScheduler`, `FifoScheduler`, `SURWScheduler` |
| `replay` | `""` | path to a recorded schedule — replays it deterministically instead of exploring |
| `sleepAsYield` | `false` | treat `Thread.sleep` as a yield |
| `ignoreTimedBlock` | `true` | ignore timed blocking operations |

These are **per-test annotation attributes, not command-line flags.** To change
the iteration count or scheduler, edit the annotation:

```java
@ConcurrencyTest(iterations = 5000, scheduler = PCTScheduler.class)
```

## Test classes

| Class | Subject | State |
|---|---|---|
| `VolatileLamportBufferFrayTest` | `VolatileLamportBuffer` | active — SPSC tests only |
| `NonVolatileLamportBufferFrayTest` | `NonVolatileLamportBuffer` | active — **passes**, and that is the lesson |
| `LockBasedLamportBufferFrayTest` | `LockBasedLamportBuffer` | active — SPSC + MPMC tests |
| `edgecase/FastPathLamportBufferFrayTest` | `FastPathLamportBuffer` | `@Disabled` — intentionally failing demo (NPE) |
| `edgecase/ConditionalLamportBufferFrayTest` | `ConditionalLamportBuffer` | `@Disabled` — replays a recorded spurious-wakeup schedule |
| `edgecase/TocTouCompoundActionFrayTest` | client code over `LockBasedLamportBuffer` | `@Disabled` — intentionally failing TOCTOU demo |

Shared scenarios live in two abstract bases:

* `AbstractSPSCLamportBufferFrayTest` — `fifoOrderUnderConcurrency`,
  `producerConsumerCompletesWithoutLoss` (1 producer, 1 consumer)
* `AbstractMPMCLamportBufferFrayTest` (extends the SPSC base) —
  `twoConcurrentProducersMustNotLoseElements`, `twoConsumersMustNotReadSameElement`

A concrete subclass only supplies a factory:

```java
protected <T> LamportBuffer<T> createBuffer(Class<T> clazz, int capacity) {
    return LockBasedLamportBuffer.createBuffer(clazz, capacity);
}
```

The three `edgecase` tests are `@Disabled` on purpose — they are failing demos.
Remove the annotation when presenting them.

## What Fray finds — and what it cannot

**Finds:** the unlocked fast-path NPE in `FastPathLamportBuffer`, the spurious
wakeup in `ConditionalLamportBuffer` (Fray treats a spurious `Condition.await()`
return as a legal scheduling choice), and the client-side TOCTOU between
`isEmpty()` and `poll()`.

And it finds them *fast*, because it constructs the schedule rather than waiting
for one. From the recordings checked into [`../results/fray`](../results/fray):

```
FastPathLamportBufferFrayTest#twoConsumersOnSingleElementMustNotCrash
  Error found at iter: 4, step: 26, Elapsed time: 32ms
  Error: java.lang.NullPointerException

ConditionalLamportBufferFrayTest#spuriousWakeupCausesReadFromEmptyBuffer
  Error found at iter: 1, step: 1850, Elapsed time: 60ms
  Error: java.lang.AssertionError: [consumer must receive the produced value,
         not null from a spurious wakeup]
```

Iteration 4 and iteration 1 — both from ordinary tests that simply start two
threads and assert. For comparison (see [`../06-jcstress`](../06-jcstress)):
jcstress does find the fast-path NPE, at 1.1% of samples, but only once you hand
it a test built around the bug — two consumers, one pre-filled element, the NPE
caught so it registers as an outcome. The spurious wakeup has not been observed
by any jcstress test in this repository. Fray needed neither the shape of the bug
nor a guess about its outcome.

> The recording directory for the first one is still named
> `...fray.edgecase.FastTrackLamportBufferFrayTest/` — `FastTrackLamportBuffer`
> was renamed to `FastPathLamportBuffer` after that run. Recordings are keyed by
> the class name at record time, so renaming a test class invalidates any
> `replay` path pointing at its old recording.

**Cannot find:** the missing-`volatile` bug in `NonVolatileLamportBuffer`.
`NonVolatileLamportBufferFrayTest` passes every iteration. Fray enumerates
*schedules*, and every schedule it produces is sequentially consistent — it does
not model store buffers, cache coherence, or JIT reordering. Pure memory
visibility bugs belong to [`06-jcstress`](../06-jcstress).

## Run

```bash
# All active Fray tests
mvn -f demos/pom.xml -pl 05-fray -am test

# One test class
mvn -f demos/pom.xml -pl 05-fray -am test -Dtest=LockBasedLamportBufferFrayTest -Dsurefire.failIfNoSpecifiedTests=false

# One test method
mvn -f demos/pom.xml -pl 05-fray -am test -Dtest='VolatileLamportBufferFrayTest#fifoOrderUnderConcurrency' -Dsurefire.failIfNoSpecifiedTests=false

# The edge-case demos are @Disabled in the source. Remove the @Disabled
# annotation from the class you want to demonstrate, then run it:
mvn -f demos/pom.xml -pl 05-fray -am test -Dtest=FastPathLamportBufferFrayTest -Dsurefire.failIfNoSpecifiedTests=false
```

### Expect this to take a while

Fray does not run on a stock JVM. The `prepare-fray` goal (from
`fray-plugins-maven`, bound to the `initialize` phase in this module's
`pom.xml`) builds an **instrumented JDK image** under
`demos/05-fray/target/fray/fray-java` — roughly 186 MB, and several minutes on
a first run. It is cached in `target/`, so only a `clean` pays that cost again.

The tests themselves are also not fast: every `@ConcurrencyTest` replays the
body 1000 times by default, and the SPSC scenarios contain `Thread.yield()`
spin loops, which produce long schedules for the scheduler to explore. Budget
tens of minutes for the full module; use `-Dtest=` to run a single class while
iterating.

Nothing extra to install — but the module cannot be run outside Maven without
that setup.

## System properties

These are the real, supported knobs (all read by `fray-junit`):

| Property | Default | Purpose |
|---|---|---|
| `fray.workDir` | `build/fray/fray-report` | where reports and recordings are written (relative to the module dir) |
| `fray.organize.by.test` | `false` | one sub-directory per test class/method instead of one shared directory |
| `fray.debugger` | `false` | pause for a debugger to attach |

```bash
mvn -f demos/pom.xml -pl 05-fray -am test -Dfray.workDir=target/fray-report
```

### The footgun

With `fray.organize.by.test=false` (Fray's default), **every test class wipes the
shared report directory before it runs** — so only the last test's recording
survives, and a bug you just found is gone before you can replay it. This
module's `pom.xml` therefore sets it globally via Surefire:

```xml
<plugin>
    <artifactId>maven-surefire-plugin</artifactId>
    <configuration>
        <systemPropertyVariables>
            <fray.organize.by.test>true</fray.organize.by.test>
        </systemPropertyVariables>
    </configuration>
</plugin>
```

## Deterministic replay

When a test fails, Fray prints the failing iteration and the recording path:

```
[INFO]: Error found at iter: 721, step: 1850, Elapsed time: 60ms
[INFO]: Error: java.lang.AssertionError: [consumer must receive the produced value, ...]
[INFO]: The recording is saved to .../fray-report/<TestClass>/<testMethod>/recording
```

Point `replay` at that directory and the exact schedule runs again, every time —
attach a debugger and step through the switch points:

```java
@ConcurrencyTest(replay = "../results/fray/fray-report/"
    + "pl.wsztajerowski.demo.lamport.fray.edgecase.ConditionalLamportBufferFrayTest/"
    + "spuriousWakeupCausesReadFromEmptyBuffer/recording")
void spuriousWakeupCausesReadFromEmptyBuffer() { ... }
```

Recordings worth keeping have been copied out of the (git-ignored) working
directory into [`../results/fray`](../results/fray), which is why the `replay`
paths above point there.

## Reports

| Location | Contents |
|---|---|
| `demos/05-fray/build/fray/fray-report/` | live output of the last run (git-ignored) |
| `demos/results/fray/fray-report/` | curated recordings checked into the repo, used by `replay` |

Each recording directory holds `schedule.json` (the scheduling decisions) and
`random.json` (the random choices), plus a `fray.log` next to it.
