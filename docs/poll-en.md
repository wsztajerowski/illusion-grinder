# Post-Talk Poll: The Illusion Grinder

**Form header:**

> Thanks for coming to the talk! Please fill in this short poll — it helps me make the
> material better.

**Shape:** 17 items (14 numbered, with Q13 split into 13a/13b, plus the recommend question
and two open ones), ~2–3 minutes to fill in. Q1–Q3 give you the lens to read every answer
that follows — a "too advanced" from someone who has never written a threaded test means
something different from the same answer out of a 15-year JVM engineer.

`*` marks a required question.

---

## A. About you

### 1. How much of the code you own is genuinely concurrent — shared mutable state across threads?

*Single choice*

- Core of what I do (queues, caches, schedulers, engines)
- Some of it — a few hot spots
- Rarely — the framework handles it for me
- None that I'm aware of

### 2. Which of these had you used before today?

*Multi-select*

- JUnit tests that spawn threads
- Fray
- jcstress
- JMH
- None of the above
- Other: \_\_\_

### 3. Years writing code professionally

*Single choice*

- 0–2
- 3–5
- 6–10
- 10+

---

## B. The presentation

### 4. Overall — how was the talk? `*`

*Scale 1–5, from "Poor" to "Great"*

### 5. Depth and level `*`

*Single choice*

- Too basic — I knew most of it already
- Slightly below my level, still enjoyed it
- Right on target
- Slightly over my head, but I followed
- Too advanced — I lost the thread

### 6. Pace `*`

*Single choice*

- Too slow
- A bit slow
- Right
- A bit fast
- Too fast

### 7. Which part was the most valuable to you? `*`

*Single choice*

- Fray and the deterministic replay under a debugger
- jcstress and the memory-visibility bug Fray cannot see
- JMH — what correctness actually costs
- The closing decision map and the three golden rules
- Hard to pick — they were all worth it
- Other: \_\_\_

### 8. …and which part could have been shorter, or cut? `*`

*Single choice*

- The setup — four implementations, two broken, all green
- Fray and the deterministic replay under a debugger
- jcstress and the memory-visibility bug Fray cannot see
- JMH — what correctness actually costs
- The closing decision map and the three golden rules
- Nothing — the balance was right
- Other: \_\_\_

> The setup is an option here and not in Q7. An on-ramp is invisible when it works, so ask
> whether it got in the way, not whether it thrilled anyone.

### 9. The talk promised you would leave knowing which tool asks which question. Did it deliver? `*`

*Single choice*

- Yes — I could pick the right tool tomorrow
- Mostly — I would need the slides in front of me
- Partly — I get the difference between Fray and jcstress, not much beyond that
- No — I am still not sure when to reach for what

> Worth more than the overall rating: it tests the actual promise made in the abstract,
> not how much people enjoyed themselves.

---

## C. The presenter

### 10. How clearly were the concepts explained? `*`

*Scale 1–5, from "Unclear" to "Very clear"*

### 11. Delivery `*`

*Grid, 1–5 scale per row*

- Energy and vocal dynamics
- Confidence and command of the material
- Keeping the room's attention

### 12. The framing — "circles of hell", "grinding illusions", "a touch of concurrent sadism" `*`

*Single choice*

- Loved it — it is what made the structure stick
- Fun, and it did not get in the way
- Neutral — I was there for the content
- A bit much — I would dial it back
- It distracted from the technical material

### 13a. Code on the slides — legibility

*Single choice*

- Readable from where I sat
- Too small / hard to read

### 13b. Code on the slides — volume

*Single choice*

- Right amount of code
- Too much code per slide
- Too little — I wanted to see more of the real thing

> Two questions rather than one multi-select list: font size and code density are different
> defects with different fixes. Merged into one question they produce a result you cannot
> act on.

---

## D. Wrap-up

### Would you recommend this talk to a colleague? `*`

*Single choice*

- Yes
- Maybe
- No

The cleanest single number to quote in a CFP submission — which is why it stands as a
required question ahead of the open ones, not as a footnote at the end.

### 14. One thing to keep, one thing to change

*Open text, optional*

### What would you want to see from me next?

*Open text, optional*

---

## Reading the results

- **Q5 × Q6** — whether a "too fast" is really a pacing problem or a depth problem. If the
  same people also answer "too advanced", the fix is more setup, not slower speech.
- **Q8 × Q3** — if it is mostly seniors who want the setup cut while newer developers
  answer "the balance was right", the on-ramp is doing its job. That is the argument for
  keeping it when reviewers tell you to trim it.
- **Q7 × Q1** — which half lands with the people who live in concurrent code. If they pick
  jcstress while the rest of the room picks Fray, the two halves of the talk have two
  different audiences and both are needed.
- **Q9 × the overall rating (Q4)** — high ratings alongside weak Q9 answers mean the talk
  entertains without teaching. It is the only pair that catches that.
