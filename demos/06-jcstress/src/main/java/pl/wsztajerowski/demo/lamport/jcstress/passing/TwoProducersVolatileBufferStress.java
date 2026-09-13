package pl.wsztajerowski.demo.lamport.jcstress.passing;

import org.openjdk.jcstress.annotations.Actor;
import org.openjdk.jcstress.annotations.Arbiter;
import org.openjdk.jcstress.annotations.JCStressTest;
import org.openjdk.jcstress.annotations.Outcome;
import org.openjdk.jcstress.annotations.State;
import org.openjdk.jcstress.infra.results.III_Result;
import pl.wsztajerowski.demo.lamport.LamportBuffer;
import pl.wsztajerowski.demo.lamport.spsc.VolatileLamportBuffer;

import static org.openjdk.jcstress.annotations.Expect.ACCEPTABLE;
import static org.openjdk.jcstress.annotations.Expect.ACCEPTABLE_INTERESTING;
import static org.openjdk.jcstress.annotations.Expect.FORBIDDEN;

/**
 * Two producers against a capacity-2 SPSC buffer — one more producer than the
 * algorithm allows.
 *
 * <p>Two slots and two producers means both {@code offer} calls <em>should</em> succeed
 * and both elements should be retrievable. The arbiter drains the buffer and reports how
 * many elements actually landed; that count is what separates a real lost update from a
 * correct interleaving. Without it, "both offers returned true" is indistinguishable
 * from correct behaviour.
 *
 * <p>{@code offer} can also throw: {@code writePosition} is incremented and then
 * wrap-reset in two separate statements, so a producer reading the cursor in between
 * indexes past the end of the array. That is captured as {@code -1} rather than being
 * allowed to abort the run.
 *
 * <p>Result triple is {@code (offer1, offer2, elementsStored)}.
 */
@JCStressTest
@Outcome(id = "1, 1, 2", expect = ACCEPTABLE,
        desc = "Both producers stored their element — two slots, two elements.")
@Outcome(id = "1, 1, 1", expect = ACCEPTABLE_INTERESTING,
        desc = "TOCTOU lost update: both offers returned true, only one element landed.")
@Outcome(id = {"1, 0, 1", "0, 1, 1"}, expect = ACCEPTABLE_INTERESTING,
        desc = "TOCTOU spurious full: size() saw a written slot before the cursor advanced, so a producer was rejected while a slot was free.")
@Outcome(id = ".*", expect = FORBIDDEN,
        desc = "Unexpected state — corruption beyond a lost update or a spurious rejection.")
@State
public class TwoProducersVolatileBufferStress {

    private final LamportBuffer<Integer> buffer = VolatileLamportBuffer.createBuffer(Integer.class, 2);

    @Actor
    public void producer1(III_Result r) {
        try {
            r.r1 = buffer.offer(1) ? 1 : 0;
        } catch (RuntimeException e) {
            r.r1 = -1;
        }
    }

    @Actor
    public void producer2(III_Result r) {
        try {
            r.r2 = buffer.offer(2) ? 1 : 0;
        } catch (RuntimeException e) {
            r.r2 = -1;
        }
    }

    @Arbiter
    public void drain(III_Result r) {
        int stored = 0;
        try {
            while (buffer.poll().isPresent()) {
                stored++;
            }
        } catch (RuntimeException e) {
            stored = -1;
        }
        r.r3 = stored;
    }
}
