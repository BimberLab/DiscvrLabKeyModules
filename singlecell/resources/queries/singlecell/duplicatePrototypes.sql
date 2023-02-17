SELECT
    o.readset,
    min(o.rowId) as minRowId,
    min(o.analysis_id) as minAnalysisId,
    count(*) as totalPrototypes

FROM sequenceanalysis.outputfiles o
WHERE o.category = 'Seurat Object Prototype'
GROUP BY o.readset
HAVING COUNT(*) > 1