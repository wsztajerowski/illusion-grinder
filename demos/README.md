# demos — the multi-module Maven project

Java 25 · Maven 3.9+ · eight modules, numbered in the order the presentation
walks through them.

```
00-lamport               interface + custom AssertJ assertions (published as a test-jar)
├── 01-lamport-volatile      VolatileLamportBuffer     — SPSC, volatile cursors
├── 02-lamport-single-thread NonVolatileLamportBuffer  — same code, volatile removed
└── 03-lamport-lock          LockBasedLamportBuffer    — MPMC, ReentrantLock
                             FastPathLamportBuffer     — unlocked fast path (broken)
                             ConditionalLamportBuffer  — if-instead-of-while (broken)

04-contract-tests        JUnit 5    — 17 tests × 4 implementations, all green
05-fray                  Fray       — systematic interleaving exploration
06-jcstress              jcstress   — probabilistic stress on real hardware
07-jmh                   JMH        — throughput measurement
```

Each module has its own README with the detail and the commands for that layer.

## Common commands

```bash
# Full build + install into ~/.m2 (skips both the unit tests and the jcstress run)
mvn -f demos/pom.xml clean install -DskipTests -Dexec.skip=true

# All unit tests across the reactor (includes 05-fray — see the warning below)
mvn -f demos/pom.xml test

# Just the fast layers
mvn -f demos/pom.xml -pl 00-lamport,01-lamport-volatile,02-lamport-single-thread,03-lamport-lock,04-contract-tests test

# A single module (-am also builds the modules it depends on)
mvn -f demos/pom.xml -pl 04-contract-tests -am test
mvn -f demos/pom.xml -pl 05-fray           -am test
mvn -f demos/pom.xml -pl 06-jcstress       -am verify   # verify, not test
mvn -f demos/pom.xml -pl 07-jmh            -am package  # then run the jar

# A single test class or method
mvn -f demos/pom.xml -pl 04-contract-tests -am test -Dtest=LamportBufferContractTest -Dsurefire.failIfNoSpecifiedTests=false
mvn -f demos/pom.xml -pl 05-fray -am test -Dtest='VolatileLamportBufferFrayTest#fifoOrderUnderConcurrency' -Dsurefire.failIfNoSpecifiedTests=false
```

> `-Dsurefire.failIfNoSpecifiedTests=false` is needed alongside `-Dtest=` whenever
> `-am` is present: the filter is applied to every module in the reactor, and the
> upstream modules have no matching test, which would otherwise fail the build.

> **Always pass `-Dexec.skip=true` to a plain `install`** unless you want the
> full jcstress suite to run. `-DskipTests` only skips Surefire;
> `exec-maven-plugin` is bound to `integration-test` in `06-jcstress` and runs
> regardless, which turns a 30-second build into a 30-minute one.

> **`mvn test` at the reactor root includes `05-fray`.** Fray builds an
> instrumented ~186 MB JDK image into `05-fray/target/fray/fray-java` on a first
> run, then replays every `@ConcurrencyTest` 1000 times. Budget tens of minutes,
> or use `-pl` to pick the modules you actually need.

## Dependency wiring worth knowing

* `00-lamport` publishes a **test-jar** (`maven-jar-plugin`, `test-jar` goal).
  Modules `01`–`04` consume it with `<type>test-jar</type><classifier>tests</classifier>`
  to share the fluent assertions. This is why building a single module needs
  `-am`, or a prior `install` of `00-lamport`.
* `05-fray` depends on `01`, `02`, `03` at **test** scope and wires the Fray JVM
  agent through `fray-plugins-maven` (`prepare-fray`, `initialize` phase).
* `06-jcstress` keeps its tests in `src/main/java` — jcstress' annotation
  processor generates the runner infrastructure at compile time, and
  `maven-shade-plugin` packages `target/jcstress.jar`.
* `07-jmh` does the same with `target/benchmarks.jar` and JMH's annotation
  processor.

## Versions

| Dependency | Version | Set in |
|---|---|---|
| Java (source/target) | 25 | `pom.xml` properties |
| JUnit Jupiter | 5.12.1 | `junit.jupiter.version` |
| AssertJ | 3.27.3 | `assertj.version` |
| Fray | 0.8.5 | `fray.version` |
| jcstress | 0.16 | `jcstress.version` |
| JMH | 1.37 | `jmh.version` |

## Recorded results

`results/` holds output copied out of the (git-ignored) build directories so it
survives a `clean` and can be referenced from the slides:

| Path | Contents |
|---|---|
| `results/fray/fray-report/` | recorded schedules, replayed by `@ConcurrencyTest(replay = ...)` |
| `results/jcstress/results/` | HTML report from a full jcstress run |
| `results/jmh/jmh-output.json` | JMH throughput numbers |
