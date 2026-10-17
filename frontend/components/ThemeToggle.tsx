"use client";

import { useSyncExternalStore } from "react";

type ThemePreference = "system" | "light" | "dark";

const NEXT: Record<ThemePreference, ThemePreference> = {
  system: "light",
  light: "dark",
  dark: "system",
};

const LABEL: Record<ThemePreference, string> = {
  system: "Auto",
  light: "Light",
  dark: "Dark",
};

// localStorage fires a "storage" event only in *other* tabs, never the one that wrote it, so
// applyTheme notifies these listeners directly after writing — the same-tab half of keeping
// useSyncExternalStore's snapshot in sync with a click.
const listeners = new Set<() => void>();

function applyTheme(theme: ThemePreference) {
  try {
    if (theme === "system") {
      document.documentElement.removeAttribute("data-theme");
      localStorage.removeItem("theme");
    } else {
      document.documentElement.setAttribute("data-theme", theme);
      localStorage.setItem("theme", theme);
    }
  } catch {
    // Private browsing or blocked storage: the toggle just won't persist across reloads.
  }
  listeners.forEach((listener) => listener());
}

function subscribe(onStoreChange: () => void) {
  listeners.add(onStoreChange);
  return () => listeners.delete(onStoreChange);
}

function getSnapshot(): ThemePreference {
  try {
    const saved = localStorage.getItem("theme");
    return saved === "light" || saved === "dark" ? saved : "system";
  } catch {
    return "system";
  }
}

function getServerSnapshot(): ThemePreference {
  return "system";
}

export function ThemeToggle() {
  const theme = useSyncExternalStore(subscribe, getSnapshot, getServerSnapshot);

  return (
    <button
      type="button"
      onClick={() => applyTheme(NEXT[theme])}
      className="rounded-[12px] px-3 py-2 text-left text-[13px] text-muted hover:bg-fill"
      aria-label="Toggle light or dark theme"
    >
      Theme: {LABEL[theme]}
    </button>
  );
}
