SELECT
freezerid,
subjectid,
sampletype,
samplesubtype,
samplesource,
processdate,
concentration,
concentration_units,
quantity,
quantity_units,
comment
FROM laboratory.samples
WHERE dateremoved IS NULL