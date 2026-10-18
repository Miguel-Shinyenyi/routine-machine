export interface Goal {
  id: number;
  name: string;
  description: string | null;
  userId: number | null;
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
  userId: number | null;
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

export interface Book {
  id: number;
  title: string;
  author: string | null;
  currentChapter: number | null;
  totalChapters: number | null;
  source: string;
  createdAt: string;
}

export interface ReadingNote {
  id: number;
  bookId: number;
  bookTitle: string;
  noteText: string;
  chapterPlaced: string | null;
  ownWordsPercentage: number | null;
  ideasConnected: string[] | null;
  quotedPhrases: string[] | null;
  questionBack: string | null;
  createdAt: string;
}

export interface ReadingNoteAssessment {
  chapterPlaced: string;
  ownWordsPercentage: number;
  ideasConnected: string[];
  quotedPhrases: string[];
  questionBack: string;
}

export interface HillsideNeighbor {
  rank: number;
  pseudonym: string;
  daysShownUp: number;
  isYou: boolean;
}

export interface Hillside {
  totalParticipants: number;
  histogram: number[];
  yourDaysShownUp: number | null;
  yourRank: number | null;
  band: string | null;
  neighbors: HillsideNeighbor[];
}

export interface CurrentUser {
  id: number;
  email: string;
  displayName: string | null;
  hillsideOptIn: boolean;
}

// Phase 9: the credit-based hillside.
export interface NeighborCreditRow {
  rank: number;
  pseudonym: string;
  credits: number;
  daysShownUp: number;
  isYou: boolean;
}

export interface HillsideV2 {
  totalParticipants: number;
  yourScore: number | null;
  yourDaysShownUp: number | null;
  yourRank: number | null;
  yourGoalCount: number | null;
  band: string | null;
  cohortReturns: number;
  neighbors: NeighborCreditRow[];
}

export type EvidenceStatus = "ACCEPTED" | "REJECTED" | "PENDING" | "SELF_REPORTED";

// Phase 10: profile data. Never fetched from or sent to anything cohort-/admin-facing — see
// docs/backend.md's decisions log.
export interface Profile {
  interests: string[] | null;
  thinkers: string[] | null;
  learningPreferences: string[] | null;
  currency: string | null;
  dayEndHour: number;
  timeZone: string;
  analogiesEnabled: boolean;
}

export interface AnalogyResponse {
  topic: string;
  interest: string;
  analogy: string;
  definition: string;
  label: string;
}

export interface EvidenceEvent {
  id: number;
  userId: number;
  goalId: number;
  goalName: string;
  source: string;
  sourceEventId: string;
  occurredAt: string;
  localDate: string;
  minutes: number | null;
  status: EvidenceStatus;
  scoringRuleVersion: number;
  reason: string;
  questioned: boolean;
}

// Phase 11: the granary. Never reaches the hillside or any admin view — see
// docs/backend.md's decisions log.
export interface GranaryMonthSummary {
  storedThisMonth: number;
  targetThisMonth: number;
  daysStored: number;
  elapsedDays: number;
  totalStoredEver: number;
  jar: boolean[];
  savingTargetAmount: number | null;
  currency: string | null;
}

export type PauseOutcome = "DECLINED" | "PROCEEDED";

export interface PauseDecision {
  id: number;
  what: string;
  amount: string | null;
  why: string | null;
  heardFrom: string | null;
  outcome: PauseOutcome | null;
  decidedAt: string | null;
  followUp24hAt: string;
  followUp30dAt: string;
  reflectionThirtyDay: string | null;
  dueForTwentyFourHourFollowUp: boolean;
  dueForThirtyDayFollowUp: boolean;
}
