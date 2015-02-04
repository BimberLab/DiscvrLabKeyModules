ALTER TABLE jbrowse.databases ADD primarydb boolean;
ALTER TABLE jbrowse.databases ADD createOwnIndex boolean;

UPDATE jbrowse.databases SET primarydb = false;
UPDATE jbrowse.databases SET createOwnIndex = false;
UPDATE jbrowse.databases SET temporary = false;