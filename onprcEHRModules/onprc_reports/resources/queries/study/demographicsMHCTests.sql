SELECT
  s.Id,
  count(s.Id) as totalTests,
  group_concat(DISTINCT s.type) as types,

  --special case A01/B17/B08
  max(CASE
    WHEN (s.allele = 'Mamu-A1*001g' AND (s.result = 'POS' OR s.result = 'NEG')) THEN s.result
    ELSE null
  END) as A01,

  max(CASE
    WHEN (s.allele = 'Mamu-B*008g' AND (s.result = 'POS' OR s.result = 'NEG')) THEN s.result
    ELSE ''
  END) as B08,

  max(CASE
    WHEN (s.allele = 'Mamu-B*017g' AND (s.result = 'POS' OR s.result = 'NEG')) THEN s.result
    ELSE ''
  END) as B17,
FROM MHC_Data.MHC_Data_Unified s
GROUP BY s.Id