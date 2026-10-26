// The opt-in demo profile (staged Prompt 6). Off unless DEMO_PROFILE_ENABLED is exactly "true".
export function demoEnabledFrom(value: string | undefined): boolean {
  return value === "true";
}

export interface DemoDeps {
  enabled: boolean;
  internalToken: string | undefined;
  // Asks the backend to open the demo account, with the internal token.
  call: (internalToken: string) => Promise<{ id: number; demo: boolean }>;
}

// The demo account's id, or null when the demo is off, cannot be asked for, fails, or the account is not flagged as a demo.
// The last check keeps this one route from ever signing a real person in.
export async function openDemo(deps: DemoDeps): Promise<number | null> {
  if (!deps.enabled || !deps.internalToken) return null;
  try {
    const user = await deps.call(deps.internalToken);
    return user.demo === true ? user.id : null;
  } catch {
    return null;
  }
}
