-- // cave_insert_trigger
-- Migration SQL that makes the change goes here.

CREATE OR REPLACE FUNCTION prehistoric_cave_insert_function ()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO proletarian.job(
        job_type, payload
    )
    VALUES (
        ':prehistoric.cave/insert', row_to_json(NEW)
    );
    return NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER prehistoric_cave_insert_trigger
AFTER INSERT ON prehistoric.cave
FOR EACH ROW
EXECUTE PROCEDURE prehistoric_cave_insert_function();


-- //@UNDO
-- SQL to undo the change goes here.


DROP TRIGGER prehistoric_cave_insert_trigger ON prehistoric.cave;
DROP FUNCTION prehistoric_cave_insert_function;