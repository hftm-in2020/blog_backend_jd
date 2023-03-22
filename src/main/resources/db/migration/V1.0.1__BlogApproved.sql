ALTER TABLE Entry ADD COLUMN approved bool DEFAULT FALSE;
UPDATE Entry SET approved = true;
ALTER TABLE Entry MODIFY approved bool NOT NULL;