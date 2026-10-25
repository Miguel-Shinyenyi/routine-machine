import type { ConsentState } from "./consent";

// What the public privacy page (/privacy) says about analytics, as data, so a test can hold it to the code. Staged Prompt 5.
// Every event the app is allowed to send (lib/analytics/events.ts) is described here: what it is and what it carries. `sentToday` is
// true only for events the app really sends now; flip it when a later part wires an event to the app.
export interface EventDisclosure {
  when: string;
  carries: string;
  sentToday: boolean;
}

export const EVENT_DISCLOSURE: Record<string, EventDisclosure> = {
  $pageview: {
    when: "You open a page.",
    carries:
      "The page's address without anything after a ? or #, and the address you came from, cut the same way. The time. Your browser and its version, your operating system, your language, your time zone, your screen and window size, and your browser's user agent text. A random device number and a random visit number that this app makes up.",
    sentToday: true,
  },
  $identify: {
    when: "You are signed in.",
    carries:
      "Your internal user number, which this app assigns and which is not your email address or your name, tied to the random device number above. Nothing else about you.",
    sentToday: true,
  },
  goal_created: { when: "You create a goal.", carries: "Nothing about the goal: not its name and not its description. (Nothing in the app creates a goal yet, so this is never sent today.)", sentToday: false },
  day_logged: { when: "A day is logged.", carries: "Only where the log came from (for example you tapped it, or an automatic source). Nothing about what was logged.", sentToday: true },
  credit_questioned: { when: "You question a credit.", carries: "Nothing about the credit, the goal or the evidence.", sentToday: true },
  reading_note_submitted: { when: "You submit a reading note.", carries: "No text of the note, no score and no book.", sentToday: true },
  pause_logged: { when: "You log a pause before a spending decision.", carries: "No amount and no item.", sentToday: true },
  hillside_joined: { when: "You join the hillside.", carries: "Nothing else.", sentToday: true },
  hillside_left: { when: "You leave the hillside.", carries: "Nothing else.", sentToday: true },
  cohort_dashboard_viewed: { when: "You open a cohort dashboard.", carries: "Nothing about the cohort or its members. Not even which cohort.", sentToday: true },
};

// Where PostHog keeps the data, from the host the deployment is configured with. Only its two hosted regions are named; any other host
// (a self-hosted one, a test one) is not guessed at.
export function regionOf(host: string): string | null {
  let hostname: string;
  try {
    hostname = new URL(host).hostname;
  } catch {
    return null;
  }
  if (hostname === "us.i.posthog.com" || hostname === "us.posthog.com") return "the United States";
  if (hostname === "eu.i.posthog.com" || hostname === "eu.posthog.com") return "the European Union";
  return null;
}

// The visitor's current choice, in a sentence, for the "Your choice" box on the privacy page.
export function describeChoice(state: ConsentState, reason: string): string {
  if (reason === "Do Not Track" || reason === "Global Privacy Control") {
    return `Your browser sends ${reason}, so nothing is counted, whatever you choose here.`;
  }
  if (state === "granted") return "You accepted. The pages you open are being counted.";
  if (state === "declined") return "You declined. Nothing is being counted.";
  return "You have not chosen yet. Nothing is counted until you accept.";
}
