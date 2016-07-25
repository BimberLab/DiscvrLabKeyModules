SELECT
v.patientId,
count(*) as totalVariants,
SUM(CASE WHEN v.filterCall = 'Somatic' THEN 1 ELSE 0 END) as totalMissenseVariants


FROM study.variants v
GROUP BY v.patientId