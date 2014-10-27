SELECT
s.Id,
s.date,
s.project,
s.procedureId

FROM onprc_ssu.schedule s
LEFT JOIN Site.{substitutePath moduleProperty('EHR','EHRStudyContainer')}.study.encounters e
  ON (s.Id = e.Id AND s.procedureId = e.procedureId AND CAST(s.date AS DATE) = e.dateOnly)
WHERE e.lsid IS NULL
