ALTER TABLE sequenceanalysis.analysisSetMembers ADD outputFileId int;

ALTER TABLE sequenceanalysis.analysisSets DROP COLUMN name;
ALTER TABLE sequenceanalysis.analysisSets ADD name varchar(200);
