# 00-lamport — shared interface & test vocabulary

The foundation module. Contains no implementation and no logic worth testing —
only the contract that every other module is written against, plus the custom
AssertJ assertions that make the higher-level tests readable.

## What is here

### `LamportBuffer<E>` (`src/main/java`)

The four-method contract implemented by every buffer variant in this repository:

```java
public interface LamportBuffer<E> {
    boolean     offer(E element);   // false when full
    Optional<E> poll();             // empty when empty
    boolean     isEmpty();
    int         size();
}
```

Implementations are created through a static factory
(`createBuffer(Class<T> elementType, int capacity)`) rather than a constructor,
because the backing array is allocated reflectively via `Array.newInstance`.

### Custom assertions (`src/test/java`)

`LamportAssertions.assertThat(buffer)` returns a fluent `LamportBufferAssert`:

| Assertion | Checks |
|---|---|
| `isEmptyBuffer()` | `isEmpty()` returns `true` |
| `hasSize(n)` | `size()` equals `n` |
| `offers(e)` | `offer(e)` returned `true` |
| `rejectsOffer(e)` | `offer(e)` returned `false` (buffer full) |
| `pollsValue(e)` | `poll()` returned exactly `e` |
| `pollsEmpty()` | `poll()` returned `Optional.empty()` |

All assertions return `this`, so a whole scenario reads as one chain. This is
what keeps `04-contract-tests` free of boilerplate.

## Why it is published as a test-jar

`maven-jar-plugin` is bound to the `test-jar` goal, so the assertions ship as
`00-lamport-1.0-SNAPSHOT-tests.jar`. Modules `01`–`04` depend on it with
`<type>test-jar</type><classifier>tests</classifier>` and reuse the same
assertion vocabulary instead of redefining it.

Consequence: **this module must be installed into the local repository** before
the downstream modules can resolve the test-jar. Either build from the reactor
root, or pass `-am` when building a single module.

## Build and test

```bash
# Install the jar + test-jar into ~/.m2 (required by modules 01-04)
mvn -f demos/pom.xml -pl 00-lamport install

# Compile only
mvn -f demos/pom.xml -pl 00-lamport test
```
