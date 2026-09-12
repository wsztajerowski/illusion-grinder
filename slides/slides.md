---
theme: default
title: "The Illusion Grinder: Four Circles of Testing Hell for Concurrent Java"
info: |
  A journey through Lamport's Circular Buffer using JUnit, Fray, jcstress, and JMH.
  Each tool asks a fundamentally different question about correctness.
mdc: true
lineNumbers: false
highlighter: shiki
colorSchema: dark
fonts:
  sans: Inter
  mono: Fira Code
layout: cover
class: text-center cover-slide
---

<div class="cover-eyebrow"><mdi-fire-alert class="ico-red" /> A Concurrency Horror Story</div>

# The Illusion Grinder

<p class="cover-subtitle">
<em>Four circles of testing hell for concurrent Java</em>
</p>

<div class="cover-tools-row">
  <span class="chip blue"><mdi-test-tube /> JUnit</span>
  <span class="chip purple"><mdi-graph-outline /> Fray</span>
  <span class="chip orange"><mdi-pulse /> jcstress</span>
  <span class="chip green"><mdi-speedometer /> JMH</span>
</div>

---
layout: section
---

<div class="section-eyebrow"><mdi-skull-crossbones /> Prologue</div>

# The Problem
## *Your concurrent code is a house of cards. The wind just hasn't blown yet.*

---

# CI Was Green. Production Was on Fire. <mdi-fire class="ico-red inline-ico" />

<v-clicks>

- A bounded queue — simple, elegant, *"battle-tested"* <mdi-shield-check class="ico-green inline-ico" />
- Thousands of unit tests, all green <mdi-check-circle class="ico-green inline-ico" />
- Deployed to production <mdi-rocket-launch class="ico-blue inline-ico" />

</v-clicks>

<v-click>

```
Exception in thread "consumer-3" java.lang.NullPointerException
    at pl.wsztajerowski.demo.lamport.LamportBuffer.poll(LamportBuffer.java:47)
```
</v-click>

<v-click>
<div class="callout yellow">
<mdi-lightbulb-alert class="ico-yellow" />&nbsp;
Single-threaded tests are <strong>blind to concurrency bugs by design</strong>.<br>
They cannot fail on a race condition — there is no race.
</div>
</v-click>

---

# Four Questions. Four Tools. Four Circles of Hell.

<div class="tools-grid">
<div v-click class="card blue">
  <div class="card-icon"><mdi-test-tube /></div>
  <strong>① JUnit</strong>
  <small>The Limbo · single thread</small>
  <p>Does the algorithm work <em>at all</em>?</p>
</div>
<div v-click class="card purple">
  <div class="card-icon"><mdi-graph-outline /></div>
  <strong>② Fray</strong>
  <small>The Maze · systematic interleavings</small>
  <p>Does any thread schedule break it?</p>
</div>
<div v-click class="card orange">
  <div class="card-icon"><mdi-pulse /></div>
  <strong>③ jcstress</strong>
  <small>The Inferno · raw hardware</small>
  <p>Does the CPU / JVM / JIT break it?</p>
</div>
<div v-click class="card green">
  <div class="card-icon"><mdi-speedometer /></div>
  <strong>④ JMH</strong>
  <small>The Reckoning · the cost of safety</small>
  <p>What does correctness <em>cost</em>?</p>
</div>
</div>

<v-click>
<div class="callout">
<mdi-information-outline />&nbsp; Each circle catches the demons that the previous one couldn't see.
</div>
</v-click>

---
layout: section
---

<div class="section-eyebrow"><mdi-book-open-page-variant /> Cast of Characters</div>

# Lamport's Circular Buffer
## *The deceptively simple data structure we're about to torture.*

---
layout: two-cols
---

# The Algorithm

<mdi-account-tie class="ico-blue inline-ico" /> **Leslie Lamport's wait-free SPSC queue (1983)**

- Bounded circular array of capacity `N`
- Two indices: `head` (producer) and `tail` (consumer)
- **No locks. No CAS. No blocking.** <mdi-flash class="ico-yellow inline-ico" />
- O(1) offer and poll · low allocation pressure

<div class="callout blue">
Looks innocent. Hides teeth.
</div>

::right::

<div class="buffer-viz">

```
  capacity = 4

   ┌─────┬─────┬─────┬─────┐
   │  A  │  B  │     │     │
   └─────┴─────┴─────┴─────┘
      ↑           ↑
     tail        head
   (consumer)  (producer)
```

