SELECT
distinct category

FROM (

SELECT
distinct category
FROM sequenceanalysis.outputfiles

UNION ALL

SELECT 'VCF File' as category

UNION ALL

SELECT 'BAM File' as category

UNION ALL

SELECT 'BED File' as category

UNION ALL

SELECT 'gVCF File' as category

) t

WHERE category is not null