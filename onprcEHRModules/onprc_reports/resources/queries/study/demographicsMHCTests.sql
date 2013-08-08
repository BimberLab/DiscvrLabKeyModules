SELECT
  s.Id,
  count(s.Id) as totalTests,
  group_concat(DISTINCT s.type) as types

FROM MHC_Data.MHC_Data_Unified s
GROUP BY s.Id