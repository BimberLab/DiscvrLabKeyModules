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

CREATE INDEX IDX_alignment_summary_analysis_id_rowid_container ON sequenceanalysis.alignment_summary
(
	analysis_id ASC,
	rowid ASC,
	container ASC
)