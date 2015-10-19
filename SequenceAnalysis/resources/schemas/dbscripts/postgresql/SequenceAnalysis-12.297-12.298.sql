ALTER TABLE sequenceanalysis.ref_nt_sequences ADD COLUMN datedisabled timestamp;
ALTER TABLE sequenceanalysis.ref_nt_sequences ADD COLUMN disabledby varchar(1000);