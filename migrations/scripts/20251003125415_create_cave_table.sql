-- // create_cave_table
-- Migration SQL that makes the change goes here.

CREATE SCHEMA prehistoric;

CREATE FUNCTION prehistoric.set_current_timestamp_updated_at()
RETURNS TRIGGER AS $$
DECLARE
  _new record;
BEGIN
  _new := NEW;
  _new."updated_at" = NOW();
  RETURN _new;
END;
$$ LANGUAGE plpgsql;

CREATE TABLE prehistoric.cave (
  id uuid NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  description TEXT NOT NULL
);

CREATE TRIGGER set_prehistoric_cave_updated_at
BEFORE UPDATE ON prehistoric.cave
FOR EACH ROW
EXECUTE PROCEDURE prehistoric.set_current_timestamp_updated_at();

-- //@UNDO
-- SQL to undo the change goes here.


DROP TABLE prehistoric.cave;
DROP FUNCTION prehistoric.set_current_timestamp_updated_at;
DROP SCHEMA prehistoric;


