import { redirect } from "next/navigation";
import { authenticatedBackendFetch } from "@/lib/api";
import { updateProfile } from "@/lib/actions";
import { PROMPT_DAYS } from "@/lib/failureModes";
import { logoutAction } from "@/lib/auth/actions";
import { getSessionToken } from "@/lib/auth/session";
import type { CurrentUser, Profile } from "@/lib/types";

// A fixed set, matching the design reference's own pick buttons (docs/design-reference/pages/
// profile.html) — not every interest or learning style, just the reference's own list, plus
// "add your own" for anything else.
const INTEREST_OPTIONS = ["Football", "Philosophy", "Skating", "Cooking", "Music", "Chess", "Films", "Farming"];
const LEARNING_PREFERENCE_OPTIONS = [
  "Examples before definitions",
  "A question back after each note",
  "Short daily drills",
  "Link it to my own work",
  "Read aloud",
];

export default async function ProfilePage() {
  const sessionToken = await getSessionToken();
  if (!sessionToken) {
    redirect("/login");
  }

  const [me, profile] = await Promise.all([
    authenticatedBackendFetch<CurrentUser>("/api/users/me", sessionToken),
    authenticatedBackendFetch<Profile>("/api/users/me/profile", sessionToken),
  ]);

  const interests = profile.interests ?? [];
  const learningPreferences = profile.learningPreferences ?? [];
  const customInterests = interests.filter((i) => !INTEREST_OPTIONS.includes(i));

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="text-[15px] font-semibold text-tint-text">Profile</p>
          <h1 className="page-title">Who the Shepherd walks with.</h1>
          <p className="mt-2 max-w-[62ch] text-[17px] text-muted">
            What you share here changes how things are explained to you. It never reaches the
            hillside, and your programme never sees it.
          </p>
        </div>
        <form action={logoutAction}>
          <button type="submit" className="text-[14px] font-medium text-tint-text hover:underline">
            Sign out
          </button>
        </form>
      </div>

      <form action={updateProfile} className="flex flex-col gap-6">
        <section aria-labelledby="about" className="card flex flex-col gap-2 p-5">
          <h3 id="about" className="text-[15px] font-semibold">
            About you
          </h3>
          <div className="flex items-center justify-between gap-3 border-t hairline py-2.5">
            <span className="text-[14px]">Email</span>
            <span className="text-[14px] text-muted">{me.email}</span>
          </div>
          <div className="flex items-center justify-between gap-3 border-t hairline py-2.5">
            <label htmlFor="timeZone" className="text-[14px]">
              Time zone
            </label>
            <input
              id="timeZone"
              name="timeZone"
              defaultValue={profile.timeZone}
              className="field w-[220px] text-right"
              placeholder="Africa/Nairobi"
            />
          </div>
          <div className="flex items-center justify-between gap-3 border-t hairline py-2.5">
            <label htmlFor="failureModePromptDay" className="text-[14px]">
              Ask me &ldquo;which failure mode did you meet this week?&rdquo; from
            </label>
            <select
              id="failureModePromptDay"
              name="failureModePromptDay"
              defaultValue={profile.failureModePromptDay}
              className="field w-[160px]"
            >
              {PROMPT_DAYS.map((day) => (
                <option key={day.value} value={day.value}>
                  {day.label}
                </option>
              ))}
            </select>
          </div>
          <div className="flex items-center justify-between gap-3 border-t hairline py-2.5">
            <label htmlFor="dayEndHour" className="text-[14px]">
              Your day ends at
            </label>
            <div className="flex items-center gap-2">
              <input
                id="dayEndHour"
                name="dayEndHour"
                type="number"
                min={0}
                max={23}
                defaultValue={profile.dayEndHour}
                className="field w-[70px] text-right"
              />
              <span className="text-[13px] text-muted">:00, so late study counts for today</span>
            </div>
          </div>
          <div className="flex items-center justify-between gap-3 border-t hairline py-2.5">
            <label htmlFor="currency" className="text-[14px]">
              Currency
            </label>
            <input
              id="currency"
              name="currency"
              defaultValue={profile.currency ?? ""}
              className="field w-[100px] text-right"
              placeholder="KSh"
            />
          </div>
        </section>

        <section aria-labelledby="loves">
          <div className="mb-2 flex flex-wrap items-baseline justify-between gap-2 px-1">
            <h2 id="loves" className="text-[17px] font-semibold">
              What you love
            </h2>
            <span className="text-[13px] text-muted">Pick any. They become the language of your explanations.</span>
          </div>
          <div className="card p-5">
            <div className="pick" role="group" aria-label="Interests">
              {INTEREST_OPTIONS.map((option) => (
                <label key={option}>
                  <input type="checkbox" name="interests" value={option} defaultChecked={interests.includes(option)} />
                  {option}
                </label>
              ))}
            </div>
            <div className="mt-3 flex flex-col gap-1.5">
              <label htmlFor="customInterests" className="text-[13px] text-muted">
                Add your own, comma separated
              </label>
              <input
                id="customInterests"
                name="customInterests"
                defaultValue={customInterests.join(", ")}
                className="field"
                placeholder="e.g. birdwatching, salsa"
              />
            </div>
            <div className="mt-3 flex flex-col gap-1.5">
              <label htmlFor="thinkers" className="text-[13px] text-muted">
                Thinkers you return to, comma separated
              </label>
              <input
                id="thinkers"
                name="thinkers"
                defaultValue={(profile.thinkers ?? []).join(", ")}
                className="field"
                placeholder="e.g. Yuval Noah Harari"
              />
            </div>
          </div>
        </section>

        <section className="card flex flex-col gap-3 p-5" aria-labelledby="myway">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <h2 id="myway" className="text-[17px] font-semibold">
              Explain it my way
            </h2>
            <label className="flex cursor-pointer items-center gap-2.5 text-[14px] text-muted">
              Use my interests in explanations
              <input type="checkbox" name="analogiesEnabled" defaultChecked={profile.analogiesEnabled} className="peer sr-only" />
              <span aria-hidden="true" className="toggle-track" />
            </label>
          </div>
          <p className="text-[13px] text-muted">
            Today&rsquo;s staff card explains a topic through the first interest above, then the
            plain definition. Turn this off to see only the plain version.
          </p>
        </section>

        <section aria-labelledby="learn">
          <h3 id="learn" className="mb-2 px-1 text-[15px] font-semibold">
            How you like to learn
          </h3>
          <div className="card p-5">
            <div className="pick" role="group" aria-label="Learning preferences">
              {LEARNING_PREFERENCE_OPTIONS.map((option) => (
                <label key={option}>
                  <input
                    type="checkbox"
                    name="learningPreferences"
                    value={option}
                    defaultChecked={learningPreferences.includes(option)}
                  />
                  {option}
                </label>
              ))}
            </div>
          </div>
        </section>

        <button type="submit" className="btn-fill self-start px-5 py-2.5 text-[15px] font-semibold">
          Save profile
        </button>
      </form>
    </div>
  );
}
