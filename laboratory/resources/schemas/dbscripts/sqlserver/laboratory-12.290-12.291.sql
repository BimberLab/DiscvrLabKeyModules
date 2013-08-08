ALTER TABLE laboratory.sample_type DROP CONSTRAINT PK_sample_type;
GO
ALTER TABLE laboratory.sample_type ADD rowid int identity(1,1);
GO
ALTER TABLE laboratory.sample_type ADD CONSTRAINT PK_sample_type PRIMARY KEY (rowid);