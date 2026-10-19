// Screenshot the running app at the same routes and widths as the design reference, then
// write side-by-side images (reference left, app right) to docs/design-reference/compare/.
//
// Usage, from the frontend folder with the app running on :3000:
//   npm i -D playwright pngjs && npx playwright install chromium
//   node ../docs/design-reference/compare.mjs http://localhost:3000
//
// Side-by-side images are for looking at, not a pass/fail diff: sample data on the reference
// will never match real records, so compare layout, type, spacing, colour and states.

import { chromium } from "playwright";
import { PNG } from "pngjs";
import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const base = (process.argv[2] ?? "http://localhost:3000").replace(/\/$/, "");
const out = join(here, "compare");
mkdirSync(out, { recursive: true });

// [reference screenshot, app route, viewport width]
const PAIRS = [
  ["today-1440.png", "/today", 1440],
  ["path-1440.png", "/path", 1440],
  ["flock-1440.png", "/flock", 1440],
  ["tally-1440.png", "/tally", 1440],
  ["reading-1440.png", "/reading", 1440],
  ["hillside-1440.png", "/hillside", 1440],
  ["watering-holes-1440.png", "/watering-holes", 1440],
  ["granary-1440.png", "/granary", 1440],
  ["profile-1440.png", "/profile", 1440],
  ["cohort-1440.png", process.env.COHORT_ROUTE ?? "/admin/cohorts/1", 1440],
  ["phone-today.png", "/today", 390],
  ["phone-reading.png", "/reading", 390],
  ["phone-hillside.png", "/hillside", 390],
  ["phone-granary.png", "/granary", 390],
];

function sideBySide(leftBuf, rightBuf) {
  const a = PNG.sync.read(leftBuf);
  const b = PNG.sync.read(rightBuf);
  const gap = 40;
  const out = new PNG({ width: a.width + gap + b.width, height: Math.max(a.height, b.height) });
  out.data.fill(255);
  PNG.bitblt(a, out, 0, 0, a.width, a.height, 0, 0);
  PNG.bitblt(b, out, 0, 0, b.width, b.height, a.width + gap, 0);
  return PNG.sync.write(out);
}

const browser = await chromium.launch();
for (const [ref, route, width] of PAIRS) {
  const scale = width < 600 ? 2 : 1;
  const ctx = await browser.newContext({ viewport: { width, height: 900 }, deviceScaleFactor: scale });
  const page = await ctx.newPage();
  try {
    await page.goto(base + route, { waitUntil: "networkidle" });
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
    const shot = await page.screenshot({ fullPage: true });
    const name = ref.replace(".png", "") + "--vs-app.png";
    writeFileSync(join(out, name), sideBySide(readFileSync(join(here, "screenshots", ref)), shot));
    console.log(`${route} @${width}: wrote compare/${name}${overflow > 0 ? `  WARNING: ${overflow}px horizontal overflow` : ""}`);
  } catch (err) {
    console.log(`${route} @${width}: FAILED (${err.message.split("\n")[0]})`);
  }
  await ctx.close();
}
await browser.close();
