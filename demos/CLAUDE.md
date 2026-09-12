# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this directory.

## Project Overview

A multi-module Maven project (Java 25) that uses **Lamport's Circular Buffer** as a concrete example to demonstrate progressively more powerful concurrency testing techniques.

## Build & Test Commands

```bash
# Build all modules — -Dexec.skip=true is required to skip the jcstress run,
# which -DskipTests does NOT skip (it is bound to integration-test)
mvn -f pom.xml clean install -DskipTests -Dexec.skip=true

# Run all unit tests
mvn -f pom.xml test

# Run tests for a specific module (-am includes upstream dependencies)
mvn -f pom.xml -pl 04-contract-tests -am test
mvn -f pom.xml -pl 05-fray -am test
mvn -f pom.xml -pl 06-jcstress -am verify    # verify, not test
mvn -f pom.xml -pl 07-jmh -am package        # then run target/benchmarks.jar

# Fray report directory (default: <module>/build/fray/fray-report)
mvn -f pom.xml -pl 05-fray -am test -Dfray.workDir=target/fray-report
```

Fray's iteration count and scheduler are `@ConcurrencyTest` annotation attributes
(`iterations`, `scheduler`), **not** system properties. Defaults: 1000 iterations,
`POSScheduler`. The only Fray system properties in 0.8.5 are `fray.workDir`,
`fray.organize.by.test` and `fray.debugger`.

## Module Structure

### Buffer Implementations

Variants of the same data structure with different thread-safety characteristics:

| Module | Class | Thread Model |
|--------|-------|-------------|
| `01-lamport-volatile` | `VolatileLamportBuffer` | SPSC — single producer, single consumer; uses `volatile` |
| `02-lamport-single-thread` | `NonVolatileLamportBuffer` | No synchronization; `volatile` deliberately removed |
| `03-lamport-lock` | `LockBasedLamportBuffer` | MPMC — fully synchronized with `ReentrantLock` |
| `03-lamport-lock` | `FastPathLamportBuffer` | MPMC with an unlocked fast path and no re-check — **broken** (NPE) |
| `03-lamport-lock` | `ConditionalLamportBuffer` | Blocking `put`/`take`; `if` instead of `while` around `await()` — **broken** |

### Testing Layers

**`00-lamport`** — defines the `LamportBuffer<E>` interface and custom AssertJ assertions shared by all modules. Published as a test-jar consumed by downstream modules.

**`04-contract-tests`** — JUnit 5 parameterized tests that run the same 17 scenarios against all four `LamportBuffer` implementations via `@MethodSource` (68 cases). All of them pass, which is the point: single-threaded tests cannot see concurrency bugs.

**`05-fray`** — uses [Fray](https://github.com/cmu-pasta/fray) (v0.8.5) to *systematically* enumerate thread interleavings. Concrete test classes extend `AbstractSPSCLamportBufferFrayTest` or `AbstractMPMCLamportBufferFrayTest` and supply a buffer factory. Configured via the `fray-plugins-maven` Maven plugin (`prepare-fray`, `initialize` phase). Tests under `fray/edgecase/` are `@Disabled` intentionally-failing demos. Surefire sets `fray.organize.by.test=true` so recordings do not overwrite each other.

**`06-jcstress`** — uses OpenJDK [jcstress](https://openjdk.org/projects/code-tools/jcstress/) (v0.16) for probabilistic multi-threaded stress testing. Each `*Stress` class declares concurrent actors and expected outcomes with `@JCStressTest`/`@Actor`/`@Outcome`/`@Arbiter`. Tests live in `src/main/java` (jcstress' annotation processor generates the runner), are shaded into `target/jcstress.jar`, and are executed by `exec-maven-plugin` during `integration-test`.

**`07-jmh`** — throughput benchmarks (JMH 1.37) for `VolatileLamportBuffer` (SPSC), `LockBasedLamportBuffer` (MPMC), and an apples-to-apples comparison of both under an identical 1P/1C workload. Shaded into `target/benchmarks.jar`.

## Architecture

### Testing Pyramid for Concurrency

```
JMH              (performance measurement)
jcstress         (probabilistic stress testing against the real JMM)
Fray             (systematic interleaving coverage)
JUnit contracts  (functional correctness)
```

Each layer catches a class of bug the one below it structurally cannot. Fray explores *schedules*, which are all sequentially consistent — so it passes `NonVolatileLamportBuffer` and only jcstress catches that missing-`volatile` bug. Conversely, Fray finds the fast-path NPE and spurious-wakeup bugs by construction rather than by luck, and can replay the exact schedule. The buffers are the subjects being tested, not reference answers: two of the four are intentionally broken.

### Key Dependencies

- Fray: `fray-junit`, `fray-core`, `fray-runtime` (v0.8.5)
- jcstress: `jcstress-core` (v0.16)
- JMH: `jmh-core`, `jmh-generator-annprocess` (v1.37)
- JUnit Jupiter: 5.12.1
- AssertJ: 3.27.3
