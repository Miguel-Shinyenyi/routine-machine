"use client";

import { useActionState, useState } from "react";
import { importMpesaStatement, type MpesaImportState } from "@/lib/actions";

const INITIAL_STATE: MpesaImportState = { error: null, needsPassword: false };

// Phase 11: the M-Pesa statement import form. Stays collapsed until opened, since it's a rare
// action (once a month), not something that should take up permanent space on the granary
// page — see docs/frontend.md's decisions log.
export function MpesaImportForm() {
  const [open, setOpen] = useState(false);
  const [state, formAction, pending] = useActionState(importMpesaStatement, INITIAL_STATE);

  if (!open) {
    return (
      <button
        type="button"
        onClick={() => setOpen(true)}
        className="rounded-full px-4 py-2 text-[14px] font-semibold"
        style={{ background: "var(--fill)" }}
      >
        Import M-Pesa statement
      </button>
    );
  }

  return (
    <form action={formAction} className="card flex w-full max-w-[420px] flex-col gap-3 p-4">
      <div className="flex items-center justify-between">
        <h3 className="text-[14px] font-semibold">Import M-Pesa statement</h3>
        <button type="button" onClick={() => setOpen(false)} className="text-[13px] text-muted hover:underline">
          Close
        </button>
      </div>
      <label className="flex flex-col gap-1 text-[13px] font-semibold">
        The statement PDF
        <input type="file" name="file" accept="application/pdf" required className="field" />
      </label>
      {(state.needsPassword || state.error === null) && (
        <label className="flex flex-col gap-1 text-[13px] font-semibold">
          Password, if it has one
          <input type="password" name="password" className="field" placeholder="Leave blank if none" />
        </label>
      )}
      {state.error && (
        <p className="text-[13px]" style={{ color: "var(--tint-text)" }} role="alert">
          {state.error}
        </p>
      )}
      <p className="text-[12px] text-muted">
        Only category totals are kept; the file is never saved, not even briefly.
      </p>
      <button type="submit" disabled={pending} className="btn-fill self-start px-4 py-2 text-[14px] font-semibold disabled:opacity-60">
        {pending ? "Reading it…" : "Import"}
      </button>
    </form>
  );
}
