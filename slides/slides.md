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

<p class="cover-byline">by Wiktor Sztajerowski</p>

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
    at pl.wsztajerowski.demo.lamport.mpmc.FastPathLamportBuffer.poll(FastPathLamportBuffer.java:46)
```
</v-click>

<v-click>
<div class="callout yellow">
<mdi-lightbulb-alert class="ico-yellow" />&nbsp;
Single-threaded tests are blind to concurrency bugs by design.<br>&nbsp;
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

<mdi-account-tie class="ico-blue inline-ico" /> **Leslie Lamport's wait-free SPSC queue** — two cursors, **no locks, no CAS** <mdi-flash class="ico-yellow inline-ico" />


<pre class="buffer-viz">
  capacity = 4

   ┌─────┬─────┬─────┬─────┐
   │  A  │  B  │     │     │
   └─────┴─────┴─────┴─────┘
      ↑           ↑
   readPos    writePos
   (consumer)  (producer)
</pre>

::right::

<v-click>

```
function poll():
  r ← readPos
  if buffer[r] == null:
    return empty
  element ← buffer[r]
  buffer[r] ← null                 // free slot
  readPos ← (r + 1) mod capacity   // publish
  return element
```

</v-click>

<v-click>

```
function offer(element):
  w ← writePos
  if buffer[w] != null:
    return false                   // full
  buffer[w] ← element
  writePos ← (w + 1) mod capacity  // publish
  return true
