SELECT
  s.subjectId as Id,
  count(s.subjectId) as totalSamples,
  group_concat(DISTINCT s.sampleType, chr(10)) as sampleTypes,

FROM DNA_Bank.samples s
GROUP BY s.subjectId