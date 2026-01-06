CREATE INDEX IDX_haplotypes_name_date ON sequenceanalysis.haplotypes
(
	Name ASC,
	datedisabled ASC
);

CREATE INDEX IDX_haplotype_sequences_name_haplotype_type ON sequenceanalysis.haplotype_sequences
(
	haplotype ASC,
	name ASC,
	type ASC
);