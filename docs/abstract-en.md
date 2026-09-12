# The Illusion Grinder: Four Circles of Testing Hell for Concurrent Java

## Abstract

Is your concurrent code a solid structure, or a house of cards the wind simply hasn't hit yet? In this talk we feed a piece of concurrent code into a machine built for grinding illusions: four circles of testing hell, where every stage is a gate you must pass before you're allowed any deeper — from naive unit tests, through a flogging of interleavings in Fray and a brutal workout on real hardware in jcstress, all the way to a performance examination of conscience in JMH. No magic. Just brutal engineering and a touch of concurrent sadism.

## Description

Green unit tests are not enough to trust concurrent code — and yet most teams stop there. This talk shows why that confidence is an illusion, and how to dismantle it systematically.

Using Lamport's circular buffer — four implementations of the same interface, two of them deliberately broken, all four passing CI — we work through four layers of testing rigour: JUnit (does the algorithm's logic hold?), Fray (systematic exploration of thread interleavings), jcstress (correctness against the Java Memory Model on real hardware), and JMH (the cost of correctness, in ops/s).

Every layer catches a different demon. Fray constructs the exact interleaving that leads to an NPE in the "optimised" fast path — and replays it deterministically, under a debugger. jcstress finds ~35,000 executions with an invisible producer write out of 600 million attempts — a memory-visibility bug Fray structurally cannot see. JMH closes the loop: the lock-free volatile implementation beats the lock-based one by 3× in throughput — but that number only means something once the implementation has been proven correct.

Attendees leave with a concrete decision map: which tool asks which question, and what each one is blind to without the others. The talk ships with a working demo repository containing the full test suite.
