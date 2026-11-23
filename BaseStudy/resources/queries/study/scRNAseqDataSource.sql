SELECT

s1.subjectId as Id,
s1.date,

('Assay(s): ' || assayType || char(10) ||
'Tissue(s): ' || tissue || char(10) ||
'Stims(s): ' || stims || char(10)) as description,
'Bimber Lab' as dataSource

FROM (SELECT
    s.subjectId,
    s.sampleDate as date,
    GROUP_CONCAT(DISTINCT s.tissue, ', ') as tissue,
    GROUP_CONCAT(DISTINCT s.assayType, ', ') as assayType,
    GROUP_CONCAT(DISTINCT s.stim, ', ') as stims

    FROM "/Labs/Bimber".singlecell.samples s
    GROUP BY s.subjectId, s.sampleDate
) s1