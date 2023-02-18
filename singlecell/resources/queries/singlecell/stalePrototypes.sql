SELECT * FROM (
SELECT
    t.*,
    CASE WHEN t.created < GREATEST(latestLoupeFile, latestHashing, latestCiteSeq) THEN true ELSE false END as isStale
FROM (
    SELECT
    o.rowId,
    o.analysis_id,
    o.name,
    o.readset,
    o.readset.status,
    o.readset.cDNA,
    o.readset.cDNA.readsetId,
    o.created,
    (SELECT max(l.created) as latestLoupeFile FROM sequenceanalysis.outputfiles l WHERE l.category = '10x Loupe File' AND l.readset = o.readset) as latestLoupeFile,
    (SELECT max(h.created) as latestHashing FROM sequenceanalysis.outputfiles h WHERE h.category = 'Cell Hashing Counts' AND h.readset = o.readset.cDNA.hashingReadsetId) as latestHashing,
    (SELECT max(cs.created) as latestCiteSeq FROM sequenceanalysis.outputfiles cs WHERE cs.category = 'Cite-seq Counts' AND cs.readset = o.readset.cDNA.citeseqReadsetId) as latestCiteSeq

    FROM sequenceanalysis.outputfiles o
    WHERE o.category = 'Seurat Object Prototype'
) t
) t2
WHERE t2.isStale = true