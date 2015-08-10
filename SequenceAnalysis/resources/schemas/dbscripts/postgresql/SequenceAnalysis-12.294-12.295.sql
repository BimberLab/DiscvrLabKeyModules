ALTER TABLE sequenceanalysis.haplotype_sequences RENAME COLUMN lineage TO name;

ALTER TABLE sequenceanalysis.haplotype_sequences ADD type VARCHAR(100);
UPDATE sequenceanalysis.haplotype_sequences SET type = 'Lineage';