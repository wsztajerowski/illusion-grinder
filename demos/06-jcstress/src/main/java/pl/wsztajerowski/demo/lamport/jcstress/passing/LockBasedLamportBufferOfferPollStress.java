package pl.wsztajerowski.demo.lamport.jcstress.passing;

import org.openjdk.jcstress.annotations.Actor;
import org.openjdk.jcstress.annotations.JCStressTest;
import org.openjdk.jcstress.annotations.Outcome;
import org.openjdk.jcstress.annotations.State;
import org.openjdk.jcstress.infra.results.I_Result;
import pl.wsztajerowski.demo.lamport.LamportBuffer;
import pl.wsztajerowski.demo.lamport.mpmc.LockBasedLamportBuffer;

import static org.openjdk.jcstress.annotations.Expect.ACCEPTABLE;
import static org.openjdk.jcstress.annotations.Expect.FORBIDDEN;

@JCStressTest
@Outcome(id = "-1", expect = ACCEPTABLE, desc = "Poll ran before offer.")
@Outcome(id = "42", expect = ACCEPTABLE, desc = "Poll observed offered value.")
@Outcome(id = ".*", expect = FORBIDDEN, desc = "Unexpected value — data race or logic error.")
@State
public class LockBasedLamportBufferOfferPollStress {

    private final LamportBuffer<Integer> buffer = LockBasedLamportBuffer.createBuffer(Integer.class, 2);

    @Actor
    public void producer() {
        buffer.offer(42);
    }

    @Actor
    public void consumer(I_Result r) {
        r.r1 = buffer.poll().orElse(-1);
    }
}

