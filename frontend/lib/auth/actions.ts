"use server";

import { redirect } from "next/navigation";
import { ApiError, backendFetch } from "@/lib/api";
import { createSession, destroySession } from "./session";

export interface AuthFormState {
  error: string | null;
}

export async function loginAction(_prevState: AuthFormState, formData: FormData): Promise<AuthFormState> {
  const email = String(formData.get("email") ?? "");
  const password = String(formData.get("password") ?? "");

  let user: { id: number };
  try {
    // Matches AuthService.login's own wording (backend/.../service/AuthService.java): never
    // say which part was wrong, so a failed guess can't be used to enumerate registered emails.
    user = await backendFetch<{ id: number }>("/api/auth/login", {
      method: "POST",
      body: JSON.stringify({ email, password }),
    });
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      return { error: "That email or password doesn't match an account." };
    }
    return { error: "Something went wrong signing in. Try again." };
  }

  await createSession(user.id);
  redirect("/today");
}

export async function registerAction(_prevState: AuthFormState, formData: FormData): Promise<AuthFormState> {
  const email = String(formData.get("email") ?? "");
  const password = String(formData.get("password") ?? "");

  if (password.length < 8) {
    return { error: "Password needs to be at least 8 characters." };
  }

  let user: { id: number };
  try {
    user = await backendFetch<{ id: number }>("/api/auth/register", {
      method: "POST",
      body: JSON.stringify({ email, password }),
    });
  } catch (error) {
    // 400 here covers both a duplicate email (AuthService.register) and a malformed email
    // (RegisterRequest's own @Email validation) — the browser's own type="email" input
    // already catches most format mistakes before this ever fires, so "already registered"
    // is the far more likely real cause, but the message stays accurate for either.
    if (error instanceof ApiError && error.status === 400) {
      return { error: "That email is already registered, or isn't a valid address." };
    }
    return { error: "Something went wrong creating your account. Try again." };
  }

  await createSession(user.id);
  redirect("/today");
}

export async function logoutAction(): Promise<void> {
  await destroySession();
  redirect("/login");
}
