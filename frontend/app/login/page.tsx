import Link from "next/link";
import { AuthForm } from "@/components/AuthForm";
import { demoLoginAction, loginAction } from "@/lib/auth/actions";
import { demoEnabledFrom } from "@/lib/demo";

export default async function LoginPage(props: PageProps<"/login">) {
  const searchParams = await props.searchParams;
  const demoOn = demoEnabledFrom(process.env.DEMO_PROFILE_ENABLED);
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
      {demoOn && (
        <form action={demoLoginAction} className="card flex flex-col gap-2 p-4 text-center">
          <p className="text-[14px] text-muted">Only looking? Open a demo with made-up sample data. Nothing in it is real.</p>
          <button type="submit" className="btn-fill mx-auto px-4 py-2 text-[15px]">
            Try the demo
          </button>
        </form>
      )}
      {searchParams.demo === "unavailable" && (
        <p role="alert" className="text-center text-[13px]" style={{ color: "var(--tint-text)" }}>
          The demo is not available right now.
        </p>
      )}
      <p className="text-center text-[12px] text-muted">
        <Link href="/privacy">Privacy</Link>
      </p>
    </div>
  );
}
