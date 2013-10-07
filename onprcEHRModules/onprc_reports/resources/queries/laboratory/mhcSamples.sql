SELECT
s.subjectId,
s.sampletype,
s.quantity

FROM laboratory.samples s
WHERE s.container = 'ACBC81ED-CD41-1030-85AB-5107380A2FD4' AND dateremoved is null