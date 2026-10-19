"use server";

import { revalidatePath } from "next/cache";
import { authenticatedBackendFetch, ApiError, backendFetch, backendFetchMultipart, intelligenceFetch } from "./api";
import { getSessionToken, getSessionUserId } from "./auth/session";
import type { MpesaImportResponse, ReadingNoteAssessment } from "./types";

function optionalNumber(value: FormDataEntryValue | null): number | null {
  if (value === null || value === "") return null;
  return Number(value);
}

export async function createScheduleTemplate(formData: FormData): Promise<void> {
  await backendFetch("/api/schedule-templates", {
    method: "POST",
    body: JSON.stringify({
      dayOfWeek: formData.get("dayOfWeek"),
      targetType: formData.get("targetType"),
      routineItemId: optionalNumber(formData.get("routineItemId")),
      targetDurationMinutes: Number(formData.get("targetDurationMinutes")),
      sortOrder: Number(formData.get("sortOrder")),
      label: formData.get("label"),
    }),
  });

  revalidatePath("/flock");
  revalidatePath("/path");
}

export async function createRoutineItem(formData: FormData): Promise<void> {
  await backendFetch("/api/routine-items", {
    method: "POST",
    body: JSON.stringify({
      name: formData.get("name"),
      description: formData.get("description") || null,
      category: formData.get("category") || null,
      goalId: optionalNumber(formData.get("goalId")),
    }),
  });

  revalidatePath("/flock");
}

export async function createAdHocTask(formData: FormData): Promise<void> {
  await backendFetch("/api/tasks", {
    method: "POST",
    body: JSON.stringify({
      taskDate: formData.get("taskDate"),
      label: formData.get("label"),
      routineItemId: optionalNumber(formData.get("routineItemId")),
    }),
  });

  revalidatePath("/today");
  revalidatePath("/flock");
}

export async function createLearningTopic(formData: FormData): Promise<void> {
  await backendFetch("/api/learning-topics", {
    method: "POST",
    body: JSON.stringify({
      name: formData.get("name"),
      goalId: Number(formData.get("goalId")),
    }),
  });

  revalidatePath("/flock");
}

export async function updateTaskStatus(taskId: number, status: string): Promise<void> {
  await backendFetch(`/api/tasks/${taskId}`, {
    method: "PATCH",
    body: JSON.stringify({ status }),
  });

  revalidatePath("/today");
  revalidatePath("/tally");
}

export async function createCurrentReading(formData: FormData): Promise<void> {
  await backendFetch("/api/current-reading", {
    method: "POST",
    body: JSON.stringify({ title: formData.get("title") }),
  });

  revalidatePath("/flock");
}

export async function createBook(formData: FormData): Promise<void> {
  await backendFetch("/api/books", {
    method: "POST",
    body: JSON.stringify({
      title: formData.get("title"),
      author: formData.get("author") || null,
      currentChapter: optionalNumber(formData.get("currentChapter")),
      totalChapters: optionalNumber(formData.get("totalChapters")),
    }),
  });

  revalidatePath("/reading");
}

// Calls the intelligence service to assess the note before saving it, but a failed or
// unconfigured assessment (no ANTHROPIC_API_KEY, Claude unreachable) never blocks the save
// itself: writing the note is the user's real action, assessment is an addition to it. See
// docs/backend.md's "assessment fields are all nullable" decision.
export async function submitReadingNote(formData: FormData): Promise<void> {
  const bookId = Number(formData.get("bookId"));
  const noteText = String(formData.get("noteText"));

  let assessment: ReadingNoteAssessment | null = null;
  try {
    assessment = await intelligenceFetch<ReadingNoteAssessment>("/reading-notes/assess", {
      method: "POST",
      body: JSON.stringify({
        noteText,
        bookTitle: formData.get("bookTitle"),
        currentChapter: optionalNumber(formData.get("currentChapter")),
        totalChapters: optionalNumber(formData.get("totalChapters")),
      }),
    });
  } catch {
    assessment = null;
  }

  await backendFetch("/api/reading-notes", {
    method: "POST",
    body: JSON.stringify({
      bookId,
      noteText,
      chapterPlaced: assessment?.chapterPlaced ?? null,
      ownWordsPercentage: assessment?.ownWordsPercentage ?? null,
      ideasConnected: assessment?.ideasConnected ?? null,
      quotedPhrases: assessment?.quotedPhrases ?? null,
      questionBack: assessment?.questionBack ?? null,
    }),
  });

  revalidatePath("/reading");
  revalidatePath("/tally");
}

