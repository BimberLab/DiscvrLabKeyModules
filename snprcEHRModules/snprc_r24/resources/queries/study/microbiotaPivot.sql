SELECT d.AnimalId,
	d.Date,
	d.Barcode,
	d.SpecimenId,
	d.Otu,
	group_concat(d.Value) as assay_value
FROM study.Microbiota d
WHERE d.Otu IS NOT NULL
GROUP BY d.AnimalId,
	d.Date,
	d.Barcode,
	d.SpecimenId,
	d.Otu
PIVOT assay_value BY Otu
