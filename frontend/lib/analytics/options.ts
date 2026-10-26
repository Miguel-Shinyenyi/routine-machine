import { sanitizeEvent } from "./sanitize";

// What the analytics library is told when it starts, after a visitor has accepted (staged Prompt 5, part 1). Each line is a
// privacy decision, and lib/analytics/options.test.ts holds them in place. The project key and host come from the deployment's
// environment (POSTHOG_KEY, POSTHOG_HOST), never from a file in the repo.
export function posthogOptions(host: string) {
  return {
    api_host: host.trim(),
    // Only the events the app names, and pageviews. No click or form capture, no session replay (it needs its own masking
    // decision, a later part), no surveys, and no feature-flag requests.
    autocapture: false,
    disable_session_recording: true,
    disable_surveys: true,
    advanced_disable_flags: true,
    // A pageview on each move between pages, and no person profile until someone is identified (by internal id only).
    capture_pageview: "history_change" as const,
    // Not asked for: PostHog's page-leave event with scroll statistics, found in a real visit. And every event is filtered once more
    // just before it leaves the browser (lib/analytics/sanitize.ts): only the named events, no query strings, no page title.
    capture_pageleave: false,
    disable_scroll_properties: true,
    before_send: sanitizeEvent,
    person_profiles: "identified_only" as const,
    // Honour Do Not Track inside the library too, and keep its identifier in local storage and not in a cookie. Never
    // `cookieless_mode`: its 'on_reject' setting still counts a visitor who said no.
    respect_dnt: true,
    persistence: "localStorage" as const,
  };
}
