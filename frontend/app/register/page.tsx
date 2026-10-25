import Link from "next/link";
import { AuthForm } from "@/components/AuthForm";
import { registerAction } from "@/lib/auth/actions";

export default function RegisterPage() {
  return (
    <div className="mx-auto flex min-h-screen max-w-[420px] flex-col justify-center gap-6 px-5 py-10">
      <div>
        <p className="text-[15px] font-semibold text-tint-text">Routine Machine</p>
        <h1 className="page-title">Join the flock.</h1>
        <p className="mt-2 text-[15px] text-muted">A password, nothing else. At least 8 characters.</p>
      </div>
      <div className="card p-5">
        <AuthForm action={registerAction} submitLabel="Create account" passwordAutoComplete="new-password" />
      </div>
      <p className="text-center text-[14px] text-muted">
        Already have an account? <Link href="/login">Sign in</Link>
      </p>
      <p className="text-center text-[12px] text-muted">
        <Link href="/privacy">Privacy</Link>
      </p>
    </div>
  );
}