</div>

---

# The Algorithm — Pseudocode

<div class="two-col-code">

<v-click>

**Producer side** <mdi-arrow-right-bold class="ico-blue inline-ico" />

```
function offer(element):
    h ← head
    if buffer[h] != null:
        return false                 // full
    buffer[h] ← element
    head ← (h + 1) mod capacity      // publish
    return true
```

</v-click>

<v-click>

**Consumer side** <mdi-arrow-left-bold class="ico-orange inline-ico" />

```
function poll():
    t ← tail
    if buffer[t] == null:
        return empty
    element ← buffer[t]
    buffer[t] ← null                 // free slot
    tail ← (t + 1) mod capacity      // publish
    return element
```

</v-click>

</div>

<v-click>
<div class="callout purple">
<mdi-magnify /> &nbsp;Spot the bug? Neither did I. Neither did my unit tests.
</div>
</v-click>

---

# Four Implementations — One Interface

```java
public interface LamportBuffer<E> {
    boolean offer(E element);
    Optional<E> poll();
}
```

<div class="impl-grid">
<div v-click class="card blue">
  <div class="card-icon"><mdi-check-decagram /></div>
  <strong>Volatile</strong>
  <small>SPSC · volatile fields</small>
  <p>Correct for one producer, one consumer</p>
</div>
<div v-click class="card red">
  <div class="card-icon"><mdi-bug /></div>
  <strong>NonVolatile</strong>
  <small>No synchronisation</small>
  <p><code>volatile</code> deliberately removed</p>
</div>
<div v-click class="card yellow">
  <div class="card-icon"><mdi-lock /></div>
  <strong>LockBased</strong>
  <small>MPMC · ReentrantLock</small>
  <p>Safe for many producers and consumers</p>
</div>
<div v-click class="card orange">
  <div class="card-icon"><mdi-emoticon-devil /></div>
  <strong>FastPath</strong>
  <small>Lock + unlocked fast-path</small>
  <p>Subtle race in the optimisation path.</p>
</div>
</div>

---
layout: center
---

# The Catch <mdi-hook class="ico-yellow inline-ico" />

<div class="callout yellow big-callout">
All four pass unit tests.<br>
<strong>Two of them are actually broken.</strong>
</div>

<div v-click class="subtle-note">
The "machine for grinding illusions" starts here.
</div>

---
layout: section
class: section-junit
---

<div class="section-eyebrow"><mdi-circle-slice-1 /> Circle I · The Limbo</div>

# JUnit Contract Tests
## *"Look mom, no errors!" — the comfortable lie.*

---
layout: two-cols
---

# The Foundation

<mdi-pillar class="ico-blue inline-ico" /> **Verify the algorithm's functional logic.**

- Single-threaded — no concurrency
- Same contract suite runs against **all four implementations**
- Fast, deterministic, always-on

**What gets tested:**

- <mdi-arrow-right-thin /> Basic offer / poll · FIFO ordering
- <mdi-sync /> Wrap-around · capacity enforcement
- <mdi-counter /> Size tracking · null rejection

::right::

<v-click>

```java
@ParameterizedTest
@MethodSource("bufferImplementations")
void shouldPreserveFifoOrder(
        LamportBuffer<Integer> buffer) {
    assertThat(buffer)
        .accepting(1, 2, 3)
        .whenPolled(3)
        .returns(1, 2, 3);
}

static Stream<LamportBuffer<Integer>>
        bufferImplementations() {
    return Stream.of(
        new VolatileLamportBuffer<>(8),
        new NonVolatileLamportBuffer<>(8),
        new LockBasedLamportBuffer<>(8),
        new FastPathLamportBuffer<>(8)
    );
}
```

</v-click>

---

# The False Positive <mdi-emoticon-confused class="ico-yellow inline-ico" />

Run the full suite against all four implementations — including the deliberately broken ones:

```
[INFO] Running LamportBufferContractTest
[INFO] Tests run: 56, Failures: 0, Errors: 0, Skipped: 0

[INFO] BUILD SUCCESS
```

<div class="callout green">
<mdi-party-popper class="ico-green" />&nbsp; All green. Time to deploy. What could possibly go wrong?
</div>

<v-click>
<div class="callout red">
<mdi-skull class="ico-red" />&nbsp; Two implementations are broken. Your CI just  <em>lied to your face</em>.
</div>
</v-click>

