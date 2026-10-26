"use client";

import { useState } from "react";
import { createGoal } from "@/lib/actions";
import { goalNameProblem } from "@/lib/goalName";

export function NewGoalForm({ existingNames }: { existingNames: string[] }) {
  const [name, setName] = useState("");
  const problem = goalNameProblem(name, existingNames);

  return (
    <form action={createGoal} onSubmit={() => setName("")} className="flex flex-wrap items-end gap-2 text-sm">
      <label className="flex flex-col gap-1">
        Goal
        <input
          name="name"
          required
          value={name}
          onChange={(event) => setName(event.target.value)}
          placeholder="Learn Rust"
          aria-invalid={problem !== null}
          aria-describedby={problem ? "goal-name-problem" : undefined}
          className="rounded-[10px] border hairline bg-surface px-2 py-1"
        />
      </label>
      <label className="flex flex-col gap-1">
        Why it matters (optional)
        <input name="description" className="rounded-[10px] border hairline bg-surface px-2 py-1" />
      </label>
      <button type="submit" disabled={problem !== null} className="btn-fill px-4 py-1.5 disabled:opacity-60">
        Add
      </button>
      {problem && (
        <p id="goal-name-problem" role="alert" className="basis-full text-[13px]" style={{ color: "var(--tint-text)" }}>
          {problem}
        </p>
      )}
    </form>
  );
}
