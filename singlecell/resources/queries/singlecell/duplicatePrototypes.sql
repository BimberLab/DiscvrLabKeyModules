SELECT
    o.readset,
    min(o.rowId) as minRowId,
    min(o.analysis_id) as minAnalysisId,
    count(*) as totalPrototypes,
    o.category

FROM sequenceanalysis.outputfiles o
WHERE o.category = 'Seurat Object Prototype'
GROUP BY o.readset, o.category
HAVING COUNT(*) > 1

UNION ALL

SELECT
    o.readset,
    min(o.rowId) as minRowId,
    min(o.analysis_id) as minAnalysisId,
    count(*) as totalPrototypes,
    o.category

FROM sequenceanalysis.outputfiles o
WHERE o.category = '10x Loupe File'
GROUP BY o.readset, o.category
HAVING COUNT(*) > 1