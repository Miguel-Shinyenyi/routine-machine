export interface FirstStep {
  title: string;
  body: string;
  href: string;
  cta: string;
}

// What a new person should do next, from what they have so far. null once the first step is done.
export function firstStep(counts: { routineItemCount: number; scheduleTemplateCount: number }): FirstStep | null {
  if (counts.routineItemCount === 0) {
    return {
      title: "Add your first routine.",
      body: "Pick one small thing you want to do most days. A walk, ten minutes of reading. Small is the point.",
      href: "/flock",
      cta: "Open the flock",
    };
  }
  if (counts.scheduleTemplateCount === 0) {
    return {
      title: "Put it on the path.",
      body: "Choose which days it happens. It then shows up here each day, ready to tap.",
      href: "/flock",
      cta: "Plan the path",
    };
  }
  return null;
}
