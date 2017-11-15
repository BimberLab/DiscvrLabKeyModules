SELECT si.AnimalId,
  si.Date,
  d.SampleId,
  d.Lab,
  d.Analyte,
  group_concat(d.Value) as assay_value
FROM snprc_r24.Biomarkers d
  inner join snprc_r24.SampleInventory as si on d.SampleId = si.SampleId
WHERE d.Analyte IS NOT NULL
GROUP BY si.AnimalId,
  d.SampleId,
  si.Date,
  d.Lab,
  d.Analyte
PIVOT assay_value BY Analyte