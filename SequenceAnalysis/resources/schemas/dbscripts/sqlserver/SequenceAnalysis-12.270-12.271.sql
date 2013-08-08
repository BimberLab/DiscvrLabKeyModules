ALTER TABLE sequenceanalysis.nt_snps_by_pos ADD pvalue double precision;

ALTER TABLE sequenceanalysis.sequence_analyses ADD synopsis varchar(4000);
ALTER TABLE sequenceanalysis.sequence_analyses ADD description varchar(4000);