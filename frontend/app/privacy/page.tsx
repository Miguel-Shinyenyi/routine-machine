import type { Metadata } from "next";
import { AnalyticsChoice } from "@/components/AnalyticsChoice";
import { analyticsConfigured } from "@/lib/analytics/consent";
import { EVENT_DISCLOSURE, regionOf } from "@/lib/analytics/disclosure";

export const metadata: Metadata = { title: "Privacy and analytics" };

// The public privacy page (staged Prompt 5, part 2): what the app's analytics collects, why, where it goes, and how to stop it. Open to
// everyone, signed in or not. It is written from lib/analytics/disclosure.ts, which a test holds equal to the list of events the app may
// send, and from the deployment's own PostHog host, so it cannot describe a different setup from the one that is running. With analytics
// switched off it says that and nothing else.
export default function PrivacyPage() {
  const host = process.env.POSTHOG_HOST ?? "";
  const on = analyticsConfigured(process.env.POSTHOG_KEY, host);
  const region = on ? regionOf(host) : null;
  const contact = (process.env.PRIVACY_CONTACT ?? "").trim();
  const sentToday = Object.entries(EVENT_DISCLOSURE).filter(([, entry]) => entry.sentToday);
  const notYet = Object.entries(EVENT_DISCLOSURE).filter(([, entry]) => !entry.sentToday);

  return (
    <div className="flex max-w-[70ch] flex-col gap-8">
      <div>
        <p className="text-[15px] font-semibold text-tint-text">Privacy</p>
        <h1 className="page-title">What we count, and how to stop it.</h1>
        <p className="mt-2 text-[17px] text-muted">
          This page is about one thing: the product analytics this app can run to learn how people use it. What you write in the app, such as
          goals, notes and amounts, is kept in this app&rsquo;s own database and is never sent to the analytics service.
        </p>
      </div>

      {!on ? (
        <section aria-labelledby="off" className="card p-4">
          <h2 id="off" className="mb-1 text-[17px] font-semibold">
            Analytics is switched off
          </h2>
          <p className="text-[15px]">On this deployment nothing is counted and nothing is sent to any analytics service.</p>
        </section>
      ) : (
        <>
          <section aria-labelledby="choice">
            <h2 id="choice" className="mb-2.5 text-[17px] font-semibold">
              Your choice
            </h2>
            <AnalyticsChoice />
            <p className="mt-2 text-[14px] text-muted">
              Nothing is counted unless you accept. You can change your mind here at any time. If your browser sends Do Not Track or Global
              Privacy Control, nothing is counted and you are not asked.
            </p>
          </section>

          <section aria-labelledby="counted">
            <h2 id="counted" className="mb-2.5 text-[17px] font-semibold">
              What is counted, and when
            </h2>
            <ul className="flex flex-col gap-3">
              {sentToday.map(([name, entry]) => (
                <li key={name} className="card p-4">
                  <p className="text-[15px] font-semibold">{entry.when}</p>
                  <p className="mt-1 text-[14px]">{entry.carries}</p>
                  <p className="mt-1 text-[12px] text-muted">Event name: {name}</p>
                </li>
              ))}
            </ul>
          </section>

          {notYet.length > 0 && (
          <section aria-labelledby="notyet">
            <h2 id="notyet" className="mb-2.5 text-[17px] font-semibold">
              Named actions, not sent yet
            </h2>
            <p className="mb-2.5 text-[14px] text-muted">
              The app is only able to send the events on this page, and these are not connected yet. When one is, this is all it carries.
            </p>
            <ul className="flex flex-col gap-2">
              {notYet.map(([name, entry]) => (
                <li key={name} className="text-[14px]">
                  <span className="font-semibold">{entry.when}</span> {entry.carries}
                </li>
              ))}
            </ul>
          </section>
          )}

          <section aria-labelledby="never">
            <h2 id="never" className="mb-2.5 text-[17px] font-semibold">
              What we never send
            </h2>
            <p className="text-[15px]">
              Your email address, your name, anything you write, any amount of money, or the text of a note. We do not record your screen, your
              clicks or how far you scroll. The random device number is kept in your browser&rsquo;s local storage,
              not in a cookie, and you can clear it at any time. The one cookie involved is a short note called{" "}
              <span className="font-mono text-[14px]">rm_events</span>: when something you do succeeds, the app leaves the name of the action
              in it so that your browser can send it if you accepted. It holds no personal data, it expires in two minutes, and your browser
              deletes it at once, whether or not you accepted.
            </p>
          </section>

          <section aria-labelledby="why">
            <h2 id="why" className="mb-2.5 text-[17px] font-semibold">
              Why, and who receives it
            </h2>
            <p className="text-[15px]">
              To see which pages people use and where they get stuck, so the app can be improved. It is not used for advertising and it is not
              sold. It is sent to PostHog, our analytics provider, at <span className="font-mono text-[14px]">{host}</span>
              {region ? <>, which keeps it in {region}, outside Kenya</> : null}.
            </p>
          </section>

          <section aria-labelledby="rights">
            <h2 id="rights" className="mb-2.5 text-[17px] font-semibold">
              Your rights
            </h2>
            <p className="text-[15px]">
              Kenya&rsquo;s Data Protection Act, 2019 applies. You can ask to see the data held about you, to have it corrected or deleted, and
              to object to how it is used, and you can withdraw your consent at any time on this page.{" "}
              {contact ? <>To ask, contact {contact}.</> : <>To ask, contact the person who runs this app.</>}
            </p>
          </section>
        </>
      )}
    </div>
  );
}
