SELECT
distinct category

FROM (

SELECT
distinct category
FROM sequenceanalysis.outputfiles

UNION ALL

SELECT 'VCF File'

UNION ALL

SELECT 'BAM File'

UNION ALL

SELECT 'BED File'

UNION ALL

SELECT 'gVCF File'

) t

WHERE category is not null