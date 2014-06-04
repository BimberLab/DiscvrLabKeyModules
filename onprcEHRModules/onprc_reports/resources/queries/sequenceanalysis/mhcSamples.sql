SELECT
s.subjectId,
s.sampletype,
s.quantity

FROM laboratory.samples s
WHERE s.container = '97D34AF9-CC91-1031-BD48-5107380A722C' AND dateremoved is null