ALTER TABLE sequenceanalysis.alignment_summary_junction DROP COLUMN jobid;
ALTER TABLE sequenceanalysis.ref_nt_sequences ADD jobid integer;