---

# Why JUnit Cannot See This

<v-clicks>

- <mdi-account /> &nbsp;JUnit runs on **one thread** — no concurrent access to trigger the bug
- <mdi-wall /> &nbsp;The JVM applies no memory barrier — the test never exercises visibility
- <mdi-eye-off /> &nbsp;Missing `volatile` is **invisible** to single-threaded tests

</v-clicks>

<v-click>
<div class="callout blue">
<mdi-lightbulb /> &nbsp;Write contract tests first. Keep them always.<br>
But do not mistake a green suite for a concurrency correctness proof.
</div>
</v-click>

<v-click>
<div class="verdict">
<span class="verdict-label">Verdict:</span> <em>Necessary. Insufficient. On to the next circle.</em> <mdi-arrow-down-bold-circle class="ico-purple inline-ico" />
</div>
</v-click>

---
layout: section
class: section-fray
---

<div class="section-eyebrow"><mdi-circle-slice-3 /> Circle II · The Maze of Schedules</div>

# Fray
## *A scheduler with a sadistic streak.*

---

# Fray's Core Idea

<blockquote class="big-quote">
"Does <strong>any execution schedule</strong> exist that breaks the logic of my system?"
</blockquote>

<v-clicks>

- <mdi-controller class="ico-purple inline-ico" /> &nbsp;Controls the **scheduler** — decides which thread runs next
- <mdi-radar class="ico-purple inline-ico" /> &nbsp;Instruments synchronisation points and controls thread switches
- <mdi-infinity class="ico-purple inline-ico" /> &nbsp;Every run explores a **different interleaving** — exhaustively
- <mdi-dice-multiple class="ico-purple inline-ico" /> &nbsp;No reliance on the OS scheduler *"getting lucky"*

</v-clicks>

---
hide: true
---

# Every Run — A Different Schedule

```
Run #1:   T1 → T1 → T2 → T1 → T2
Run #2:   T2 → T1 → T2 → T2 → T1
Run #3:   T1 → T2 → T2 → T1 → T1
...
Run #N:   every reachable schedule explored
```

No reliance on the OS scheduler "getting lucky".

---

# A Footgun in the Config <mdi-foot-print class="ico-yellow inline-ico" />

By default, Fray uses a shared report directory for all test outputs and wipes it before each run.
Every test class **nukes** the previous test's recording. <mdi-bomb class="ico-red inline-ico" />

**The cure:**

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <configuration>
        <systemPropertyVariables>
            <fray.organize.by.test>true</fray.organize.by.test>
        </systemPropertyVariables>
    </configuration>
</plugin>
```

<div class="callout yellow">
Lose one bug report and you'll never re-roll the same dice again. Save them all.
</div>

---

# A Fray Test

```java
@ConcurrencyTest
void twoConsumersMustNotReadSameElement() {
    buffer.offer(1);
    buffer.offer(2);
    Thread t1 = new Thread(() -> results.add(buffer.poll()));
    Thread t2 = new Thread(() -> results.add(buffer.poll()));
    t1.start(); t2.start();
    t1.join();  t2.join();
    assertThat(results).containsExactlyInAnyOrder(
        Optional.of(1), Optional.of(2));
}
```

<div class="callout purple">
<mdi-magic-staff class="ico-purple" />&nbsp;
Looks like an ordinary JUnit test. Fray hijacks the scheduler underneath.
</div>

---
layout: two-cols
---

# What Fray Finds — Unlocked Fast-path

<div class="slide-subtitle">Enter <code>FastPathLamportBuffer</code> — the optimisation we promised in Circle I.</div>

```java
public Optional<E> poll() {
    if (buffer[readPosition] == null) { // ← no lock!
        return Optional.empty();
    }
    lock.lock();
    try {
        E elem = buffer[readPosition]; // ← re-read
        // ...
    } finally { lock.unlock(); }
}
```

::right::

**The interleaving Fray constructs:** <mdi-format-list-numbered class="ico-purple inline-ico" />

```
T1: passes null-check      (slot has element)
T2: passes null-check      (slot still has element)
T1: acquires lock, reads, advances readPosition
T2: acquires lock, reads... null → 💥 NPE
```

<v-click>
<div class="callout purple">
<mdi-alert-octagon class="ico-purple" />&nbsp;
Check-then-act without a lock is a race.<br>
The optimisation that wasn't.
</div>
</v-click>

---
layout: two-cols
---

# What Fray Finds — Spurious Wakeup

```java
public Optional<E> poll() throws InterruptedException {
    lock.lock();
    try {
        if (isEmpty()) {
            condition.await(); // ← if, not while!
        }
        return Optional.of(buffer[readPosition++]);
    } finally { lock.unlock(); }
}
```

::right::

**The schedule Fray triggers:**

```
await() returns early 👻
  → still empty
  → Optional.of(null)
  → 💥