// The hillside should never enroll anyone who hasn't explicitly agreed (see
// docs/backend.md's hillside_opt_in decision), so this only ever runs for a signed-in session
// — the Hillside page itself doesn't render the toggle at all when there's no session token.
export async function setHillsideOptIn(formData: FormData): Promise<void> {
  const token = await getSessionToken();
  if (!token) return;

  await authenticatedBackendFetch("/api/users/me/hillside-opt-in", token, {
    method: "PUT",
    body: JSON.stringify({ optIn: formData.get("optIn") === "true" }),
  });

  revalidatePath("/hillside");
}

// "Question a credit" — records the request, nothing more (see
// docs/product-direction.md's Hillside v2 transparency bullet).
export async function questionCredit(formData: FormData): Promise<void> {
  const eventId = formData.get("eventId");
  await backendFetch(`/api/evidence-events/${eventId}/question`, { method: "PATCH" });
  revalidatePath("/hillside");
}

function splitCommaList(value: FormDataEntryValue | null): string[] {
  if (typeof value !== "string" || value.trim() === "") return [];
  return value.split(",").map((v) => v.trim()).filter((v) => v.length > 0);
}

// Phase 10's Profile page. Interests/learning preferences are checkboxes (a fixed set, same
// toggle-chip look as the design reference) plus a free-text "add your own" field merged in;
// thinkers is free text only, since the reference shows it as a single text input, not a pick
// list. See docs/product-direction.md's Profile section.
export async function updateProfile(formData: FormData): Promise<void> {
  const token = await getSessionToken();
  if (!token) return;

  const interests = [...formData.getAll("interests").map(String), ...splitCommaList(formData.get("customInterests"))];
  const learningPreferences = formData.getAll("learningPreferences").map(String);
  const thinkers = splitCommaList(formData.get("thinkers"));
  const currency = formData.get("currency");
  const timeZone = formData.get("timeZone");
  const dayEndHour = formData.get("dayEndHour");
  const analogiesEnabled = formData.get("analogiesEnabled") === "on";

  await authenticatedBackendFetch("/api/users/me/profile", token, {
    method: "PUT",
    body: JSON.stringify({
      interests,
      thinkers,
      learningPreferences,
      currency: typeof currency === "string" && currency.trim() !== "" ? currency.trim() : null,
      timeZone: typeof timeZone === "string" && timeZone.trim() !== "" ? timeZone.trim() : null,
      dayEndHour: typeof dayEndHour === "string" && dayEndHour !== "" ? Number(dayEndHour) : null,
      analogiesEnabled,
    }),
  });

  revalidatePath("/profile");
  revalidatePath("/today");
}

// Phase 11: the granary. userId always comes from the verified session, never from the form
// — the same reasoning every other authenticated action here already follows.
export async function storeSaving(): Promise<void> {
  const userId = await getSessionUserId();
  if (userId === null) return;
  await backendFetch("/api/granary/savings", { method: "POST", body: JSON.stringify({ userId }) });
  revalidatePath("/granary");
  revalidatePath("/today");
}

export async function setSavingTargetAmount(formData: FormData): Promise<void> {
  const userId = await getSessionUserId();
  if (userId === null) return;
  const amount = Number(formData.get("amount"));
  await backendFetch("/api/granary/saving-target-amount", {
    method: "PUT",
    body: JSON.stringify({ userId, amount }),
  });
  revalidatePath("/granary");
}

