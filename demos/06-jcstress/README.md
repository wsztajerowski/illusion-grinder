# 06-jcstress — Circle III: the actual hardware

[jcstress](https://openjdk.org/projects/code-tools/jcstress/) (OpenJDK) is the
Java Concurrency Stress test harness. It runs a tiny piece of code billions of
times on real CPUs, in tight loops designed to defeat the JIT's ability to hide
reordering, and tabulates every observed outcome against what you declared
legal.

Where [Fray](../05-fray) asks *"does any schedule break it?"*, jcstress asks
*"does the CPU, JVM and JIT break it?"* — and that is a different question.

## How a jcstress test is written

```java
@JCStressTest
@Outcome(id = "1, 1, 0", expect = ACCEPTABLE,  desc = "Producer offers, consumer polls it")
@Outcome(id = "1, 0, 1", expect = ACCEPTABLE,  desc = "Consumer polls before producer offers")
@Outcome(id = "1, 0, 0", expect = FORBIDDEN,   desc = "Element vanished — visibility bug")
@State
public class VolatileSpscLamportBufferJcstressTest {

    private final LamportBuffer<Integer> queue = VolatileLamportBuffer.createBuffer(Integer.class, 2);

    @Actor   public void producer(III_Result r) { r.r1 = queue.offer(1) ? 1 : 0; }
    @Actor   public void consumer(III_Result r) { r.r2 = queue.poll().orElse(0); }
    @Arbiter public void arbiter (III_Result r) { r.r3 = queue.poll().orElse(0); }
}
```

* `@State` — the object under test, freshly allocated per invocation.
* `@Actor` — a method run concurrently on its own thread, exactly once per state.
* `@Arbiter` — runs after all actors have finished; observes the final state.
* `@Outcome` — a result tuple with a verdict: `ACCEPTABLE`,
  `ACCEPTABLE_INTERESTING` (legal but noteworthy — reported separately),
  `FORBIDDEN` (fails the run), or `UNKNOWN`.

## Tests in this module

| Class | Subject | Looking for |
|---|---|---|
| `VolatileSpscLamportBufferJcstressTest` | `VolatileLamportBuffer` | SPSC baseline — must never lose an element |
| `TwoProducersVolatileBufferStress` | `VolatileLamportBuffer` | TOCTOU lost update when an SPSC buffer gets two producers |
| `SingleThreadLamportBufferMultiOpStress` | `NonVolatileLamportBuffer` | duplicate reads, lost writes, FIFO violations from missing `volatile` |
| `NonVolatileSpscLamportBufferJcstressTest` | `NonVolatileLamportBuffer` | the headline visibility bug — **currently disabled** (`@JCStressTest` commented out) because it fails by design |
| `LockBasedLamportBufferOfferPollStress` | `LockBasedLamportBuffer` | control sample — should be clean |
| `FastPathLamportBufferStress` | `FastPathLamportBuffer` | the unlocked fast path under real contention |
| `SpuriousWakeupLamportBufferStress` | `ConditionalLamportBuffer` | `if`-instead-of-`while` around `Condition.await()` |

Six tests run by default.

## What a full run actually reports

From a complete `mvn -pl 06-jcstress -am verify` on this repository (macOS,
x86_64, JDK 25):

| Test | Verdicts observed |
|---|---|
| `TwoProducersVolatileBufferStress` | **Interesting** — lost update in 2,306,358,327 of 2,319,307,022 samples (99.4%) |
| `SingleThreadLamportBufferMultiOpStress` | **Interesting** — `-1, 2`: first write invisible, second visible |
| `VolatileSpscLamportBufferJcstressTest` | Acceptable only |
| `LockBasedLamportBufferOfferPollStress` | Acceptable only |
| `FastPathLamportBufferStress` | Acceptable only |
| `SpuriousWakeupLamportBufferStress` | Acceptable only |

Note the last two rows. jcstress ran the fast-path and spurious-wakeup buffers
for billions of executions and never hit the window — while
[Fray](../05-fray) found the fast-path NPE on its **4th** iteration and the
spurious wakeup on its **1st**. Systematic exploration is not a slower version
of stress testing; it answers a different question. Run both.

## Reproducing the headline result

Re-enable `NonVolatileSpscLamportBufferJcstressTest` by uncommenting its
`@JCStressTest` annotation:

```
   RESULT      SAMPLES     FREQ       EXPECT  DESCRIPTION
  1, 0, 0       35.222   <0,01%    Forbidden  Producer offered, consumer never saw it
  1, 0, 1  308.860.121   47,12%   Acceptable  Consumer polls before producer offers
  1, 1, 0  346.641.431   52,88%   Acceptable  Producer offers, consumer polls it
```

~35,000 executions out of 655 million in which the producer's write never
reached the consumer. That is "works on my machine" at 0.01% frequency — and it
is exactly the bug Fray structurally cannot see.

The archived HTML report for that run is checked in at
[`../results/jcstress/results/`](../results/jcstress/results) as
`pl.wsztajerowski.demo.lamport.jcstress.NonVolatileSpscLamportBufferJcstressTest.html`
— status `FAILED`, with the forbidden `1, 0, 0` state observed under several
compilation modes (Interpreter, C1, C2, and C2 with `-XX:+StressLCM`/`StressGCM`).

## How the module is built

* Tests live in **`src/main/java`**, not `src/test/java` — jcstress compiles them
  with its own annotation processor (`JCStressTestProcessor`, wired in via
  `annotationProcessorPaths`), which generates the runner infrastructure.
* `maven-shade-plugin` packages everything into a self-contained
  `target/jcstress.jar` with `org.openjdk.jcstress.Main` as its entry point.
* `exec-maven-plugin` runs that jar in the **`integration-test`** phase, with the
  `--add-opens` flags jcstress needs on modern JDKs.

Because the run is bound to `integration-test`, `verify` — not `test` — is the
lifecycle goal that executes the stress tests.

## Run

```bash
# Build the fat jar and run the full suite (this is the long one — expect minutes)
mvn -f demos/pom.xml -pl 06-jcstress -am verify

# Build the fat jar only, no stress run
mvn -f demos/pom.xml -pl 06-jcstress -am package
```

Then drive the jar directly for anything finer-grained:

```bash
# List the available tests
java -jar demos/06-jcstress/target/jcstress.jar -l

# Run one test (substring match on the class name)
java -jar demos/06-jcstress/target/jcstress.jar -t TwoProducers

# Fast feedback while iterating: sanity or quick mode
java -jar demos/06-jcstress/target/jcstress.jar -m sanity
java -jar demos/06-jcstress/target/jcstress.jar -t FastPath -m quick

# Crank it up when hunting a rare outcome
java -jar demos/06-jcstress/target/jcstress.jar -t NonVolatile -m stress

# Limit CPUs (also limits memory footprint) and choose a report directory
java -jar demos/06-jcstress/target/jcstress.jar -c 4 -r target/jcstress-results

# Print all results, including the boring ones
java -jar demos/06-jcstress/target/jcstress.jar -v
```

Useful flags: `-m <sanity|quick|default|tough|stress>` (test mode preset),
`-t <substring>` (filter), `-c <N>` (CPU count), `-iters <N>`, `-f <count>`
(forks), `-r <dir>` (report directory), `-l` (list), `-v` (verbose).

## Reports

An HTML report is written to `target/jcstress-results/index.html` (open
`index.html` for the summary, one page per test class). A curated copy of a
previous run is checked in at
[`../results/jcstress/results`](../results/jcstress/results).

## Gotcha: `mvn install` runs the whole suite

`-DskipTests` skips Surefire, **not** `exec-maven-plugin`. A plain
`mvn -f demos/pom.xml install -DskipTests` from the reactor root still runs the
entire jcstress suite and takes many minutes. To skip it:

```bash
mvn -f demos/pom.xml clean install -DskipTests -Dexec.skip=true
```
