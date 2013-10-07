SELECT
s.subjectId,
s.sampletype,
s.quantity

FROM laboratory.samples s
WHERE s.container = '4696CCDD-CB09-1030-8D66-5107380A00F7' AND dateremoved is null