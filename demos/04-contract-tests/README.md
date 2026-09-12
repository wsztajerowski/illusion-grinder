# 04-contract-tests — Circle I: "Look mom, no errors!"

One JUnit 5 test class, one set of behavioural assertions, run against **all
four** `LamportBuffer` implementations through `@ParameterizedTest` +
`@MethodSource`.

```java
private static Stream<Implementation> implementations() {
    return Stream.of(
        new Implementation("01 volatile-based", VolatileLamportBuffer::createBuffer),
        new Implementation("02 single-thread",  NonVolatileLamportBuffer::createBuffer),
        new Implementation("03 lock-based",     LockBasedLamportBuffer::createBuffer),
        new Implementation("04 fast-path",      FastPathLamportBuffer::createBuffer)
    );
}
```

17 test methods × 4 implementations = **68 green test cases**.

## What is covered

Empty/full transitions, FIFO ordering, interleaved produce/consume, effective
capacity, wrap-around (single round, multiple rounds, partial), minimum capacity
(1), `size()` accuracy across every transition, null rejection, and invalid
capacity rejection. Assertions use the fluent custom AssertJ API from
[`00-lamport`](../00-lamport):

```java
assertThat(sut)
    .offers(42)
    .pollsValue(42)
    .isEmptyBuffer();
```

## The point of this module

**All 68 pass. Two of the four implementations are badly broken.**

That is the false-confidence baseline the rest of the repository dismantles.
A single-threaded test cannot construct an interleaving, cannot delay a write,
and cannot force a spurious wakeup — so it cannot see a race, a visibility bug,
or a missing re-check. It verifies the *algorithm*, nothing more.

| Implementation | Contract tests | Actually correct? |
|---|---|---|
| `VolatileLamportBuffer` | pass | only for SPSC |
| `NonVolatileLamportBuffer` | pass | **no** — no memory visibility |
| `LockBasedLamportBuffer` | pass | yes |
| `FastPathLamportBuffer` | pass | **no** — unlocked fast path |

## Run

```bash
# All 68 cases
mvn -f demos/pom.xml -pl 04-contract-tests -am test

# One test method, across all four implementations
mvn -f demos/pom.xml -pl 04-contract-tests -am test -Dtest='LamportBufferContractTest#wrapsAroundCorrectly' -Dsurefire.failIfNoSpecifiedTests=false

# Per-case detail (one line per implementation) after a run
cat demos/04-contract-tests/target/surefire-reports/*ContractTest.txt
```

Expected result:

```
Tests run: 68, Failures: 0, Errors: 0, Skipped: 0
```

Next circle: [`05-fray`](../05-fray).