```

<v-click>
<div class="callout purple">
Guard <code>Condition.await()</code> with <code>while</code>, not <code>if</code>.
</div>
</v-click>

<v-click>

```java
// ❌ Unsafe — one spurious wakeup is enough
if (isEmpty()) condition.await();

// ✅ Correct — re-check after every wakeup
while (isEmpty()) condition.await();
```

</v-click>

---

# Deterministic Replay <mdi-replay class="ico-purple inline-ico" />

**Classic torture:** the test fails on iteration 721. *Which schedule caused it?*

```
2026-05-26 22:49:04 [INFO]: Error found at iter: 721, step: 1850, Elapsed time: 60ms
2026-05-26 22:49:04 [INFO]: Error: java.lang.AssertionError: [consumer must receive the produced value,
not null from a spurious wakeup]
Expecting actual not to be null
Thread: Thread[#3,main,5,main]
    at pl.wsztajerowski.demo.lamport.fray.edgecase.ConditionalLamportBufferFrayTest
    .spuriousWakeupCausesReadFromEmptyBuffer(ConditionalLamportBufferFrayTest.java:54)
    ...

2026-05-26 22:49:04 [INFO]: The recording is saved to /demos/05-fray/target/fray/fray-report/.../recording
```

<v-click>

**Fray records & replays the exact schedule:** <mdi-record-rec class="ico-red inline-ico" />

```java
@ConcurrencyTest(
        replay = "PATH_TO_FRAY_REPORT/recording"
)
```

Attach a debugger. Step through the exact thread switches. *Watch the bug bloom in slow motion.*

</v-click>

---

# Fray's Blind Spot <mdi-eye-off-outline class="ico-yellow inline-ico" />

`NonVolatileLamportBufferFrayTest` — missing `volatile`. Fray result:

```
[INFO] Running pl.wsztajerowski.demo.lamport.fray.NonVolatileLamportBufferFrayTest
[INFO] Tests run: 3000, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 4.459 s

BUILD SUCCESS
```

<div class="callout yellow">
3000 schedules. Zero failures. <strong>And the code is still broken.</strong>
</div>

---

# Why Fray Cannot See This

<v-clicks>

- <mdi-controller /> &nbsp;Fray controls **thread scheduling** — not CPU caches or JIT optimisations
- <mdi-memory /> &nbsp;The bug is a **memory visibility** failure, not a scheduling failure
- <mdi-chip /> &nbsp;It only appears with real hardware effects (cache latency + reordering)

</v-clicks>

<v-click>
<div class="callout purple">

**Fray proves:** "No thread ordering breaks my logic." <mdi-check class="ico-green inline-ico" /><br>
**Fray cannot prove:** "The JVM / CPU will not reorder my memory accesses." <mdi-close class="ico-red inline-ico" />

</div>
</v-click>

<v-click>
<div class="verdict">
<span class="verdict-label">Verdict:</span> <em>Logic, yes. Hardware reality, no. Descend further.</em> <mdi-arrow-down-bold-circle class="ico-orange inline-ico" />
</div>
</v-click>

---
layout: section
class: section-jcstress
---

<div class="section-eyebrow"><mdi-circle-slice-5 /> Circle III · The Inferno</div>

# jcstress
## *Where your code meets the actual CPU. Bring asbestos.*

---

# jcstress's Core Idea

<blockquote class="big-quote">
"Is this code correct relative to the <strong>Java Memory Model</strong>?"
</blockquote>

<v-clicks>

- <mdi-flask class="ico-orange inline-ico" /> &nbsp;OpenJDK's laboratory for the Java Memory Model
- <mdi-counter class="ico-orange inline-ico" /> &nbsp;Runs **millions** of iterations on real hardware
- <mdi-dice-6 class="ico-orange inline-ico" /> &nbsp;Lets OS / JVM / CPU choose execution order — then *observes outcomes*
- <mdi-chip class="ico-orange inline-ico" /> &nbsp;Can pin actors to specific CPUs (affinity, where supported)
- <mdi-fire class="ico-orange inline-ico" /> &nbsp;Stresses cache coherence by physically placing the load

</v-clicks>

---

# A jcstress Test

```java
@JCStressTest
@Outcome(id = "1, 1, 0", expect = Expect.ACCEPTABLE,
    desc = "Producer offers, then consumer polls it ✅")
@Outcome(id = "1, 0, 1", expect = Expect.ACCEPTABLE,
    desc = "Consumer polls before producer offers ✅")
@Outcome(id = "1, 0, 0", expect = Expect.FORBIDDEN,
    desc = "Producer offered… consumer never saw it 💥 (visibility bug)")
@State
public class NonVolatileSpscLamportBufferJcstressTest {
    private final LamportBuffer<Integer> queue = NonVolatileLamportBuffer.createBuffer(Integer.class, 2);

    @Actor public void producer(III_Result r) { r.r1 = queue.offer(1) ? 1 : 0; }
    @Actor public void consumer(III_Result r) { r.r2 = queue.poll().orElse(0); }
    @Arbiter public void arbiter(III_Result r) { r.r3 = queue.poll().orElse(0); }
}
```

---
layout: two-cols
---

# Why `volatile` Is Non-Negotiable

<div class="slide-subtitle">Before we look at the result — remember why missing <code>volatile</code> matters.</div>

<mdi-memory class="ico-orange inline-ico" /> Each CPU core has its own cache.

<v-clicks>

- Without a memory barrier, writes stay **local**
- The JIT compiler and CPU may **reorder** instructions
- `volatile` creates a **happens-before** guarantee
- Skip it, and your thread lives in a parallel universe

</v-clicks>

::right::

<v-click>
<div class="mem-diagram bad">

**Without `volatile` — stale read** <mdi-close-circle class="ico-red inline-ico" />

```
Producer CPU             Consumer CPU
─────────────────────────────────────
write head = 1
  └─ stays in L1 cache 💾
                         read head → 0  ← stale!
```

</div>
</v-click>

<v-click>
<div class="mem-diagram good">

**With `volatile` — guaranteed visibility** <mdi-check-circle class="ico-green inline-ico" />

```
Producer CPU             Consumer CPU
─────────────────────────────────────
write head = 1
  └─ flushed to main memory ⚡
                         read head → 1
```

</div>
</v-click>

---

# jcstress Finds What Fray Cannot <mdi-target class="ico-orange inline-ico" />

`NonVolatileLamportBuffer` — the buffer that "passed everything":

```
   RESULT      SAMPLES     FREQ       EXPECT  DESCRIPTION
  1, 0, 0       35.222   <0,01%    Forbidden  Producer offered, consumer never saw it ← 💥
  1, 0, 1  308.860.121   47,12%   Acceptable  Consumer polls before producer offers
  1, 1, 0  346.641.431   52,88%   Acceptable  Producer offers, consumer polls it
```

<v-click>
<div class="callout orange big-callout">
<mdi-fire class="ico-orange" />&nbsp;
<strong>~35,000 executions </strong> in which the producer's write never reached the consumer's cache.<br>
Welcome to "works on my machine" at 0.01% frequency.
</div>
</v-click>

---
layout: center
---

<img src="/src/resources/jcstress-result.png" alt="jcstress result output" style="max-height: 80vh; margin: 0 auto; border-radius: 8px; box-shadow: 0 8px 32px rgba(251,146,60,0.25);" />

---

# Fray vs. jcstress

<div class="vs-table">

|  | <mdi-graph-outline class="ico-purple inline-ico" /> Fray | <mdi-pulse class="ico-orange inline-ico" /> jcstress |
|--|------|---------|
| **Core question** | Does a bad schedule exist? | Is this correct vs. the JMM? |
| **Controls** | Thread scheduling | Nothing — lets OS/CPU/JIT decide |
| **Finds** | Logic races · deadlocks | Visibility bugs · reorderings |
| **Deterministic replay** | <mdi-check-circle class="ico-green" /> Yes | <mdi-close-circle class="ico-red" /> No |
| **Blind spot** | JMM / hardware reorderings | Rare interleavings |
| **Vibe** | A patient sadist | A drunk physicist |

</div>

<v-click>
<div class="callout orange">

<code>NonVolatileLamportBuffer</code> &nbsp;<mdi-arrow-right class="inline-ico" />&nbsp; passes Fray, fails jcstress.<br>
<code>FastPathLamportBuffer</code> &nbsp;<mdi-arrow-right class="inline-ico" />&nbsp; caught by Fray, may be missed by jcstress.

</div>
</v-click>

---
hide: true
layout: two-cols
---

# TOCTOU with Two Producers

```java
@JCStressTest
@Outcome(id = "true, false",  expect = ACCEPTABLE)
@Outcome(id = "false, true",  expect = ACCEPTABLE)
@Outcome(id = "true, true",   expect = ACCEPTABLE_INTERESTING,
         desc = "TOCTOU: both believe they succeeded")
@Outcome(id = "false, false", expect = FORBIDDEN)
@State
public class TwoProducersVolatileBufferStress {

    private final LamportBuffer<Integer> buffer =
        new VolatileLamportBuffer<>(1); // capacity 1!

    @Actor public void producer1(ZZ_Result r) { r.r1 = buffer.offer(1); }
    @Actor public void producer2(ZZ_Result r) { r.r2 = buffer.offer(2); }
}
```

::right::

```
RESULT           FREQ    EXPECT
true, false      48.3%   Acceptable
false, true      48.1%   Acceptable
true, true        3.6%   Interesting ← TOCTOU!
false, false      0.0%   Forbidden
```

<v-click>

Both producers see the buffer as non-full **simultaneously** — one silently overwrites the other.

<div class="callout orange">
<code>volatile</code> protects <strong>visibility</strong>, not <strong>atomicity</strong>.
</div>

</v-click>

---
layout: section
class: section-jmh
---

<div class="section-eyebrow"><mdi-circle-slice-7 /> Circle IV · The Reckoning</div>

# JMH
## *The bill arrives. Correctness is never free.*

---

# Only Benchmark Correct Code <mdi-scale-balance class="ico-green inline-ico" />

**Why naive benchmarks lie:**

- <mdi-delete-sweep class="ico-red inline-ico" /> &nbsp;JIT eliminates "dead" computations — you measure *nothing*
- <mdi-thermometer-low class="ico-red inline-ico" /> &nbsp;Poor warmup distorts steady-state numbers
- <mdi-arrow-up-bold-box class="ico-red inline-ico" /> &nbsp;Loop hoisting moves work outside the benchmark body

<v-click>

**JMH solves this:**

- <mdi-shield-check class="ico-green inline-ico" /> &nbsp;`Blackhole` prevents dead code elimination
- <mdi-shield-check class="ico-green inline-ico" /> &nbsp;Warmup + fork isolation produce stable, comparable measurements
</v-click>

<v-click>
<div class="callout green">
<mdi-skull-outline class="ico-green" />&nbsp;
Accurate numbers for broken code are worse than no numbers at all.
</div>
</v-click>

---

# A JMH Benchmark

```java
@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Group)
public class ApplesToApplesBenchmark {

