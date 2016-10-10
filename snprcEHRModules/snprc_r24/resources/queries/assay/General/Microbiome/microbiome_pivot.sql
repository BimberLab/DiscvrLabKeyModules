SELECT d.AnimalId,
	d.Date,
	d.UiId,
	d.GrcId,
	d.SampleId,
	d.Otu,
	group_concat(d.Value) as assay_value
FROM assay.General.Microbiome.Data d
WHERE d.Otu IS NOT NULL
GROUP BY d.AnimalId,
	d.Date,
	d.UiId,
	d.GrcId,
	d.SampleId,
	d.Otu,
	d.Value
PIVOT assay_value BY Otu
