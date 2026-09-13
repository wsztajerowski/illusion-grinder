package pl.wsztajerowski.demo.lamport.jcstress.passing;

import org.openjdk.jcstress.annotations.*;
import org.openjdk.jcstress.infra.results.III_Result;
import pl.wsztajerowski.demo.lamport.LamportBuffer;
import pl.wsztajerowski.demo.lamport.spsc.VolatileLamportBuffer;

import java.util.Optional;

@JCStressTest
@Outcome(id = "1, 1, 0", expect = Expect.ACCEPTABLE,
    desc = "Consumer pobral element, kolejka jest pusta po zakonczeniu")
@Outcome(id = "1, 0, 1", expect = Expect.ACCEPTABLE,
    desc = "Consumer byl za wczesnie; arbiter pobral element pozniej")
@Outcome(id = "1, 0, 0", expect = Expect.FORBIDDEN,
    desc = "Element zniknal: consumer zobaczyl sygnal i skonsumowal null")
@State
public class VolatileSpscLamportBufferJcstressTest {

    private final LamportBuffer<Integer> queue =
        VolatileLamportBuffer.createBuffer(Integer.class, 2);

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