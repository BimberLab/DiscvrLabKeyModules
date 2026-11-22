SELECT
  s.Id,
  count(s.Id) as totalTests,
  group_concat(DISTINCT s.assayType) as assayTypes,

  --special case A01/B17/B08
  max(CASE
    WHEN (s.marker = 'Mamu-A1*001g' AND (s.result = 'POS' OR s.result = 'NEG')) THEN s.result
    ELSE null
  END) as A01,

  max(CASE
      WHEN (s.marker = 'Mamu-A1*002g' AND (s.result = 'POS' OR s.result = 'NEG')) THEN s.result
      ELSE null
  END) as A02,

  max(CASE
    WHEN (s.marker = 'Mamu-B*008g' AND (s.result = 'POS' OR s.result = 'NEG')) THEN s.result
    ELSE ''
  END) as B08,

  max(CASE
    WHEN (s.marker = 'Mamu-B*017g' AND (s.result = 'POS' OR s.result = 'NEG')) THEN s.result
    ELSE ''
  END) as B17,
  GROUP_CONCAT(distinct CASE WHEN s.result = 'POS' THEN s.marker ELSE null END, char(10)) as allAlleles
FROM study.genetics s
WHERE s.category = 'MHC Typing'
GROUP BY s.Id