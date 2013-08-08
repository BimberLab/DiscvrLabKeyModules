ALTER TABLE laboratory.sample_type DROP CONSTRAINT PK_sample_type;

ALTER TABLE laboratory.sample_type ADD rowid serial;

ALTER TABLE laboratory.sample_type ADD CONSTRAINT PK_sample_type PRIMARY KEY (rowid);