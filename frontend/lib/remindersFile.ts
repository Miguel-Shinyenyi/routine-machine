import { readFile } from "node:fs/promises";
import path from "node:path";
import { parseReminderFile } from "./reminders";

// Server only. Read on each request so the page never needs a code change to pick up an edited file (in the Docker image the
// file is copied beside the server, see frontend/Dockerfile). A missing or unreadable file means no reminder, never a broken page.
export async function loadReminders(): Promise<{ daily: string[]; stronger: string[] }> {
  try {
    return parseReminderFile(await readFile(path.join(process.cwd(), "content", "reminders.txt"), "utf8"));
  } catch {
    return { daily: [], stronger: [] };
  }
}
