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

# The Algorithm

<mdi-account-tie class="ico-blue inline-ico" /> **Leslie Lamport's wait-free SPSC queue (1983)** — two cursors, **no locks, no CAS** <mdi-flash class="ico-yellow inline-ico" />

<div class="two-col-code">

<v-click>

<pre class="buffer-viz">
  capacity = 4

   ┌─────┬─────┬─────┬─────┐
   │  A  │  B  │     │     │
   └─────┴─────┴─────┴─────┘
      ↑           ↑
   readPos    writePos
   (consumer)  (producer)
</pre>

</v-click>

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

</div>

<v-click>
<div class="callout purple">
<mdi-magnify /> &nbsp;<code>offer()</code> is the mirror image. Spot the bug? Neither did I — neither did my unit tests.
</div>
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

# The False Negative <mdi-emoticon-confused class="ico-yellow inline-ico" />

Run the full suite against all four implementations — including the deliberately broken ones:

```
[INFO] Running LamportBufferContractTest
[INFO] Tests run: 68, Failures: 0, Errors: 0, Skipped: 0

[INFO] BUILD SUCCESS
```

<div class="callout green">
<mdi-party-popper class="ico-green" />&nbsp; All green. Time to deploy. What could possibly go wrong?
</div>

<v-click>
<div class="callout red">
<mdi-skull class="ico-red" />&nbsp; Two implementations are broken. Your CI just&nbsp; <em>lied to your face</em>.
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
<mdi-lightbulb /> &nbsp;Write contract tests first. Keep them always.<br>&nbsp;But do not mistake a green suite for a concurrency correctness proof.
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
- <mdi-infinity class="ico-purple inline-ico" /> &nbsp;Every run explores a **different interleaving** — sampled, with probabilistic guarantees of finding bugs
- <mdi-dice-multiple class="ico-purple inline-ico" /> &nbsp;No reliance on the OS scheduler *"getting lucky"*

</v-clicks>

---

# Every Run — A Different Schedule

```
Run #1:     T1 → T1 → T2 → T1 → T2
Run #2:     T2 → T1 → T2 → T2 → T1
Run #3:     T1 → T2 → T2 → T1 → T1
...
Run #1000:  a new sample, every iteration
```

`@ConcurrencyTest` runs **1000 iterations by default** — a thousand different schedules of the same test body. Two main strategies:

<v-clicks>

- <mdi-shuffle-variant class="ico-purple inline-ico" /> &nbsp;**POS** *(Partial Order Sampling)* — the default: picks the next thread at random, re-rolling only the threads that actually race, so equivalent schedules are not explored twice
- <mdi-sort-numeric-variant class="ico-purple inline-ico" /> &nbsp;**PCT** *(Probabilistic Concurrency Testing)* — random thread priorities plus a few random preemption points, with a provable lower bound on catching a bug of a given depth

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
  assertThat(results).containsExactlyInAnyOrder(
    Optional.of(1), Optional.of(2));
}
```

<div class="callout purple">
<mdi-magic-staff class="ico-purple" />&nbsp;
Looks like an ordinary JUnit test. Fray hijacks the scheduler underneath.
</div>

<v-clicks>
<div class="subtle-note">
Switch with <code>@ConcurrencyTest(scheduler = PCTScheduler.class)</code> — <code>POSScheduler</code> is the default.
</div>
</v-clicks>

---
layout: two-cols
---

# What Fray Finds — Unlocked Fast-path

<div class="slide-subtitle">Enter <code>FastPathLamportBuffer</code> — the optimisation we promised in Circle I.</div>

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

::right::

<v-click>

**The interleaving Fray constructs:** <mdi-format-list-numbered class="ico-purple inline-ico" />

```
T1: passes null-check      (slot has element)
T2: passes null-check      (slot still has element)
T1: acquires lock, reads, advances readPosition
T2: acquires lock, reads... null → 💥 NPE
```
</v-click>

<v-click>
<div class="callout purple">
<mdi-alert-octagon class="ico-purple" />&nbsp;
Check-then-act without a lock is a race.<br>
<strong>This is the exception we opened with.</strong> The optimisation that wasn't.
</div>
</v-click>

<v-click>

```java
// ❌ FastPathLamportBuffer — check, then lock
if (buffer[readPosition] == null)
  return Optional.empty();
lock.lock();

// ✅ LockBasedLamportBuffer — lock, then check
lock.lock();
if (buffer[readPosition] == null)
  return Optional.empty();
