package pl.wsztajerowski.demo.lamport.jcstress.edgecase;

import org.openjdk.jcstress.annotations.*;
import org.openjdk.jcstress.infra.results.III_Result;
import pl.wsztajerowski.demo.lamport.LamportBuffer;
import pl.wsztajerowski.demo.lamport.singlethread.NonVolatileLamportBuffer;

import java.util.Optional;

/*
    This test shows value lost entirely due to lack of visibility guarantees in the non-volatile buffer.
    The producer offers an element, but the consumer fails to see it and polls an empty buffer instead.
    The arbiter then polls the buffer again, and also fails to see the element, confirming that it was lost
    rather than just observed by the consumer before the producer's offer.
 */
@JCStressTest
@Outcome(id = "1, 1, 0", expect = Expect.ACCEPTABLE, desc = "Producer offers the element, then consumer polls it")
@Outcome(id = "1, 0, 1", expect = Expect.ACCEPTABLE, desc = "Consumer polls the element, before producer offers")
@Outcome(id = "0, *", expect = Expect.ACCEPTABLE_INTERESTING, desc = "Producer failed to offer the element - possible bug in buffer")
@Outcome(id = "1, 0, 0", expect = Expect.FORBIDDEN, desc = "Producer offers the element, but consumer fails to poll it - visibility bug")
@State
public class NonVolatileSpscLamportBufferJcstressTest {

    private final LamportBuffer<Integer> queue =
        NonVolatileLamportBuffer.createBuffer(Integer.class, 2);

    @Actor
    public void producer(III_Result r) {
        r.r1 = queue.offer(1) ? 1 : 0;
    }

    @Actor
    public void consumer(III_Result r) {
        Optional<Integer> v = queue.poll();
        r.r2 = v.orElse(0);
    }

    @Arbiter
    public void arbiter(III_Result r) {
        Optional<Integer> v = queue.poll();
        r.r3 = v.orElse(0);
    }
}