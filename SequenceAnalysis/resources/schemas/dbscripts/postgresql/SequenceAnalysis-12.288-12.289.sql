DROP INDEX sequenceanalysis.IDX_alignment_summary_junction_status_alignment_id;

CREATE INDEX IDX_alignment_summary_junction_alignment_id_status ON sequenceanalysis.alignment_summary_junction (alignment_id, status);

--ALTER TABLE sequenceanalyses.sequence_readsets ADD platform_unit varchar(100);