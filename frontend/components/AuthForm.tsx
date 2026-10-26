"use client";

import { useActionState, useEffect, useRef } from "react";
import type { AuthFormState } from "@/lib/auth/actions";

const INITIAL_STATE: AuthFormState = { error: null };

export function AuthForm({
  action,
  submitLabel,
  passwordAutoComplete,
  sendTimeZone = false,
}: {
  action: (state: AuthFormState, formData: FormData) => Promise<AuthFormState>;
  submitLabel: string;
  passwordAutoComplete: "current-password" | "new-password";
  // Sign-up only: tell the server the browser's time zone, so a new account does not start on UTC.
  sendTimeZone?: boolean;
}) {
  const [state, formAction, pending] = useActionState(action, INITIAL_STATE);
  const zoneField = useRef<HTMLInputElement>(null);
  useEffect(() => {
    if (sendTimeZone && zoneField.current) {
      zoneField.current.value = Intl.DateTimeFormat().resolvedOptions().timeZone ?? "";
    }
  }, [sendTimeZone]);

  return (
    <form action={formAction} className="flex flex-col gap-3">
      {sendTimeZone && <input ref={zoneField} type="hidden" name="timeZone" />}
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
