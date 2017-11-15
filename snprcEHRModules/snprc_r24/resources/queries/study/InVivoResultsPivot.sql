SELECT ivr.AnimalId,
  ivr.Date,
  ivr.Aim,
  ivr.DataType,
  group_concat(ivr.Value) as assay_value
FROM study.InVivoResults ivr
WHERE ivr.DataType IS NOT NULL
GROUP BY ivr.AnimalId,
  ivr.Aim,
  ivr.Date,
  ivr.DataType
PIVOT assay_value BY DataType
order by Date desc, AnimalId asc, Aim asc