```

</v-click>

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
hide: true
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

# Why Fray Cannot See This

<v-clicks>

- <mdi-controller /> &nbsp;Fray controls **thread scheduling** — not CPU caches or JIT optimisations
- <mdi-memory /> &nbsp;The bug is a **memory visibility** failure — the write happens, the other thread never sees it — not a scheduling failure
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

<div class="slide-subtitle">Before we look at the result — remember why missing <code>volatile</code> matters.</div>

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

# Why jcstress Cannot Promise Coverage <mdi-dice-multiple class="ico-yellow inline-ico" />

- <mdi-controller /> &nbsp;Fray controls exactly **one** thing — which thread runs next. Not the bytecode, not memory operations
- <mdi-dice-6 /> &nbsp;jcstress controls **nothing**: same code, millions of runs, watch what the OS / JIT / CPU happen to do
- <mdi-percent /> &nbsp;The visibility bug surfaced **35,222 times in 655 million** runs — 0.005%
- <mdi-timer-sand /> &nbsp;Shorten the run and that row **disappears**. Same code. Green report.


<v-click>
<div class="callout orange">

**jcstress proves:** "This outcome really happens on real hardware." <mdi-check class="ico-green inline-ico" /><br>
**jcstress cannot prove:** "That outcome never happens." <mdi-close class="ico-red inline-ico" />

</div>
</v-click>

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
Run with <code>-f 2 -wi 5 -i 5</code> — the CLI overrides <code>@Fork(1)</code>, hence <code>Cnt 10</code> overleaf.
<code>Control.stopMeasurement</code> is what stops a spinning benchmark from hanging at the end of an iteration.
</div>

---

# Lock-Free vs. Lock-Based

**1 producer + 1 consumer — same topology, two implementations:**

```
Benchmark                             (capacity)  (implementation)   Mode  Cnt         Score         Error  Units
JmhBenchmark.applesToApples                   64          VOLATILE  thrpt   10  12806160,712 ±  138235,768  ops/s
JmhBenchmark.applesToApples:consumer          64          VOLATILE  thrpt   10   6388150,897 ±   69540,893  ops/s
JmhBenchmark.applesToApples:producer          64          VOLATILE  thrpt   10   6418009,816 ±   68750,521  ops/s
JmhBenchmark.applesToApples                   64              LOCK  thrpt   10   3119315,813 ±  205707,745  ops/s
JmhBenchmark.applesToApples:consumer          64              LOCK  thrpt   10   1559657,794 ±  102857,340  ops/s
JmhBenchmark.applesToApples:producer          64              LOCK  thrpt   10   1559658,019 ±  102850,405  ops/s
JmhBenchmark.applesToApples                 1024          VOLATILE  thrpt   10  14877651,416 ± 1379752,921  ops/s
JmhBenchmark.applesToApples                 1024              LOCK  thrpt   10   5413892,148 ±  908797,769  ops/s
```

<v-click>
<div class="callout green big-callout">
<mdi-rocket class="ico-green" />&nbsp;
Lock-free volatile:&nbsp;<strong>3–4× higher throughput</strong>&nbsp; — for SPSC.
And&nbsp;<em>only</em>&nbsp;for SPSC: add a second producer and it silently loses&nbsp;<strong>~1.5%</strong>&nbsp;of writes.
</div>
</v-click>

<v-click>
<div class="verdict">
<span class="verdict-label">Verdict:</span> <em>One topology, one machine, one JDK — for code you had already proven correct. </em>
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
hide: true
---

# When to Reach for Which Tool

| Symptom / Goal | Reach for |
|---|---|
| <mdi-check-circle class="ico-blue inline-ico" /> Functional correctness & regression safety | **JUnit** |
| <mdi-shuffle-variant class="ico-purple inline-ico" /> Scheduling bugs (races, deadlocks, ordering) | **Fray** |
| <mdi-chip class="ico-orange inline-ico" /> JMM bugs (visibility, reordering, atomicity) | **jcstress** |
| <mdi-speedometer class="ico-green inline-ico" /> Throughput / latency / perf regressions | **JMH** |

---

# What Each Circle Costs <mdi-timer-outline class="ico-green inline-ico" />

<div class="slide-subtitle">Absolute times are a property of <em>your</em> suite on <em>your</em> machine. What is stable is <strong>what drives them</strong>.</div>

| Layer | Cost grows with | Where it belongs |
|---|---|---|
| <mdi-test-tube class="ico-blue inline-ico" /> **JUnit** | number of tests | every save |
| <mdi-graph-outline class="ico-purple inline-ico" /> **Fray** | tests × **iterations** (1000 by default) | every PR |
| <mdi-pulse class="ico-orange inline-ico" /> **jcstress** | tests × **configurations** × time each — and configurations multiply | every PR while small, nightly as it grows |
| <mdi-speedometer class="ico-green inline-ico" /> **JMH** | params × **forks** × iterations × time | release, on a quiet machine |

---

# What Each Circle Costs <mdi-timer-outline class="ico-green inline-ico" />

<div class="subtle-note">
This repo, one laptop: 0.34 s · 5.4 s · 4 m 21 s · 13 m 25 s — one run each, JMH at 2 forks for one of three
benchmark classes. An anchor, not a benchmark.
</div>

<v-click>
<div class="callout orange">
<mdi-alert class="ico-orange" />&nbsp;
The two probabilistic tools have&nbsp;<strong>no natural stopping point</strong>&nbsp;You decide how long to look —
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
</span>

</div>

<div class="qr-block">
  <img src="/src/resources/poll-qr.png" alt="QR code linking to the post-talk feedback poll" />
  <span class="qr-caption">Feedback poll <mdi-clipboard-text-outline class="ico-green inline-ico" /></span>
</div>

</div>
