-- Staged Prompt 7: the day of the week the weekly failure-mode question starts appearing on Today, chosen by the person on their Profile.
-- A day name from java.time.DayOfWeek. Monday for everyone until they choose, which is how the question behaved before the setting existed.
ALTER TABLE users ADD COLUMN failure_mode_prompt_day VARCHAR(10) NOT NULL DEFAULT 'MONDAY';
