"use client";

import { useActionState } from "react";
import type { AuthFormState } from "@/lib/auth/actions";

const INITIAL_STATE: AuthFormState = { error: null };

export function AuthForm({
  action,
  submitLabel,
  passwordAutoComplete,
}: {
  action: (state: AuthFormState, formData: FormData) => Promise<AuthFormState>;
  submitLabel: string;
  passwordAutoComplete: "current-password" | "new-password";
}) {
  const [state, formAction, pending] = useActionState(action, INITIAL_STATE);

  return (
    <form action={formAction} className="flex flex-col gap-3">
      <label className="flex flex-col gap-1 text-[13px] font-semibold">
        Email
        <input name="email" type="email" required autoComplete="email" className="field" />
      </label>
      <label className="flex flex-col gap-1 text-[13px] font-semibold">
        Password
        <input
          name="password"
          type="password"
          required
          minLength={8}
          autoComplete={passwordAutoComplete}
          className="field"
        />
      </label>
      {state.error && (
        <p className="text-[13px]" style={{ color: "var(--tint-text)" }} role="alert">
          {state.error}
        </p>
      )}
      <button type="submit" disabled={pending} className="btn-fill mt-1 px-4 py-2 text-[15px] disabled:opacity-60">
        {pending ? "Working on it…" : submitLabel}
      </button>
    </form>
  );
}
