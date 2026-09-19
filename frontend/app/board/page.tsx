import Link from "next/link";
import { materializeDay } from "@/lib/materialize";
import { todayIsoDate } from "@/lib/schedule";
import type { Task } from "@/lib/types";
import { TaskCard } from "@/components/TaskCard";

const COLUMNS: { status: Task["status"]; label: string }[] = [
  { status: "TODO", label: "To Do" },
  { status: "IN_PROGRESS", label: "In Progress" },
  { status: "DONE", label: "Done" },
];

function adjacentDate(isoDate: string, deltaDays: number): string {
  const [year, month, day] = isoDate.split("-").map(Number);
  const date = new Date(Date.UTC(year, month - 1, day));
  date.setUTCDate(date.getUTCDate() + deltaDays);
  return date.toISOString().slice(0, 10);
}

export default async function BoardPage(props: PageProps<"/board">) {
  const searchParams = await props.searchParams;
  const rawDate = searchParams.date;
  const date = typeof rawDate === "string" ? rawDate : todayIsoDate();

  const tasks = await materializeDay(date);

  return (
    <div>
      <div className="mb-6 flex items-center justify-between">
        <Link href={`/board?date=${adjacentDate(date, -1)}`} className="text-sm hover:underline">
          &larr; Previous day
        </Link>
        <h1 className="text-lg font-semibold">{date}</h1>
        <Link href={`/board?date=${adjacentDate(date, 1)}`} className="text-sm hover:underline">
          Next day &rarr;
        </Link>
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        {COLUMNS.map((column) => (
          <div key={column.status}>
            <h2 className="mb-2 text-sm font-medium text-black/60 dark:text-white/60">
              {column.label}
            </h2>
            <div className="flex flex-col gap-2">
              {tasks
                .filter((task) => task.status === column.status)
                .map((task) => (
                  <TaskCard key={task.id} task={task} />
                ))}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
