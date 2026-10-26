// Why a typed goal name cannot be added, or null. The backend refuses a name the person already has (exact match), so say it before the click.
export function goalNameProblem(typed: string, existingNames: string[]): string | null {
  const name = typed.trim();
  if (name === "") return null;
  return existingNames.includes(name) ? "You already have a goal with that name." : null;
}