```

</v-click>

---

# Four Implementations — One Interface

```java
public interface LamportBuffer<E> {
  boolean offer(E element);
  Optional<E> poll();
  boolean isEmpty();
  int size();
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
  <p><code>volatile</code> deliberately removed — writes may go unseen</p>
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

<v-click>

**What gets tested:**

- <mdi-arrow-right-thin /> Basic offer / poll · FIFO ordering
- <mdi-sync /> Wrap-around · capacity enforcement
- <mdi-counter /> Size tracking · null rejection
</v-click>

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

# The False Negative <mdi-emoticon-confused class="ico-yellow inline-ico" />

Run the full suite against all four implementations — including the deliberately broken ones:

```
[INFO] Running LamportBufferContractTest
[INFO] Tests run: 68, Failures: 0, Errors: 0, Skipped: 0

[INFO] BUILD SUCCESS
```

<div v-click class="callout green">
<mdi-party-popper class="ico-green" />&nbsp; All green. Time to deploy. What could possibly go wrong?
</div>

<div v-click class="callout red">
<mdi-skull class="ico-red" />&nbsp; Two implementations are broken. Your CI just&nbsp; <em>lied to your face</em>.
</div>

---

# Circle I Closes — JUnit

<div class="vs-table">

| <mdi-arm-flex class="ico-blue inline-ico" /> Strengths | <mdi-eye-off-outline class="ico-red inline-ico" /> Blind spots |
|---|---|
| **Simple.** Every developer already knows it — no new syntax, no new runner | **Cannot catch concurrency bugs.** One thread cannot construct a race, so no race can fail |
| **Fast.** Cheap enough to run on every save | Which is why `NonVolatile` and `FastPath` pass every one — and are still broken |

</div>

<v-click>
<div class="callout blue">
<mdi-lightbulb class="ico-blue" />&nbsp;
Write units first and keep them always — just never mistake a green suite for a concurrency proof.
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

- <mdi-controller class="ico-purple inline-ico" /> &nbsp;Fray controls the **scheduler** — it decides which thread runs next, so every run explores a **different interleaving**
- <mdi-dice-multiple class="ico-purple inline-ico" /> &nbsp;`@ConcurrencyTest` samples **1000 schedules by default** — no reliance on the OS scheduler *"getting lucky"*

</v-clicks>

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
  assertThat(results)
    .containsExactlyInAnyOrder(
      Optional.of(1), 
      Optional.of(2)
    );
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

<div class="slide-subtitle">Enter <code>FastPathLamportBuffer</code> </div>

```java
public Optional<E> poll() {
  if (buffer[readPos] == null) { // ← no lock!
    return Optional.empty();
  }
  lock.lock();
  try {
    E elem = buffer[readPos]; // ← re-read
    // ...
  } finally { lock.unlock(); }
}
```

<v-click>

**The interleaving Fray constructs:** <mdi-format-list-numbered class="ico-purple inline-ico" />

```
T1: passes null-check      (slot has element)
T2: passes null-check      (slot still has element)
T1: acquires lock, reads, advances readPosition
T2: acquires lock, reads... null → 💥 NPE
```
</v-click>

::right::

<div v-click class="callout purple">
<mdi-alert-octagon class="ico-purple" />&nbsp;
Check-then-act without a lock is a race.<br>
<strong>This is the exception we opened with.</strong> The optimisation that wasn't.
</div>

<div v-click class="mem-diagram bad">
```java
// ❌ check, then lock
if (buffer[readPosition] == null)
  return Optional.empty();
lock.lock();
```
</div>

<div v-click class="mem-diagram good">
```java
// ✅ lock, then check
lock.lock();
if (buffer[readPosition] == null)
  return Optional.empty();
```
</div>

---

# Deterministic Replay <mdi-replay class="ico-purple inline-ico" />

**Classic torture:** the test fails on iteration 4 of 1000. *Re-run it and it passes.*

```
2026-05-27 00:10:01 [INFO]: Error found at iter: 4, step: 26, Elapsed time: 32ms
2026-05-27 00:10:01 [INFO]: Error: java.lang.NullPointerException
java.lang.NullPointerException
    at java.base/java.util.Optional.of(Optional.java:113)
    at pl.wsztajerowski.demo.lamport.mpmc.FastPathLamportBuffer.poll(FastPathLamportBuffer.java:46)
    at pl.wsztajerowski.demo.lamport.fray.edgecase.FastPathLamportBufferFrayTest
        .twoConsumersOnSingleElementMustNotCrash(FastPathLamportBufferFrayTest.java:37)
```

<div class="subtle-note">The NPE is <code>Optional.of(null)</code> — the same line 46 as the exception we opened with.</div>

<v-click>

**Fray records & replays the exact schedule:** <mdi-record-rec class="ico-red inline-ico" />

```java
@ConcurrencyTest(
    replay = "PATH_TO_FRAY_REPORT/recording"
)
```

Without the recording the bug is gone — *with it, attach a debugger and step the exact switches.*

</v-click>

---

# Fray's Blind Spot <mdi-eye-off-outline class="ico-yellow inline-ico" />

`NonVolatileLamportBufferFrayTest` — missing `volatile`. Fray result:

```
[INFO] Running pl.wsztajerowski.demo.lamport.fray.NonVolatileLamportBufferFrayTest
[INFO] Tests run: 2000, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 5.441 s

BUILD SUCCESS
```

<div class="callout yellow">
2 tests × 1000 schedules each. Zero failures. <strong>And the code is still broken.</strong>
</div>

---

# Circle II Closes — Fray

<div class="vs-table">

| <mdi-arm-flex class="ico-purple inline-ico" /> Strengths | <mdi-eye-off-outline class="ico-red inline-ico" /> Blind spots |
|---|---|
| **Reads like JUnit.** `@ConcurrencyTest` on an ordinary test body — no new syntax, no new runner | **Cannot catch Java Memory Model issues.** Every schedule it explores is sequentially consistent — `NonVolatile` stays green |
| **A different interleaving every run** — found `FastPath`'s NPE within the first handful of schedules, replayable under a debugger | **Needs an instrumented JVM.** The plugin builds a whole instrumented JDK image before anything runs |

</div>

<v-click>
<div class="callout purple">
<mdi-dice-multiple class="ico-purple" />&nbsp;
A green run is&nbsp;<strong>1000 sampled schedules</strong>&nbsp;— not a proof — Fray gives probability, not exhaustiveness.
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

- <mdi-flask class="ico-orange inline-ico" /> &nbsp;OpenJDK's laboratory for the Java Memory Model — **millions** of iterations on real hardware
- <mdi-dice-6 class="ico-orange inline-ico" /> &nbsp;Controls **nothing**: the OS, JVM and CPU pick the order and jcstress just *observes outcomes* — pinning actors to cores to stress cache coherence

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

<v-clicks>
<div class="callout">
<mdi-key-variant />&nbsp;
<strong>Reading the triple:</strong> <code>III_Result</code> is three ints — <code>r1</code> the producer's <code>offer</code>, <code>r2</code> the consumer's <code>poll</code>, <code>r3</code> the arbiter's <code>poll</code>.
Both <code>@Actor</code>s run concurrently; the <code>@Arbiter</code> runs&nbsp;<em>after both finish</em>.
So <code>1, 0, 0</code> reads: offered, nobody saw it, and it never arrived.
</div>
</v-clicks>

---
layout: two-cols
---

# Why `volatile` Is Non-Negotiable

<mdi-memory class="ico-orange inline-ico" /> Each CPU core has its own cache.

- Without a memory barrier, writes stay **local**
- The JIT compiler and CPU may **reorder** instructions
- `volatile` creates a **happens-before** guarantee
- Skip it, and your thread lives in a parallel universe


::right::

<v-click>
<div class="mem-diagram bad">

**Without `volatile` — stale read** <mdi-close-circle class="ico-red inline-ico" />

```
Producer CPU             Consumer CPU
─────────────────────────────────────
write writePos = 1
  └─ stays in L1 cache 💾
                     read writePos → 0  ← stale!
```

</div>
</v-click>

<v-click>
<div class="mem-diagram good">

**With `volatile` — guaranteed visibility** <mdi-check-circle class="ico-green inline-ico" />

```
Producer CPU             Consumer CPU
─────────────────────────────────────
write writePos = 1
  └─ flushed to main memory ⚡
                     read writePos → 1
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
layout: two-cols
---

# <mdi-emoticon-devil class="ico-orange inline-ico" /> TOCTOU — When `volatile` Is Not Enough

<div class="slide-subtitle"><strong>TOCTOU</strong> — <em>time-of-check to time-of-use</em>: code checks a value, then acts on it, and the value changed in the gap.</div>

```java
// VolatileLamportBuffer, cap 2 — but TWO producers
@Actor void producer1(III_Result r) 
{ r.r1 = buffer.offer(1) ? 1 : 0; }
@Actor void producer2(III_Result r) 
{ r.r2 = buffer.offer(2) ? 1 : 0; }

@Arbiter void drain(III_Result r) {
  int stored = 0;
  while (buffer.poll().isPresent()) stored++;
  r.r3 = stored;        // how many actually landed
}
```

<div class="subtle-note">
Without the arbiter's count,&nbsp;<em>"both offers returned true"</em>&nbsp;is indistinguishable from correct.
</div>

::right::

```
 RESULT    FREQ       EXPECT
1, 1, 2   98,00%   Acceptable   both stored ✅
1, 1, 1    1,48%  Interesting   lost update 💥
1, 0, 1    0,26%  Interesting   "full" — free slot 💥
0, 1, 1    0,25%  Interesting   same, other producer 💥
```

<v-click>
<div class="callout orange">
<code>volatile</code>&nbsp;protects&nbsp;<strong>visibility</strong>,&nbsp;not&nbsp;<strong>atomicity</strong>
One non-atomic check-then-act, two distinct failures.
<code>offer</code>&nbsp;fills the slot, then moves the cursor — and&nbsp;<code>size()</code>&nbsp;reads both.
</div>
</v-click>

<v-click>
<div class="callout purple">
It was never broken — it was&nbsp;<strong>SPSC:</strong> single producer, single consumer.
We brought a second producer.
</div>
</v-click>

---

# Fray vs. jcstress

<div class="vs-table">

|  | <mdi-graph-outline class="ico-purple inline-ico" /> Fray | <mdi-pulse class="ico-orange inline-ico" /> jcstress |
|--|------|---------|
| **Core question** | Does a bad schedule exist? | Is this correct vs. the JMM? |
| **Controls** | Thread scheduling | Nothing — lets OS/CPU/JIT decide |
| **Finds** | Logic races · deadlocks | Visibility bugs · reorderings |
| **Deterministic replay** | <mdi-check-circle class="ico-green" /> Yes | <mdi-close-circle class="ico-red" /> No |
| **Blind spot** | JMM / hardware reorderings | Scenarios you didn't think to model |
| **Vibe** | A patient sadist | A drunk physicist |

</div>

<v-click>
<div class="callout orange">

<code>NonVolatileLamportBuffer</code> &nbsp;<mdi-arrow-right class="inline-ico" />&nbsp; passes Fray, fails jcstress — Fray <em>structurally</em> cannot see it.<br>
<code>FastPathLamportBuffer</code> &nbsp;<mdi-arrow-right class="inline-ico" />&nbsp; <em>both</em> catch it (jcstress at 1.1%) — but only with a two-consumer test.

</div>
</v-click>

---

# Circle III Closes — jcstress

<div class="vs-table">

| <mdi-arm-flex class="ico-orange inline-ico" /> Strengths | <mdi-eye-off-outline class="ico-red inline-ico" /> Blind spots |
|---|---|
| **No instrumentation.** Runs your real bytecode, on any JVM | **Struggles with I/O-intensive code.** Millions of concurrent runs exhaust the open-file limit |
| **Catches JMM bugs** — the visibility failure Fray structurally cannot see, plus lost updates under two producers | **Orders of magnitude slower than Fray.** Millions of iterations per test, not a thousand schedules |

</div>

<v-click>
<div class="verdict">
<span class="verdict-label">Verdict:</span> <em>Hardware reality, yes. Coverage, never. Two tools, not one.</em> <mdi-arrow-down-bold-circle class="ico-green inline-ico" />
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

# A JMH Benchmark

```java
@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Group)
public class BufferCapacityBenchmark {