    @Param({"64", "1024"}) int capacity;
    LamportBuffer<Integer> buffer;

    @Benchmark @Group("spsc") @GroupThreads(1)
    public void producer(Blackhole bh) { bh.consume(buffer.offer(42)); }

    @Benchmark @Group("spsc") @GroupThreads(1)
    public void consumer(Blackhole bh) { bh.consume(buffer.poll()); }
}
```

---

# Lock-Free vs. Lock-Based

**1 producer + 1 consumer — same topology, two implementations:**

```
Benchmark                             (capacity)  (implementation)   Mode  Cnt         Score   Error  Units
JmhBenchmark.applesToApples                   64          VOLATILE  thrpt    2  16814467,255          ops/s
JmhBenchmark.applesToApples:consumer          64          VOLATILE  thrpt    2   8370300,747          ops/s
JmhBenchmark.applesToApples:producer          64          VOLATILE  thrpt    2   8444166,508          ops/s
JmhBenchmark.applesToApples                   64              LOCK  thrpt    2   4277676,491          ops/s
JmhBenchmark.applesToApples:consumer          64              LOCK  thrpt    2   2138839,366          ops/s
JmhBenchmark.applesToApples:producer          64              LOCK  thrpt    2   2138837,125          ops/s
JmhBenchmark.applesToApples                 1024          VOLATILE  thrpt    2  18750040,034          ops/s
JmhBenchmark.applesToApples                 1024              LOCK  thrpt    2   5942621,982          ops/s
```

<v-click>
<div class="callout green big-callout">
<mdi-rocket class="ico-green" />&nbsp;
Lock-free volatile: <strong>~3× higher throughput</strong> for SPSC workloads.<br>
Lock-free is not always better — only when used correctly.
</div>
</v-click>

---
layout: section
---

<div class="section-eyebrow"><mdi-flag-checkered /> Epilogue</div>

# The Testing Pyramid
## *Four layers. Four questions. Zero illusions.*

---

# Each Layer Answers a Different Question

<div class="pyramid">

```
                    ┌───────────────┐
                    │      JMH      │               "How fast is it?"
                    └───────────────┘
                 ┌─────────────────────┐
                 │      jcstress       │            "Does CPU/JVM/JIT break it?"
                 └─────────────────────┘
              ┌───────────────────────────┐
              │           Fray            │         "Does any thread schedule break it?"
              └───────────────────────────┘
           ┌─────────────────────────────────┐
           │      JUnit Contract Tests       │      "Does the algorithm work at all?"
           └─────────────────────────────────┘
