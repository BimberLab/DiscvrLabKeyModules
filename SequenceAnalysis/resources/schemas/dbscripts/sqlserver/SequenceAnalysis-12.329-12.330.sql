CREATE NONCLUSTERED INDEX IDX_haplotypes_name_date ON sequenceanalysis.haplotypes
(
	Name ASC,
	datedisabled ASC
);

CREATE NONCLUSTERED INDEX IDX_haplotype_sequences_name_haplotype_type ON sequenceanalysis.haplotype_sequences
(
	haplotype ASC,
	name ASC,
	type ASC
);

CREATE NONCLUSTERED INDEX IDX_alignment_summary_analysis_id_rowid_container_total ON sequenceanalysis.alignment_summary
(
	analysis_id ASC,
	rowid ASC,
	container ASC
)
INCLUDE(total)

CREATE STATISTICS STAT_ref_nt_sequence_rowid_locus_container ON sequenceanalysis.ref_nt_sequences (RowId, locus, container)
WITH AUTO_DROP = OFF

CREATE STATISTICS STAT_ref_nt_sequence_locus_container ON sequenceanalysis.ref_nt_sequences (locus, container)
WITH AUTO_DROP = OFF

CREATE STATISTICS STAT_sequence_analyses_container_readset ON sequenceanalysis.sequence_analyses (Container, readset)
WITH AUTO_DROP = OFF

CREATE STATISTICS STAT_sequence_readsets_rowid_container ON sequenceanalysis.sequence_readsets (RowId, Container)
WITH AUTO_DROP = OFF

CREATE STATISTICS STAT_asj_alignmentid_container_ref_nt_id ON sequenceanalysis.alignment_summary_junction(alignment_id, container, ref_nt_id)
WITH AUTO_DROP = OFF
              
CREATE STATISTICS STAT_asj_alignmentid_ref_nt_id_status_alignment_id ON sequenceanalysis.alignment_summary_junction(ref_nt_id, status, alignment_id)
WITH AUTO_DROP = OFF

