# Understanding first

Rules for every project. They apply to code you write and code an AI writes. Source: Miguel's notes of 2026-10-16.

## Why

- Hype makes you chase volume. Volume you cannot follow is not progress.
- A third feeling feeds the hype: there is so much to do that you end up doing nothing. It is a cause of the hype, so it gets its own rule (rule 11), next to fear of missing out and desperation.
- Code that is hyped carries fear of missing out and desperation. Code you understand carries good emotions. Understanding takes time.
- AI can write the code. You stay close behind it and understand each part as it appears.
- Problems come from patterns where people struggled. Understand the people first. Then write code, preferably with AI. Then stop and check that the output matches the intent.
- Patterns, constants and outliers only show up when you give them time.
- If you only write code, ask who you are helping.

## The cycle of every step

Added 2026-10-19, in Miguel's words: understand things as they are, not as you want them to be. Then let AI code what you understood is needed. That leads to an intervention with more good than harm, because every action has consequences. Every step goes round this cycle.

1. Understand. Read the code, the data and the people as they are now. Write down what you found, including what you wish were different but is not.
2. Decide what is needed. One sentence, from what you found, not from what you hoped to find.
3. Let AI write it, and stay close behind it.
4. Check the output against the sentence.
5. Weigh the consequences before you ship. Name who or what the change affects, what it could break, and how you would undo it. Ship only when the good outweighs the harm.
6. Go back to step 1 for the next step.

## Hard rules

1. Do not accept AI output you cannot explain. If you cannot explain it, stop and read it before the next prompt.
2. Review each change before the next change starts. No stacked, unread changes.
3. State the goal in one sentence before any work. Write it in the task, the issue or the prompt.
4. After the work, check the output against that sentence. Record the result.
5. Small daily work beats a large burst. Log what you did each day, even if it is one fix.
6. During a hype cycle, add no new tool or direction until the current one has a written reason and a finish line.
7. If the codebase is broken at its base, rewrite it. Do not patch on top of it.
8. Do not break your own train of thought while you work out the goal. Batch interruptions.
9. The goal is to solve the other person's problem as they state it, not the problem you think they have. Read their words first. Lead with their goal.
10. Do not act before you are asked. No drafts, follow-ups or audits nobody requested. When a thread raises something you already solved, step in then and help.
11. When there is so much to do that you do nothing, stop planning the whole. Pick the smallest next piece, finish it today and log it.
12. Before any change, record what is true now, as it is. A plan built on what you want to be true is the Assumption failure mode.
13. Every change states its likely consequences and how to undo it. No change ships without both.

## Failure modes

Each has a sign and a remedy. Name the mode in the project log when you notice it.

1. Forming. Right question, wrong answer. Sign: the answer is solid but does not help. Remedy: ask questions until you know the goal and how to approach it.
2. Dislodging. Stuck in a rut. Sign: the third attempt at the same approach. Remedy: accept the approach is not working and restart.
3. Assumption. Wrong question, right answer. Sign: a correct result for a problem nobody has. Remedy: go back and understand the goal.
4. Location. So close, so far. Sign: a step was skipped. Remedy: slow down and take every step.
5. Achievement. A band aid on a broken bone. Sign: patches on patches. Remedy: if the whole codebase is poor, rewrite it.
6. Progression. Falling behind without noticing. Sign: AI wrote code you do not understand. Remedy: stop, read, rebuild your understanding, then continue.
7. Interruption. Someone breaks the thought that was finding the goal. Remedy: you know the goal best; you deal with the humans; protect the time.
8. Mislead. Suggestions that lead you astray. Remedy: check each suggestion against the goal before you act on it.

## Where this applies

- Every `CLAUDE.md` and `PROJECT.md` links here and to `docs/engineering-concerns.md`.
- Every prompt to Claude Code starts with the goal sentence and ends with a check of the output against it.
- Every open-source comment starts with the goal, then the evidence, then the method.