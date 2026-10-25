-- Demo profile (staged Prompt 6): marks the one clearly-labelled sample account. Every real account is false.
ALTER TABLE users ADD COLUMN demo BOOLEAN NOT NULL DEFAULT FALSE;
