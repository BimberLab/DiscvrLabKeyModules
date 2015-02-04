ALTER TABLE jbrowse.databases ADD primarydb bit;
ALTER TABLE jbrowse.databases ADD createOwnIndex bit;
GO
UPDATE jbrowse.databases SET primarydb = 0;
UPDATE jbrowse.databases SET createOwnIndex = 0;
UPDATE jbrowse.databases SET temporary = 0;