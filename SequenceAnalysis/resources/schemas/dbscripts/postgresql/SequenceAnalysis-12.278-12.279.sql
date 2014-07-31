SELECT core.executeJavaUpgradeCode('migrateSequenceField');

ALTER TABLE sequenceanalysis.ref_nt_sequences DROP COLUMN sequence;
ALTER TABLE sequenceanalysis.ref_nt_sequences DROP COLUMN status;

ALTER TABLE sequenceanalysis.nt_snps DROP CONSTRAINT fk_nt_snps_alignment;
DROP TABLE sequenceanalysis.sequence_reads;
DROP TABLE sequenceanalysis.sequence_alignments;
DROP TABLE sequenceanalysis.aa_snps;
DROP TABLE sequenceanalysis.nt_snps;