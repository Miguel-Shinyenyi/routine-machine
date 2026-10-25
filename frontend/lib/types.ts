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

export type MpesaCategory =
  | "FOOD"
  | "TRANSPORT"
  | "FAMILY_SUPPORT"
  | "DATA_AND_AIRTIME"
  | "LEARNING"
  | "BETTING"
  | "OVERDRAFT_FEES"
  | "OTHER";

export interface MpesaImportResponse {
  statementMonth: string;
  transactionsParsed: number;
  categoryTotals: Partial<Record<MpesaCategory, number>>;
}

export interface CohortMeasure {
  count: number;
  denominator: number;
}

export interface CohortDashboardSummary {
  activatedWithinSevenDays: CohortMeasure;
  checkedInThisWeek: CohortMeasure;
  returnedAfterAMissedDay: CohortMeasure;
  feltSupportive: CohortMeasure;
  saidNeutral: number;
  saidPressuring: number;
}

export interface CohortGroupMeasure {
  studyGroup: string;
  memberCount: number;
  hidden: boolean;
  checkedInThisWeek: CohortMeasure | null;
  returnedAfterAMissedDay: CohortMeasure | null;
}

export interface CohortWeeklyCheckIn {
  week: number;
  checkedIn: number | null;
  isCurrentWeek: boolean;
  isFuture: boolean;
}

export interface CohortWeeklyCheckInChart {
  activatedDenominator: number;
  weeks: CohortWeeklyCheckIn[];
}

export type CohortSurveyAnswer = "SUPPORTIVE" | "NEUTRAL" | "PRESSURING";

// The owner-only operations page (docs/observability.md, part 5). Status is UP, DOWN or NOT_CONFIGURED.
export interface WatchtowerService {
  name: string;
  status: string;
}

// One row of the owner's sync table. Result is off, push only, unknown, failing, ok or never succeeded. A source
// that has not run has null failures in a row and no last success, which is not the same as zero failures.
export interface WatchtowerSyncSource {
  source: string;
  enabled: boolean;
  lastActivityAt: string | null;
  result: string;
  consecutiveFailures: number | null;
  lastSuccessAt: string | null;
  successes24h: number;
  failures24h: number;
}

export interface WatchtowerSync {
  available: boolean;
  sources: WatchtowerSyncSource[];
}

// The owner's charts. A chart carries at most 8 series, the ones with the largest peaks; totalSeries is how many
// there were and truncated says some were left out. A point is [seconds since 1970, value].
export interface WatchtowerChart {
  id: string;
  title: string;
  unit: string;
  series: { label: string; points: [number, number][] }[];
  totalSeries: number;
  truncated: boolean;
}

export interface WatchtowerMetrics {
  available: boolean;
  range: string;
  charts: WatchtowerChart[];
}

// The owner's logs: the newest 100 lines of the backend and the intelligence service, reduced to these fields.
// traceUrl is set only when the line carries a trace id and the backend knows where traces are shown.
export interface WatchtowerLogEntry {
  time: string;
  service: string;
  level: string | null;
  message: string;
  requestId: string | null;
  traceId: string | null;
  traceUrl: string | null;
}

export interface WatchtowerLogs {
  available: boolean;
  range: string;
  entries: WatchtowerLogEntry[];
}