export async function createPause(formData: FormData): Promise<void> {
  const userId = await getSessionUserId();
  if (userId === null) return;
  await backendFetch("/api/granary/pauses", {
    method: "POST",
    body: JSON.stringify({
      userId,
      what: formData.get("what"),
      amount: formData.get("amount") || null,
      why: formData.get("why") || null,
      heardFrom: formData.get("heardFrom") || null,
    }),
  });
  revalidatePath("/granary");
  revalidatePath("/today");
}

export async function decidePause(formData: FormData): Promise<void> {
  const pauseId = formData.get("pauseId");
  const outcome = formData.get("outcome");
  await backendFetch(`/api/granary/pauses/${pauseId}/decide`, {
    method: "PATCH",
    body: JSON.stringify({ outcome }),
  });
  revalidatePath("/granary");
  revalidatePath("/today");
}

export async function reflectPause(formData: FormData): Promise<void> {
  const pauseId = formData.get("pauseId");
  const reflection = formData.get("reflection");
  await backendFetch(`/api/granary/pauses/${pauseId}/reflect`, {
    method: "PATCH",
    body: JSON.stringify({ reflection }),
  });
  revalidatePath("/granary");
  revalidatePath("/today");
}

export interface MpesaImportState {
  error: string | null;
  needsPassword: boolean;
}

// Phase 11: the PDF's bytes go straight to the backend as multipart form data and are never
// written to disk here either — nothing in this action persists the file. See
// docs/backend.md's decisions log for the password-protected-PDF and format-change handling
// this was built against.
export async function importMpesaStatement(
  _prevState: MpesaImportState,
  formData: FormData,
): Promise<MpesaImportState> {
  const userId = await getSessionUserId();
  if (userId === null) {
    return { error: "You need to be signed in to import a statement.", needsPassword: false };
  }

  const file = formData.get("file");
  if (!(file instanceof File) || file.size === 0) {
    return { error: "Choose a PDF statement to import.", needsPassword: false };
  }
  const password = formData.get("password");

  const body = new FormData();
  body.set("userId", String(userId));
  body.set("file", file);
  if (typeof password === "string" && password.trim() !== "") {
    body.set("password", password.trim());
  }

  try {
    await backendFetchMultipart<MpesaImportResponse>("/api/granary/mpesa-statement", body);
  } catch (error) {
    if (error instanceof ApiError) {
      try {
        const parsed = JSON.parse(error.message) as { error?: string; type?: string };
        return {
          error: parsed.error ?? "Couldn't import that statement.",
          needsPassword: parsed.type === "PASSWORD_REQUIRED",
        };
      } catch {
        return { error: "Couldn't import that statement.", needsPassword: false };
      }
    }
    return { error: "Couldn't import that statement.", needsPassword: false };
  }

  revalidatePath("/granary");
  return { error: null, needsPassword: false };
}

// An org admin who isn't also enrolled in this specific cohort can view the dashboard
// ("admins see aggregates only") but isn't eligible to answer the pilot's own survey, which
// is a learner's question, not an admin's — the backend 403s that case. Swallowed here
// rather than crashing the page; any other failure still surfaces as a real error.
export async function submitCohortSurveyResponse(formData: FormData): Promise<void> {
  const userId = await getSessionUserId();
  if (userId === null) return;
  const cohortId = formData.get("cohortId");
  try {
    await backendFetch(`/api/organisations/cohorts/${cohortId}/survey-response`, {
      method: "POST",
      body: JSON.stringify({ requestingUserId: userId, answer: formData.get("answer") }),
    });
  } catch (error) {
    if (!(error instanceof ApiError && error.status === 403)) {
      throw error;
    }
  }
  revalidatePath(`/cohort/${cohortId}`);
}