```

</div>

<div class="callout">
<mdi-stairs-up />&nbsp; <strong>Four circles walked.</strong> You now know which demons live where — and which tool drags them into the light. Skip a step, fall through.
</div>

---

# When to Reach for Which Tool

| Symptom / Goal | Reach for |
|---|---|
| <mdi-check-circle class="ico-blue inline-ico" /> Functional correctness & regression safety | **JUnit** |
| <mdi-shuffle-variant class="ico-purple inline-ico" /> Scheduling bugs (races, deadlocks, ordering) | **Fray** |
| <mdi-chip class="ico-orange inline-ico" /> JMM bugs (visibility, reordering, atomicity) | **jcstress** |
| <mdi-speedometer class="ico-green inline-ico" /> Throughput / latency / perf regressions | **JMH** |

---

# Three Golden Rules

<v-clicks>
<div class="rule">
  <h2><mdi-numeric-1-circle class="ico-blue inline-ico" /> Never trust green unit tests as a concurrency proof</h2>
  <p>A single-threaded test cannot trigger a race. All four implementations passed; two were broken.</p>
</div>
<div class="rule">
  <h2><mdi-numeric-2-circle class="ico-purple inline-ico" /> Fray and jcstress are complementary — not interchangeable</h2>
  <p>Fray finds scheduling bugs jcstress may miss. jcstress finds JMM bugs Fray cannot see. <strong>Run both.</strong></p>
</div>
<div class="rule">
  <h2><mdi-numeric-3-circle class="ico-green inline-ico" /> Only benchmark code you have already proven correct</h2>
  <p>Accurate numbers for broken code are worse than no numbers.</p>
</div>
</v-clicks>

---
layout: two-cols
---

# Resources

**Tools used in this talk** <mdi-toolbox class="ico-blue inline-ico" />

- [Fray](https://github.com/cmu-pasta/fray) — CMU PASTA Lab / Microsoft Research
- [jcstress](https://openjdk.org/projects/code-tools/jcstress/) — OpenJDK
- [JMH](https://github.com/openjdk/jmh) — OpenJDK

**Demo repository** <mdi-github class="ico-purple inline-ico" />

```
github.com/wsztajerowski/
   systematic-concurrency-testing
```

::right::

**Further reading** <mdi-book-open class="ico-green inline-ico" />

- [A Randomized Scheduler with
  Probabilistic Guarantees of Finding Bugs](https://www.microsoft.com/en-us/research/wp-content/uploads/2016/02/asplos277-pct.pdf)
- [Partial Order Aware Concurrency Sampling](https://www.cs.columbia.edu/~junfeng/papers/pos-cav18.pdf)
- [JSR-133 Java Memory Model](https://jcp.org/en/jsr/detail?id=133)

---
layout: center
class: text-center thank-you
---

# Thank You <mdi-hand-wave class="ico-yellow inline-ico" />

<br>

## Questions? <mdi-comment-question-outline class="ico-blue inline-ico" />

<br>
<br>

<div class="signoff">
Now go look at your "well-tested" concurrent code.<br>
<em>It's probably lying to you too.</em>
</div>

<br>

<span class="footer-title">
The Illusion Grinder - Four circles of testing hell for concurrent Java
</span>
