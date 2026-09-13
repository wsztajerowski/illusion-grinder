package pl.wsztajerowski.demo.lamport.jcstress.passing;

import org.openjdk.jcstress.annotations.Actor;
import org.openjdk.jcstress.annotations.JCStressTest;
import org.openjdk.jcstress.annotations.Outcome;
import org.openjdk.jcstress.annotations.State;
import org.openjdk.jcstress.infra.results.II_Result;
import pl.wsztajerowski.demo.lamport.LamportBuffer;
import pl.wsztajerowski.demo.lamport.singlethread.NonVolatileLamportBuffer;

import static org.openjdk.jcstress.annotations.Expect.ACCEPTABLE;
import static org.openjdk.jcstress.annotations.Expect.ACCEPTABLE_INTERESTING;
import static org.openjdk.jcstress.annotations.Expect.FORBIDDEN;

/**
 * Multi-operation test to surface visibility and ordering bugs in
 * SingleThreadLamportBuffer (non-volatile fields).
 *
 * The producer offers two values (1, then 2) into a buffer of capacity 4.
 * The consumer polls twice.  Because the buffer is FIFO, the only valid
 * observation of two values is (1, 2) — any other pair indicates a
 * reordering, duplicate-read, or lost-write caused by missing volatile.
 *
 * Interesting anomalies (all caused by non-volatile fields):
 *   (-1, 1)  — first poll saw stale null, second poll re-read slot 0
 *   (1, 1)   — readPosition update not visible → duplicate read of slot 0
 *   (-1, 2)  — first write invisible, second visible
 *   (2, 1)   — FIFO order violated
 *   (2, -1)  — only second value visible
 */
@JCStressTest
@Outcome(id = "1, 2",  expect = ACCEPTABLE,
        desc = "Consumer saw both values in FIFO order.")
@Outcome(id = "1, -1", expect = ACCEPTABLE,
        desc = "Consumer saw only the first value; second offer may not have run yet.")
@Outcome(id = "-1, -1", expect = ACCEPTABLE,
        desc = "Consumer ran entirely before producer.")
@Outcome(id = "-1, 1", expect = ACCEPTABLE_INTERESTING,
        desc = "First poll saw stale null, second poll picked it up — " +
               "write visibility delayed due to non-volatile array / position fields.")
@Outcome(id = "1, 1",  expect = ACCEPTABLE_INTERESTING,
        desc = "Duplicate read! readPosition update not visible to consumer's " +
               "own subsequent read — non-volatile field reordering.")
@Outcome(id = "-1, 2", expect = ACCEPTABLE_INTERESTING,
        desc = "First write invisible but second is — store reordering or " +
               "partial visibility of non-volatile fields.")
@Outcome(id = "2, 1",  expect = ACCEPTABLE_INTERESTING,
        desc = "FIFO violation — values observed out of offer order.")
@Outcome(id = "2, -1", expect = ACCEPTABLE_INTERESTING,
        desc = "Only second value visible — first write lost or invisible.")
@Outcome(id = "2, 2",  expect = ACCEPTABLE_INTERESTING,
        desc = "Duplicate read of second value — severe ordering anomaly.")
@Outcome(id = ".*",    expect = FORBIDDEN,
        desc = "Unexpected value — data corruption.")
@State
public class SingleThreadLamportBufferMultiOpStress {

    private final LamportBuffer<Integer> buffer =
            NonVolatileLamportBuffer.createBuffer(Integer.class, 4);

    @Actor
    public void producer() {
        buffer.offer(1);
        buffer.offer(2);
    }

    @Actor
    public void consumer(II_Result r) {
        r.r1 = buffer.poll().orElse(-1);
        r.r2 = buffer.poll().orElse(-1);
    }
}

