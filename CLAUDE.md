# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a presentation project demonstrating systematic concurrency testing in Java using Lamport's Circular Buffer as a running example. It contains a multi-module Maven project (`demos/`) and a Slidev presentation (`slides/`).

## Build & Test Commands

### Maven (demos)

```bash
# Build all modules — ALWAYS pass -Dexec.skip=true unless you want the full
# jcstress suite to run (it is bound to integration-test and -DskipTests does
# NOT skip it; without the flag a build takes ~30 minutes instead of ~30s)
mvn -f demos/pom.xml clean install -DskipTests -Dexec.skip=true

# Run all unit tests
mvn -f demos/pom.xml test

# Run tests for a specific module (use -am to include dependencies)
mvn -f demos/pom.xml -pl 04-contract-tests -am test
mvn -f demos/pom.xml -pl 05-fray -am test
mvn -f demos/pom.xml -pl 06-jcstress -am verify    # verify, not test
mvn -f demos/pom.xml -pl 07-jmh -am package        # then run target/benchmarks.jar

# Fray report location (default: <module>/build/fray/fray-report)
mvn -f demos/pom.xml -pl 05-fray -am test -Dfray.workDir=target/fray-report
```

Fray iteration count and scheduler are **`@ConcurrencyTest` annotation attributes, not
system properties** — `@ConcurrencyTest(iterations = 5000, scheduler = PCTScheduler.class)`.
Defaults: 1000 iterations, `POSScheduler`. The only Fray system properties that exist in
0.8.5 are `fray.workDir`, `fray.organize.by.test` and `fray.debugger`.

### Slides (Slidev)

```bash
cd slides
npm install
npm run dev      # Development server
npm run build    # Build static site
npm run export   # Export to PDF
```

## Architecture

### Demo Modules (progressive complexity)

The modules build on each other; `00-lamport` exports a test-jar consumed by modules 01–04.

| Module | Purpose |
|--------|---------|
| `00-lamport` | `LamportBuffer<E>` interface + custom AssertJ assertions (exports test-jar) |
| `01-lamport-volatile` | `VolatileLamportBuffer` — SPSC implementation using `volatile` cursors |
| `02-lamport-single-thread` | `NonVolatileLamportBuffer` — same code with `volatile` removed (deliberately broken) |
| `03-lamport-lock` | `LockBasedLamportBuffer` (correct MPMC), `FastPathLamportBuffer` (unlocked fast path, broken), `ConditionalLamportBuffer` (`if` instead of `while` around `await()`, broken) |
| `04-contract-tests` | JUnit 5 parameterized tests running the same contract against all four `LamportBuffer` implementations |
| `05-fray` | Systematic concurrency testing via [Fray](https://github.com/cmu-pasta/fray) — explores thread interleavings |
| `06-jcstress` | Probabilistic stress testing via [jcstress](https://openjdk.org/projects/code-tools/jcstress/) using `@JCStressTest`/`@Actor`/`@Outcome` |
| `07-jmh` | Throughput benchmarks via [JMH](https://github.com/openjdk/jmh) |

### Testing Pyramid for Concurrency

1. **JUnit** (`04-contract-tests`) — functional correctness of the algorithm
2. **Fray** (`05-fray`) — systematic exploration of thread interleavings
3. **jcstress** (`06-jcstress`) — probabilistic stress testing against the real JMM
4. **JMH** (`07-jmh`) — performance measurement

Fray and jcstress are complementary: Fray explores *schedules* (all sequentially consistent, so it cannot see the missing-`volatile` bug in `02`), jcstress explores *hardware* (so it catches exactly that).

### Key Technical Details

- Java 25, Maven 3.9+
- Fray 0.8.5 (agent wired in by `fray-plugins-maven`, `prepare-fray` goal, `initialize` phase)
- jcstress 0.16 — tests live in `src/main/java`, shaded into `target/jcstress.jar`, run by `exec-maven-plugin` during `integration-test`
- JMH 1.37 — shaded into `target/benchmarks.jar`
- Contract tests in `04-contract-tests` use `@MethodSource` over four implementations: 17 methods × 4 = 68 cases, all passing
- Fray tests extend `AbstractSPSCLamportBufferFrayTest` or `AbstractMPMCLamportBufferFrayTest`; concrete subclasses supply a buffer factory. Tests under `fray/edgecase/` are `@Disabled` intentionally-failing demos
- `05-fray` sets `fray.organize.by.test=true` via Surefire so test recordings do not overwrite each other

### Documentation

Each module has a `README.md` covering what it demonstrates and the commands to run it; the root `README.md` is the entry point. `docs/abstract.md` holds the (Polish) talk abstract. `demos/results/` holds recorded Fray schedules, jcstress HTML reports and JMH JSON output copied out of git-ignored build directories.
