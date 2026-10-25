import { notFound } from "next/navigation";
import { fetchServices, serviceTone, type Tone } from "@/lib/watchtower";

// Owner-only (docs/observability.md, part 5). No redirect to /login and no link in the sidebar: to anyone but the
// owner this page does not exist, so a signed-out visitor and a signed-in stranger both get the plain 404 page.
// The backend decides who the owner is; this page only passes its answer on.

const TONE_CLASS: Record<Tone, string> = {
  good: "chip-tended",
  bad: "chip-tint",
  neutral: "",
};

const TONE_WORD: Record<Tone, string> = {
  good: "Up",
  bad: "Down",
  neutral: "Not configured",
};

export default async function WatchtowerPage() {
  const services = await fetchServices();
  if (services === null) {
    notFound();
  }

  return (
    <div className="flex flex-col gap-8">
      <div>
        <p className="text-[15px] font-semibold text-tint-text">Watchtower</p>
        <h1 className="page-title">Is everything running.</h1>
        <p className="mt-2 max-w-[62ch] text-[17px] text-muted">
          Only you can see this page. Health, metrics, logs and sync status will sit here.
        </p>
      </div>

      <section aria-labelledby="services">
        <h2 id="services" className="mb-2.5 px-1 text-[17px] font-semibold">
          Services
        </h2>
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {services.map((service) => {
            const tone = serviceTone(service.status);
            return (
              <div key={service.name} className="card flex flex-col gap-2 p-4">
                <h3 className="text-[16px] font-semibold">{service.name}</h3>
                <span className={`chip self-start ${TONE_CLASS[tone]}`}>{TONE_WORD[tone]}</span>
              </div>
            );
          })}
        </div>
      </section>
    </div>
  );
}
