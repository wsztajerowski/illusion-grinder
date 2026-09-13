package pl.wsztajerowski.demo.lamport.jcstress.passing;

import org.openjdk.jcstress.annotations.Actor;
import org.openjdk.jcstress.annotations.JCStressTest;
import org.openjdk.jcstress.annotations.Outcome;
import org.openjdk.jcstress.annotations.State;
import org.openjdk.jcstress.infra.results.II_Result;
import pl.wsztajerowski.demo.lamport.LamportBuffer;
import pl.wsztajerowski.demo.lamport.mpmc.FastPathLamportBuffer;

import static org.openjdk.jcstress.annotations.Expect.ACCEPTABLE;
import static org.openjdk.jcstress.annotations.Expect.ACCEPTABLE_INTERESTING;
import static org.openjdk.jcstress.annotations.Expect.FORBIDDEN;

/**
 * Two consumers against one element in {@link FastPathLamportBuffer} — the scenario Fray
 * constructs deterministically, handed to jcstress to see whether random hardware
 * scheduling finds it too.
 *
 * <p>The race: both consumers clear the unlocked {@code buffer[readPosition] == null}
 * fast-path check while the element is still present. The first then takes the lock and
 * advances {@code readPosition}; the second takes the lock and reads the <em>next</em>
 * slot, which is null, and {@code Optional.of(null)} throws.
 *
 * <p>The NPE is caught and reported as {@code -99} so it becomes an observable outcome
 * rather than a hard error that aborts the run.
 *
 * <p>Result pair is {@code (consumer1, consumer2)}.
 */
@JCStressTest
@Outcome(id = {"42, -1", "-1, 42"}, expect = ACCEPTABLE,
        desc = "One consumer took the element, the other found the buffer empty.")
@Outcome(id = ".*-99.*", expect = ACCEPTABLE_INTERESTING,
        desc = "Unlocked check-then-act: poll() threw NullPointerException.")
@Outcome(id = "42, 42", expect = ACCEPTABLE_INTERESTING,
        desc = "Both consumers received the same element — duplicated delivery.")
@Outcome(id = ".*", expect = FORBIDDEN,
        desc = "Unexpected state — corruption beyond the fast-path race.")
@State
public class TwoConsumersFastPathBufferStress {

    private final LamportBuffer<Integer> buffer = FastPathLamportBuffer.createBuffer(Integer.class, 2);

    public TwoConsumersFastPathBufferStress() {
        buffer.offer(42);
    }

    @Actor
    public void consumer1(II_Result r) {
        r.r1 = pollOrMarkFailure();
    }

    @Actor
    public void consumer2(II_Result r) {
        r.r2 = pollOrMarkFailure();
    }

    private int pollOrMarkFailure() {
        try {
            return buffer.poll().orElse(-1);
        } catch (NullPointerException e) {
            return -99;
        }
    }
}
