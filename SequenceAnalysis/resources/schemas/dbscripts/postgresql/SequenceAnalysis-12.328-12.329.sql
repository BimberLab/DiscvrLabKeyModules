CREATE INDEX IDX_asj_status_container_alignment_id_ref_nt_id ON sequenceanalysis.alignment_summary_junction (
	status ASC,
	container ASC,
	alignment_id ASC,
	ref_nt_id ASC
);

CREATE INDEX IDX_readData_readset ON sequenceanalysis.readData (
	readset ASC
);

CREATE INDEX IDX_quality_metrics_metricname_dataid_readset ON sequenceanalysis.quality_metrics (
	metricName ASC,
	dataId ASC,
	readset ASC
);

