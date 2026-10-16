"use server";

import { revalidatePath } from "next/cache";
import { backendFetch } from "./api";

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
