ALTER TABLE sequenceanalysis.barcodes ADD reverse_complement varchar(4000);
ALTER TABLE sequenceanalysis.readdata ADD archived bool default false;

UPDATE sequenceanalysis.readdata SET archived = false;