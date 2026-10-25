// The geometry and wording behind the owner's charts (components/charts/WatchtowerChart.tsx). Pure, so each rule
// can be tested without rendering anything.

export type Point = [number, number];
export interface ChartSeries {
  label: string;
  points: Point[];
}

export type Range = "1h" | "6h" | "24h";
const RANGES: Range[] = ["1h", "6h", "24h"];
export const DEFAULT_RANGE: Range = "6h";

// The backend refuses any range but these three. A page address with anything else falls back to the default
// instead of passing it on to be refused.
export function parseRange(value: string | string[] | undefined): Range {
  return typeof value === "string" && (RANGES as string[]).includes(value) ? (value as Range) : DEFAULT_RANGE;
}

// The next 1, 2 or 5 times a power of ten at or above the value: a clean top for the y axis. Never zero.
export function niceMax(value: number): number {
  if (!(value > 0)) return 1;
  const power = Math.pow(10, Math.floor(Math.log10(value)));
  const fraction = value / power;
  const step = fraction <= 1 ? 1 : fraction <= 2 ? 2 : fraction <= 5 ? 5 : 10;
  return step * power;
}

export function seriesExtent(series: ChartSeries[]): { tMin: number; tMax: number; vMax: number } | null {
  let tMin = Infinity;
  let tMax = -Infinity;
  let vMax = -Infinity;
  for (const s of series) {
    for (const [t, v] of s.points) {
      tMin = Math.min(tMin, t);
      tMax = Math.max(tMax, t);
      vMax = Math.max(vMax, v);
    }
  }
  return tMin === Infinity ? null : { tMin, tMax, vMax };
}

// The index of the time closest to t. A tie goes to the earlier one.
export function nearestIndex(times: number[], t: number): number {
  let best = 0;
  for (let i = 1; i < times.length; i++) {
    if (Math.abs(times[i] - t) < Math.abs(times[best] - t)) best = i;
  }
  return best;
}

function compact(value: number): string {
  if (Math.abs(value) >= 1000) return `${Number((value / 1000).toFixed(1))}K`;
  return String(Number(value.toFixed(1)));
}

// The unit comes from the backend's chart list: "s", "USD", "per hour", "credits", "failures".
export function formatValue(value: number, unit: string): string {
  if (unit === "s") return value < 1 ? `${Math.round(value * 1000)} ms` : `${Number(value.toFixed(2))} s`;
  if (unit === "USD") return `$${value.toFixed(2)}`;
  return compact(value);
}

// Hours and minutes in UTC, so the labels do not depend on the server's time zone.
export function clockLabel(epochSeconds: number): string {
  return new Date(epochSeconds * 1000).toISOString().slice(11, 16);
}

// Which line ends get a direct label. Labels run top to bottom in the order given (smallest y first); one that would
// sit closer than minGap to the last label kept is skipped, not nudged, because a nudged label drifts off its line.
export function labelledEnds(ys: number[], minGap: number): number[] {
  const order = ys.map((y, i) => [y, i] as const).sort((a, b) => a[0] - b[0]);
  const kept: number[] = [];
  let last = -Infinity;
  for (const [y, i] of order) {
    if (y - last >= minGap) {
      kept.push(i);
      last = y;
    }
  }
  return kept.sort((a, b) => a - b);
}

// The smallest time between two neighbouring points of any series: the query step, as the data shows it.
export function stepOf(series: ChartSeries[]): number {
  let step = Infinity;
  for (const s of series) {
    for (let i = 1; i < s.points.length; i++) step = Math.min(step, s.points[i][0] - s.points[i - 1][0]);
  }
  return step === Infinity ? 1 : step;
}

// Breaks a series where more than two steps pass between points. Prometheus leaves out a time it has no value for,
// and a line drawn across that hole would show values that were never measured. A lone point is a run of its own.
export function splitAtGaps(points: Point[], step: number): Point[][] {
  const runs: Point[][] = [];
  for (const point of points) {
    const run = runs[runs.length - 1];
    if (run && point[0] - run[run.length - 1][0] <= 2 * step) run.push(point);
    else runs.push([point]);
  }
  return runs;
}
