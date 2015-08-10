EXEC sp_rename 'sequenceanalysis.haplotype_sequences.lineage', 'name', 'COLUMN';
GO
ALTER TABLE sequenceanalysis.haplotype_sequences ADD type VARCHAR(100);
GO
UPDATE sequenceanalysis.haplotype_sequences SET type = 'Lineage';