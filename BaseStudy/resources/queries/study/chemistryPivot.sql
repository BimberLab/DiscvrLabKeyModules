SELECT
    b.Id,
    b.date,
    b.method,
    b.test,
    group_concat(b.result) as result

FROM (SELECT
         b.Id,
         b.date,
         b.test,
         b.method,
         CASE
             WHEN b.result IS NULL THEN  b.qualresult
             ELSE CAST(CAST(b.result AS float) AS VARCHAR)
             END as result
     FROM study.labwork b
     WHERE b.test.category = 'Chemistry' AND b.test.sort_order != 999
) b

GROUP BY b.id, b.date, b.test, b.method
PIVOT result BY test IN (select value from studies.labwork_types t WHERE t.sort_order != 999 AND t.category = 'Chemistry' order by sort_order)


