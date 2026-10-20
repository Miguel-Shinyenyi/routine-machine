// Per-user scoping, part 3 (docs/backend.md). Every personal backend route answers for one
// user when given `userId`, and shows only unowned legacy rows when not — so every personal
// call from a signed-in page adds it. Replaces a userId already on the path rather than
// sending two.
export function withUserId(path: string, userId: number): string {
  const [base, query = ""] = path.split("?");
  const kept = query.split("&").filter((pair) => pair !== "" && !pair.startsWith("userId="));
  return `${base}?${[...kept, `userId=${userId}`].join("&")}`;
}
