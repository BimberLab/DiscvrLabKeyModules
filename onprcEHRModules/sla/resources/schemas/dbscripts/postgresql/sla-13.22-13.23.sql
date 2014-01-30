ALTER TABLE sla.census DROP CONSTRAINT PK_Census;

ALTER TABLE sla.census ALTER COLUMN objectid SET NOT NULL;

ALTER TABLE sla.census DROP COLUMN rowid;

ALTER TABLE sla.census ADD CONSTRAINT PK_Census PRIMARY KEY (objectid);