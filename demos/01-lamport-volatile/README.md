# 01-lamport-volatile — the SPSC implementation

`VolatileLamportBuffer<E>` — Lamport's circular buffer with `volatile` read and
write positions. **Correct for exactly one producer and one consumer.**

## The implementation

```java
private volatile int readPosition;
private volatile int writePosition;
private final E[] buffer;          // plain array, no volatile elements
```

* `offer()` writes the element, then advances `writePosition`. The `volatile`
  write publishes the element store that precedes it (happens-before edge).
* `poll()` reads `readPosition`/`writePosition`, takes the element, nulls the
  slot, then advances `readPosition`.
* `size()` derives the count from the two positions, disambiguating the
  "positions equal" case (empty vs. full) by inspecting `buffer[readPosition]`.

The only synchronisation is the `volatile` on the two cursors — there is no
lock and no CAS. That is what makes it fast (see `07-jmh`: roughly 3× the
throughput of the lock-based variant for an SPSC workload).

## Where it holds and where it breaks

| Scenario | Verdict |
|---|---|
| 1 producer + 1 consumer | Correct — each cursor has a single writer |
| 2+ producers | **Broken** — `isFull()`/`offer()` is a check-then-act on `writePosition`; two producers can both pass the check and the second overwrites the first |
| 2+ consumers | **Broken** — same check-then-act on `readPosition` |

The multi-producer failure is reproduced empirically in
`06-jcstress` → `TwoProducersVolatileBufferStress`, where both `offer()` calls
return `true` in the overwhelming majority of samples yet only one element
survives.

This is the reason `VolatileLamportBufferFrayTest` in `05-fray` extends only
`AbstractSPSCLamportBufferFrayTest` and not the MPMC variant: the MPMC tests
are not applicable to an SPSC data structure.

## Build and test

```bash
# Module tests only (-am also builds 00-lamport, needed for the test-jar)
mvn -f demos/pom.xml -pl 01-lamport-volatile -am test

# Single test class
mvn -f demos/pom.xml -pl 01-lamport-volatile -am test -Dtest=VolatileLamportBufferTest -Dsurefire.failIfNoSpecifiedTests=false
```

The tests here are plain single-threaded JUnit — they pass, which is precisely
the point the presentation makes. The interesting verdicts come from
[`04-contract-tests`](../04-contract-tests), [`05-fray`](../05-fray) and
[`06-jcstress`](../06-jcstress).
