"use client";

import { useActionState, useEffect, useRef } from "react";
import { importMpesaStatement, type MpesaImportState } from "@/lib/actions";

const INITIAL_STATE: MpesaImportState = { error: null, needsPassword: false };

// Phase 11: the M-Pesa statement import form, in a native <dialog> modal rather than an
// inline expanding card — see docs/frontend.md's decisions log. It's a rare action (once a
// month), and a modal keeps the trigger button from shifting the rest of the page around it.
export function MpesaImportForm() {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const submittedRef = useRef(false);
  const [state, formAction, pending] = useActionState(importMpesaStatement, INITIAL_STATE);

  useEffect(() => {
    if (!pending && submittedRef.current && state.error === null) {
      submittedRef.current = false;
      dialogRef.current?.close();
    }
  }, [pending, state]);

  return (
    <>
      <button
        type="button"
        onClick={() => dialogRef.current?.showModal()}
        className="rounded-full px-4 py-2 text-[14px] font-semibold"
        style={{ background: "var(--fill)" }}
      >
        Import M-Pesa statement
      </button>
      <dialog
        ref={dialogRef}
        className="m-auto rounded-2xl p-0 backdrop:bg-black/40"
        style={{ background: "var(--surface)" }}
        onClick={(event) => {
          if (event.target === dialogRef.current) dialogRef.current?.close();
        }}
        onClose={() => {
          submittedRef.current = false;
        }}
      >
        <form
          action={formAction}
          onSubmit={() => {
            submittedRef.current = true;
          }}
          className="card flex w-[min(420px,calc(100vw-2rem))] flex-col gap-3 p-4"
        >
          <div className="flex items-center justify-between">
            <h3 className="text-[14px] font-semibold">Import M-Pesa statement</h3>
            <button
              type="button"
              onClick={() => dialogRef.current?.close()}
              className="text-[13px] text-muted hover:underline"
            >
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
          <button
            type="submit"
            disabled={pending}
            className="btn-fill self-start px-4 py-2 text-[14px] font-semibold disabled:opacity-60"
          >
            {pending ? "Reading it…" : "Import"}
          </button>
        </form>
      </dialog>
    </>
  );
}
