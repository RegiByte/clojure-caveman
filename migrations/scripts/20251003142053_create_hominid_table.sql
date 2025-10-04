-- // create_hominid_table
-- Migration SQL that makes the change goes here.

CREATE TABLE prehistoric.hominid (
    id uuid NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    "name" text,
    cave_id uuid REFERENCES prehistoric.cave(id)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT
);

CREATE TRIGGER set_prehistoric_hominid_updated_at
BEFORE UPDATE ON prehistoric.hominid
FOR EACH ROW
EXECUTE PROCEDURE prehistoric.set_current_timestamp_updated_at();

CREATE INDEX idx_prehistoric_hominid_cave_id 
ON prehistoric.hominid
USING btree (cave_id);

-- //@UNDO
-- SQL to undo the change goes here.

DROP TABLE prehistoric.hominid;