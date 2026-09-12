# 02-lamport-single-thread — the same buffer, without `volatile`

`NonVolatileLamportBuffer<E>` is a byte-for-byte copy of
[`VolatileLamportBuffer`](../01-lamport-volatile) with one modifier removed:

```java
private int readPosition;    // was: volatile
private int writePosition;   // was: volatile
```

Everything else — `offer()`, `poll()`, `size()`, the wrap-around arithmetic —
is identical. Two comments in the source mark the exact spots where the
happens-before edge used to be:

```java
writePosition = writePosition + 1; // missing volatile publication
```

## Why this module exists

It is the control sample for the whole talk. Removing `volatile` breaks **only**
the Java Memory Model guarantees — not the algorithm. So:

| Testing layer | Verdict | Why |
|---|---|---|
| JUnit ([`04-contract-tests`](../04-contract-tests)) | **passes** | A single thread always sees its own writes |
| Fray ([`05-fray`](../05-fray)) | **passes** | Fray explores thread *schedules*, and every schedule is sequentially consistent — it does not model store buffers or cache visibility |
| jcstress ([`06-jcstress`](../06-jcstress)) | **fails** | Real hardware + JIT reorder and delay the write; the consumer never sees the element |

That middle row is the interesting one: it is Fray's structural blind spot.
A tool that systematically enumerates interleavings still assumes each
interleaving executes under sequential consistency, so a pure visibility bug is
invisible to it. Only running on real silicon (jcstress) exposes it.

The `SingleThreadLamportBufferMultiOpStress` test in `06-jcstress` catalogues
the anomalies this produces: duplicate reads, lost writes, and FIFO order
violations.

## Build and test

```bash
# Module tests only
mvn -f demos/pom.xml -pl 02-lamport-single-thread -am test

# Single test class
mvn -f demos/pom.xml -pl 02-lamport-single-thread -am test -Dtest=NonVolatileLamportBufferTest -Dsurefire.failIfNoSpecifiedTests=false
```

To see it actually fail, run the jcstress layer:

```bash
mvn -f demos/pom.xml -pl 06-jcstress -am verify
```
