"use client";

import { useState } from "react";
import {
  clockLabel,
  formatValue,
  labelledEnds,
  nearestIndex,
  niceMax,
  seriesExtent,
  splitAtGaps,
  stepOf,
} from "@/lib/chart";
import type { WatchtowerChart as ChartData } from "@/lib/types";

// One chart on the owner's page (docs/observability.md, part 5). A line per series, a legend when there are two or
// more, a crosshair and one tooltip listing every series at the time the pointer is near, and a table of the same
// numbers under the chart. The colours are --series-1 to --series-8; the backend sends at most eight series.

const WIDTH = 720;
const HEIGHT = 240;
const LEFT = 52;
const RIGHT = 560;
const TOP = 12;
const BOTTOM = 200;
const LABEL_GAP = 14;

function shorten(label: string): string {
  return label.length > 24 ? `${label.slice(0, 23)}…` : label;
}

export function WatchtowerChart({ chart }: { chart: ChartData }) {
  const [hovered, setHovered] = useState<number | null>(null);
  const { series, unit } = chart;
  const extent = seriesExtent(series);

  // A colour belongs to a series name, not to its rank: the slot is its place among the names in order, so a series
  // that moves up or down the ranking keeps its colour.
  const slotOf = new Map([...series.map((s) => s.label)].sort().map((label, i) => [label, i]));
  const colorOf = (label: string) => `var(--series-${(slotOf.get(label) ?? 0) + 1})`;

  const header = (
    <div className="mb-2 flex flex-wrap items-baseline justify-between gap-x-3">
      <h3 className="text-[15px] font-semibold">{chart.title}</h3>
      <span className="text-[12px] text-muted">{unit}</span>
    </div>
  );

  if (extent === null) {
    return (
      <div className="card p-4">
        {header}
        <p className="text-[13px] text-muted">No data in this range.</p>
      </div>
    );
  }

  const yMax = niceMax(extent.vMax);
  const span = Math.max(1, extent.tMax - extent.tMin);
  const xFor = (t: number) => LEFT + ((t - extent.tMin) / span) * (RIGHT - LEFT);
  const yFor = (v: number) => BOTTOM - (v / yMax) * (BOTTOM - TOP);
  const times = Array.from(new Set(series.flatMap((s) => s.points.map(([t]) => t)))).sort((a, b) => a - b);
  const step = stepOf(series);
  const yTicks = [0, yMax / 2, yMax];
  const xTicks = [extent.tMin, extent.tMin + span / 2, extent.tMax].map(Math.round);

  const ends = series.map((s) => s.points[s.points.length - 1]);
  const labelled =
    series.length <= 4
      ? new Set(labelledEnds(ends.map((end) => (end ? yFor(end[1]) : Infinity)), LABEL_GAP))
      : new Set<number>();

  function at(t: number) {
    return series.map((s) => s.points.find(([pt]) => pt === t)?.[1] ?? null);
  }

  function move(clientX: number, box: DOMRect) {
    const x = ((clientX - box.left) / box.width) * WIDTH;
    const t = extent!.tMin + ((x - LEFT) / (RIGHT - LEFT)) * span;
    setHovered(nearestIndex(times, t));
  }

  const hoveredTime = hovered === null ? null : times[hovered];

  return (
    <div className="card p-4">
      {header}
      <div className="relative">
        <svg
          viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
          width="100%"
          role="img"
          tabIndex={0}
          aria-label={`${chart.title}. Use the left and right arrow keys to read values.`}
          style={{ display: "block" }}
          onPointerMove={(e) => move(e.clientX, e.currentTarget.getBoundingClientRect())}
          onPointerLeave={() => setHovered(null)}
          onBlur={() => setHovered(null)}
          onKeyDown={(e) => {
            if (e.key === "ArrowRight") setHovered((h) => Math.min(times.length - 1, (h ?? -1) + 1));
            else if (e.key === "ArrowLeft") setHovered((h) => Math.max(0, (h ?? times.length) - 1));
            else if (e.key === "Escape") setHovered(null);
          }}
        >
          <g stroke="var(--hairline)" strokeWidth={1}>
            {yTicks.map((tick) => (
              <path key={tick} d={`M${LEFT} ${yFor(tick)}H${RIGHT}`} />
            ))}
          </g>
          <g fontSize={11} fill="var(--muted)">
            {yTicks.map((tick) => (
              <text key={tick} x={LEFT - 8} y={yFor(tick) + 4} textAnchor="end">
                {tick === 0 ? "0" : formatValue(tick, unit)}
              </text>
            ))}
            {xTicks.map((t, i) => (
              <text key={t} x={xFor(t)} y={BOTTOM + 20} textAnchor={i === 0 ? "start" : i === 2 ? "end" : "middle"}>
                {clockLabel(t)}
              </text>
            ))}
          </g>
          {series.map((s) =>
            splitAtGaps(s.points, step).map((run, i) =>
              run.length === 1 ? (
                <circle key={`${s.label}-${i}`} cx={xFor(run[0][0])} cy={yFor(run[0][1])} r={2} fill={colorOf(s.label)} />
              ) : (
                <polyline
                  key={`${s.label}-${i}`}
                  points={run.map(([t, v]) => `${xFor(t)},${yFor(v)}`).join(" ")}
                  fill="none"
                  stroke={colorOf(s.label)}
                  strokeWidth={2}
                  strokeLinejoin="round"
                  strokeLinecap="round"
                />
              ),
            ),
          )}
          {series.map((s, i) => {
            const end = ends[i];
            if (!end) return null;
            return (
              <circle
                key={s.label}
                cx={xFor(end[0])}
                cy={yFor(end[1])}
                r={4}
                fill={colorOf(s.label)}
                stroke="var(--surface)"
                strokeWidth={2}
              />
            );
          })}
          <g fontSize={12} fill="var(--ink)">
            {series.map((s, i) => {
              const end = ends[i];
              if (!end || !labelled.has(i)) return null;
              return (
                <text key={s.label} x={RIGHT + 12} y={yFor(end[1]) + 4}>
                  {shorten(s.label)} &middot; {formatValue(end[1], unit)}
                </text>
              );
            })}
          </g>
          {hoveredTime !== null && (
            <path d={`M${xFor(hoveredTime)} ${TOP}V${BOTTOM}`} stroke="var(--muted)" strokeWidth={1} />
          )}
        </svg>
        {hoveredTime !== null && (
          <div
            role="status"
            className="pointer-events-none absolute top-2 rounded-[10px] bg-surface px-3 py-2 text-[12px] shadow-md"
            style={{
              left: `${(xFor(hoveredTime) / WIDTH) * 100}%`,
              transform: xFor(hoveredTime) > (LEFT + RIGHT) / 2 ? "translateX(calc(-100% - 10px))" : "translateX(10px)",
              border: "1px solid var(--hairline)",
            }}
          >
            <p className="mb-1 text-muted">{clockLabel(hoveredTime)} UTC</p>
            {series.map((s, i) => {
              const value = at(hoveredTime)[i];
              return (
                <p key={s.label} className="flex items-center gap-2">
                  <span aria-hidden="true" style={{ width: 12, height: 2, background: colorOf(s.label) }} />
                  <span className="font-semibold">{value === null ? "—" : formatValue(value, unit)}</span>
                  <span className="text-muted">{shorten(s.label)}</span>
                </p>
              );
            })}
          </div>
        )}
      </div>

      {series.length >= 2 && (
        <ul className="mt-2 flex flex-wrap gap-x-4 gap-y-1 text-[12px]">
          {series.map((s) => (
            <li key={s.label} className="flex items-center gap-1.5">
              <span aria-hidden="true" style={{ width: 12, height: 2, background: colorOf(s.label) }} />
              {s.label}
            </li>
          ))}
        </ul>
      )}

      {chart.truncated && (
        <p className="mt-2 text-[12px] text-muted">
          Showing {series.length} of {chart.totalSeries} series, the ones with the largest peaks.
        </p>
      )}

      <details className="mt-2">
        <summary className="cursor-pointer text-[12px] text-muted">Table</summary>
        <table className="mt-1 w-full text-left text-[12px]">
          <thead>
            <tr className="text-muted">
              <th className="py-1 font-medium">Series</th>
              <th className="py-1 font-medium">Latest</th>
              <th className="py-1 font-medium">Peak</th>
            </tr>
          </thead>
          <tbody>
            {series.map((s) => {
              const last = s.points[s.points.length - 1];
              const peak = s.points.length ? Math.max(...s.points.map(([, v]) => v)) : null;
              return (
                <tr key={s.label} className="border-t hairline">
                  <td className="py-1">{s.label}</td>
                  <td className="py-1">{last ? formatValue(last[1], unit) : "—"}</td>
                  <td className="py-1">{peak === null ? "—" : formatValue(peak, unit)}</td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </details>
    </div>
  );
}
