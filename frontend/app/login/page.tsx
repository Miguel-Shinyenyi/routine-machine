import Link from "next/link";
import { AuthForm } from "@/components/AuthForm";
import { loginAction } from "@/lib/auth/actions";

export default function LoginPage() {
  return (
    <div className="mx-auto flex min-h-screen max-w-[420px] flex-col justify-center gap-6 px-5 py-10">
      <div>
        <p className="text-[15px] font-semibold text-tint-text">Routine Machine</p>
        <h1 className="page-title">Welcome back.</h1>
        <p className="mt-2 text-[15px] text-muted">Sign in to see your own flock, not the sample one.</p>
      </div>
      <div className="card p-5">
        <AuthForm action={loginAction} submitLabel="Sign in" passwordAutoComplete="current-password" />
      </div>
      <p className="text-center text-[14px] text-muted">
        New here? <Link href="/register">Create an account</Link>
      </p>
      <p className="text-center text-[12px] text-muted">
        <Link href="/privacy">Privacy</Link>
      </p>
    </div>
  );
}
