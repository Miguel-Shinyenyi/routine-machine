export interface Goal {
  id: number;
  name: string;
  description: string | null;
}

export type RoutineItemCategory = "BODY" | "MIND" | "WORK" | "HOME";

export type RoutineItemSource =
  | "MANUAL"
  | "HUB"
  | "WAKATIME"
  | "ACTIVITYWATCH"
  | "HEALTH"
  | "READER_NOTES"
  | "GITHUB"
  | "STRAVA"
  | "RESCUETIME";

export type HillsideField = "READING" | "MOVEMENT" | "DEEP_WORK" | "LEARNING" | "SLEEP" | "WRITING";

export interface RoutineItem {
  id: number;
  name: string;
  description: string | null;
  category: RoutineItemCategory | null;
  countRule: string | null;
  source: RoutineItemSource;
  hillsideField: HillsideField | null;
  isPrivate: boolean;
  goalId: number | null;
  goalName: string | null;
  createdAt: string;
}

export interface LearningTopic {
  id: number;
  name: string;
  goalId: number;
  goalName: string;
}

export interface DailyLog {
  id: number;
  logDate: string;
  routineItemId: number | null;
  learningTopicId: number | null;
  source: string;
  measuredMinutes: number | null;
}

export interface ScheduleTemplate {
  id: number;
  dayOfWeek: string;
  targetType: "ROUTINE_ITEM" | "LEARNING_SLOT";
  routineItemId: number | null;
  targetDurationMinutes: number;
  sortOrder: number;
  label: string;
}

export interface Task {
  id: number;
  scheduleTemplateId: number | null;
  taskDate: string;
  label: string;
  routineItemId: number | null;
  learningTopicId: number | null;
  status: "TODO" | "IN_PROGRESS" | "DONE";
  dailyLogId: number | null;
}

export interface CurrentReading {
  id: number;
  title: string;
  source: string;
}

export interface Suggestion {
  goal: { id: number; name: string };
  topic: { id: number; name: string };
  lastLoggedOn: string | null;
  reason: string;
}

export interface CompletionRate {
  routineItemId: number;
  name: string;
  loggedDays: number;
  totalDays: number;
  rate: number;
}

export interface Streak {
  routineItemId: number;
  name: string;
  currentStreak: number;
  longestStreak: number;
}

export interface SkipHeavyDay {
  date: string;
  completedCount: number;
  totalItems: number;
  skipRatio: number;
}

export interface TopicRoutineCorrelation {
  daysWithTopicLog: number;
  daysWithoutTopicLog: number;
  avgRoutineCompletionsOnTopicLogDays: number;
  avgRoutineCompletionsOnOtherDays: number;
}

export interface Patterns {
  completionRates: CompletionRate[];
  streaks: Streak[];
  skipHeavyDays: SkipHeavyDay[];
  topicRoutineCorrelation: TopicRoutineCorrelation;
}

export interface DayShownUp {
  date: string;
  shownUp: boolean;
}

export interface GoalWeekHours {
  goalId: number;
  weekStart: string;
  cumulativeHours: number;
}

export interface RoutineShownUp {
  routineItemId: number;
  name: string;
  shownUpDays: number;
  windowDays: number;
}

export interface Tally {
  daysShownUp: DayShownUp[];
  daysShownUpCount: number;
  goalWeekHours: GoalWeekHours[];
  routineShownUp: RoutineShownUp[];
}

export interface WateringHoleStatus {
  source: string;
  enabled: boolean;
  lastActivityAt: string | null;
  description: string;
}
