SELECT
  s.Id,
  group_concat(DISTINCT s.outcome, char(10)) as outcomes

FROM study.outcomes s
GROUP BY s.Id