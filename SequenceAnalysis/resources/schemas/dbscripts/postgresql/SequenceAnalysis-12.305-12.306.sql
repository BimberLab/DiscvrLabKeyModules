ALTER TABLE sequenceanalysis.readdata ADD runid integer;

UPDATE sequenceanalysis.readdata SET runid = (SELECT runid from sequenceanalysis.sequence_readsets WHERE sequence_readsets.rowid = readdata.readset);
