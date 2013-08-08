ALTER TABLE laboratory.workbooks ADD exptGroup varchar(1000);
ALTER TABLE laboratory.workbooks ADD materials text;
ALTER TABLE laboratory.workbooks ADD methods text;
ALTER TABLE laboratory.workbooks ADD results text;

ALTER TABLE laboratory.peptides ADD modification varchar(1000);

CREATE TABLE laboratory.workbook_group_members (
  rowid serial,
  groupname varchar(200),
  workbook entityid,
  container entityid,
  created int,
  createdby timestamp,
  modified int,
  modifiedby timestamp,

  CONSTRAINT pk_workbook_group_members PRIMARY KEY (rowid)
);

DELETE FROM laboratory.sample_type  WHERE type = 'Brain';
INSERT INTO laboratory.sample_type (type) VALUES ('Brain');

DELETE FROM laboratory.sample_type  WHERE type = 'Buffy coat';
INSERT INTO laboratory.sample_type (type) VALUES ('Buffy coat');

DELETE FROM laboratory.sample_type  WHERE type = 'Spleen';
INSERT INTO laboratory.sample_type (type) VALUES ('Spleen');

DELETE FROM laboratory.sample_additive  WHERE additive = 'Filter Paper';
INSERT INTO laboratory.sample_additive (additive) VALUES ('Filter Paper');

ALTER TABLE laboratory.workbooks ADD folder entityid;

UPDATE laboratory.workbooks SET folder = container;

ALTER TABLE laboratory.workbooks DROP CONSTRAINT PK_workbooks;
ALTER TABLE laboratory.workbooks DROP COLUMN container;
ALTER TABLE laboratory.workbooks ADD CONSTRAINT PK_workbooks PRIMARY KEY (folder);


