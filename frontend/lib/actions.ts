"use server";

import { revalidatePath } from "next/cache";
import { authenticatedBackendFetch, backendFetch, intelligenceFetch } from "./api";
import { getSessionToken } from "./auth/session";
import type { ReadingNoteAssessment } from "./types";

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
