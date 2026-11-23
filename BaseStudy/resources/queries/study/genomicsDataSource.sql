SELECT

s1.subjectId as Id,
s1.date,

('Assay(s): ' || application) as description,
'ONPRC Genetics Core' as dataSource

FROM (SELECT
    s.subjectId,
    coalesce(s.sampleDate, now()) as date,
    GROUP_CONCAT(DISTINCT s.application, ', ') as application

    FROM "/Internal/ColonyData".sequenceanalysis.sequence_readsets s
    GROUP BY s.subjectId, s.sampleDate
) s1