# 03-lamport-lock — lock-based variants (one correct, two broken)

Three `ReentrantLock`-based buffers. One is the reference MPMC implementation;
the other two are deliberate bugs, each chosen to be caught by a *different*
tool later in the pipeline.

## `LockBasedLamportBuffer<E>` — correct MPMC

Every public method (`offer`, `poll`, `isEmpty`, `size`) takes the lock for its
entire body. Safe for any number of producers and consumers.

```java
public Optional<E> poll() {
    lock.lock();
    try {
        if (buffer[readPosition] == null) return Optional.empty();
        ...
    } finally { lock.unlock(); }
}
```

Correct — but *individually* correct. `05-fray`'s `TocTouCompoundActionFrayTest`
uses this class to show that thread-safe objects do not compose into
thread-safe operations: the client-side pattern

```java
if (!buffer.isEmpty()) consumed.add(buffer.poll().get());
```

is a TOCTOU race, because another consumer can drain the buffer between the two
individually-atomic calls, and `.get()` then throws `NoSuchElementException`.

## `FastPathLamportBuffer<E>` — the "optimised" one

Checks emptiness **outside** the lock as a fast-path early return, then fails to
re-verify after acquiring it:

```java
public Optional<E> poll() {
    if (buffer[readPosition] == null) return Optional.empty();   // no lock held
    lock.lock();
    try {
        E elem = buffer[readPosition];   // may be null now
        ...
        return Optional.of(elem);        // NullPointerException
    } finally { lock.unlock(); }
}
```

Two consumers both pass the unlocked check, then serialize on the lock. The
first consumes the element and advances `readPosition`; the second reads a null
slot and hands it to `Optional.of()`. Caught by
[`FastPathLamportBufferFrayTest`](../05-fray) — a pure *scheduling* bug, so Fray
finds it and can replay the exact interleaving.

## `ConditionalLamportBuffer<E>` — `if` instead of `while`

A blocking queue (`put`/`take`, no `LamportBuffer` interface — it never returns
"full"/"empty", it waits) guarded by two `Condition`s:

```java
if (buffer[readPosition] == null) {   // BUG: must be while
    notEmpty.await();
}
```

`Condition.await()` is permitted by the specification to return without a
matching `signal()` — a *spurious wakeup*. With `if`, the guard is not
re-checked after waking, so `take()` reads an empty slot and `put()` overwrites
a full one. Fray models spurious wakeup as a legal scheduling choice and finds
this; ordinary test runs essentially never will. See
[`ConditionalLamportBufferFrayTest`](../05-fray) and
`SpuriousWakeupLamportBufferStress` in [`06-jcstress`](../06-jcstress).

## Summary

| Class | Implements `LamportBuffer` | Status | Found by |
|---|---|---|---|
| `LockBasedLamportBuffer` | yes | correct (composition still unsafe) | — |
| `FastPathLamportBuffer` | yes | **broken** — unlocked fast path, no re-check | Fray |
| `ConditionalLamportBuffer` | no (`put`/`take`) | **broken** — `if` instead of `while` around `await()` | Fray |

## Build and test

```bash
# Module tests only
mvn -f demos/pom.xml -pl 03-lamport-lock -am test

# Single test class
mvn -f demos/pom.xml -pl 03-lamport-lock -am test -Dtest=LockBasedLamportBufferTest -Dsurefire.failIfNoSpecifiedTests=false
```

As with the other implementation modules, the local JUnit tests pass for all
three classes. The bugs only surface in [`05-fray`](../05-fray) and
[`06-jcstress`](../06-jcstress).
