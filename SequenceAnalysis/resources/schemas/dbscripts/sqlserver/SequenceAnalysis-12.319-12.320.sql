ALTER TABLE sequenceanalysis.barcodes ADD reverse_complement varchar(4000);
ALTER TABLE sequenceanalysis.readdata ADD archived bit default 0;
GO
UPDATE sequenceanalysis.readdata SET archived = 0;