    @Param({"64", "1024"}) int capacity;
    LamportBuffer<Integer> buffer;

    @Benchmark @Group("spsc") @GroupThreads(1)
    public void producer(Blackhole bh) { bh.consume(buffer.offer(42)); }

    @Benchmark @Group("spsc") @GroupThreads(1)
    public void consumer(Blackhole bh) { bh.consume(buffer.poll()); }
}
```

<div class="subtle-note">
<code>Blackhole</code> stops the JIT deleting "dead" work; warmup and forks stop you measuring a cold JVM.
Without them you are measuring nothing — and accurate numbers for broken code are worse than no numbers.
</div>

---

# Lock-Free vs. Lock-Based

**1 producer + 1 consumer — same topology, two implementations:**

```
Benchmark                             (capacity)  (implementation)   Mode  Cnt         Score         Error  Units
JmhBenchmark.applesToApples                   64          VOLATILE  thrpt   10  12806160,712 ±  138235,768  ops/s
JmhBenchmark.applesToApples                   64              LOCK  thrpt   10   3119315,813 ±  205707,745  ops/s
```

<v-click>
<div class="callout green big-callout">
<mdi-rocket class="ico-green" />&nbsp;
<strong>Lock-free wins on this box</strong>&nbsp;— treat the ratio as an order of magnitude, not a promise.
</div>
</v-click>

<v-click>
<div class="callout orange">
<mdi-alert class="ico-orange" />&nbsp;
And&nbsp;<em>only</em>&nbsp;for SPSC: add a second producer and it silently loses writes.
</div>
</v-click>

---

# Circle IV Closes — JMH

<div class="vs-table">

| <mdi-arm-flex class="ico-green inline-ico" /> Strengths | <mdi-eye-off-outline class="ico-red inline-ico" /> Blind spots |
|---|---|
| **Stops the JIT lying.** `Blackhole` keeps "dead" work alive — no dead-code elimination, no loop hoisting | **Needs a repeatable environment.** Numbers compare only within one setup — same CPU, same memory, nothing else running |
| **Warmup and fork isolation** — steady-state numbers, not a cold JVM | **Slow.** The longest run in the deck — warmup × iterations × forks × params |

</div>

<v-click>
<div class="verdict">
<span class="verdict-label">Verdict:</span> <em>Measure last — and only what you have already proven correct.</em> <mdi-scale-balance class="ico-green inline-ico" />
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

# What Each Circle Costs <mdi-timer-outline class="ico-green inline-ico" />

<div class="slide-subtitle">Absolute times are a property of <em>your</em> suite on <em>your</em> machine. What is stable is <strong>what drives them</strong>.</div>

| Layer | Cost grows with | Local numbers | Where it belongs |
|---|---|---|---|
| <mdi-test-tube class="ico-blue inline-ico" /> **JUnit** | number of tests | 0.34 s | every change |
| <mdi-graph-outline class="ico-purple inline-ico" /> **Fray** | tests × **iterations** | 5.4 s | every PR |
| <mdi-pulse class="ico-orange inline-ico" /> **jcstress** | tests × **configurations** × time | 4 m 21 s | PR while small, nightly later |
| <mdi-speedometer class="ico-green inline-ico" /> **JMH** | params × **forks** × iterations | 13 m 25 s | release, on demand |

<div class="subtle-note">
Local numbers: one run each, one laptop. An example of the order of magnitude, nothing more.
</div>

<v-click>
<div class="callout orange">
<mdi-alert class="ico-orange" />&nbsp;
The two probabilistic tools have&nbsp;<strong>no natural stopping point</strong>&nbsp;— you decide how long to look,
and a shorter run is a weaker claim, not a faster test.
</div>
</v-click>

---

# Three Golden Rules

<v-clicks>
<div class="rule">
  <h2><mdi-numeric-1-circle class="ico-blue inline-ico" /> Never trust green unit tests as a concurrency proof</h2>
  <p>A single-threaded test cannot trigger a race. All four implementations passed; two were broken.</p>
</div>
<div class="rule">
  <h2><mdi-numeric-2-circle class="ico-purple inline-ico" /> Fray and jcstress are complementary — not interchangeable</h2>
  <p>jcstress finds JMM bugs Fray <em>structurally</em> cannot see. Fray finds races without being told their shape. <strong>Run both.</strong></p>
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

<br />
<br />

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

<div class="thanks-row">
<div class="thanks-main">

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
<br /> by Wiktor Sztajerowski
</span>

</div>

<div class="qr-block">
  <img src="/src/resources/poll-qr.png" alt="QR code linking to the post-talk feedback poll" />
  <span class="qr-caption">Feedback poll <mdi-clipboard-text-outline class="ico-green inline-ico" /></span>
</div>
</div>

---
layout: section
---

<div class="section-eyebrow"><mdi-bookmark-multiple /> Appendix</div>

# Appendix
## *The slides that did not make the cut. Ask, and we'll jump to one.*

---

# When to Reach for Which Tool

| Symptom / Goal | Reach for |
|---|---|
| <mdi-check-circle class="ico-blue inline-ico" /> Functional correctness & regression safety | **JUnit** |
| <mdi-shuffle-variant class="ico-purple inline-ico" /> Scheduling bugs (races, deadlocks, ordering) | **Fray** |
| <mdi-chip class="ico-orange inline-ico" /> JMM bugs (visibility, reordering, atomicity) | **jcstress** |
| <mdi-speedometer class="ico-green inline-ico" /> Throughput / latency / perf regressions | **JMH** |

---
layout: two-cols
---

# What Fray Finds — Spurious Wakeup

<div class="slide-subtitle">A fifth buffer — off the interface on purpose. It blocks:
<code>take()</code> / <code>put()</code>, not <code>offer()</code> / <code>poll()</code>.
Different contract, so it never sat the four-implementation exam.</div>

```java
public E take() {
  lock.lock();
  try {
    if (buffer[readPos] == null) { // ← if, not while!
      notEmpty.await();
    }
    E elem = buffer[readPos]; // null on spurious wakeup
    // ...
    return elem;              // ← silently null
  } finally { lock.unlock(); }
}
```

::right::

<v-click>

**The schedule Fray triggers:**

```
await() returns early 👻
  → slot still empty
  → take() returns null
  → 💥 null leaks to the consumer
```
</v-click>

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
