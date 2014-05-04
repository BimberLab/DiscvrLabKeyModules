/*
 * Copyright (c) 2010-2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

SELECT
    CAST(dates.dateOnly as timestamp) as date,
    dates.dateOnly @hidden,
    dates.lastCensusDate,
    dates.daysBetweenCensus,

    c2.project,
    c2.cagetype,
    c2.cagesize,
    c2.species,
    c2.room,
    c2.counttype,
    c2.animalcount,
    c2.cagecount,
    c2.objectid as sourceRecord,
    pdf.chargeid,

    dates.startDate as startDate @hidden,
    dates.endDate as endDate @hidden,
FROM (
  SELECT
    i.dateOnly,
    max(CAST(c.date as date)) as lastCensusDate,
    TIMESTAMPDIFF('SQL_TSI_DAY', CAST(max(CAST(c.date as date)) AS TIMESTAMP), i.dateOnly) as daysBetweenCensus,
    min(i.startDate) as startDate @hidden,
    min(i.endDate) as endDate @hidden
  FROM ldk.dateRange i
  --find the most recent census, on or before this date.  note: we dont allow a census more than 14 days old.  the odd CASTing is done for postgres
  --the intent is to leave blank lines for any date where we cannot find an appropriate census, which should get flagged downstream
  LEFT JOIN sla.census c ON (c.dateOnly <= i.dateOnly AND TIMESTAMPDIFF('SQL_TSI_DAY', CAST(c.dateOnly AS TIMESTAMP), CAST(i.dateOnly AS TIMESTAMP)) < 14)
  GROUP BY i.dateOnly
) dates

--now join to that census record
LEFT JOIN sla.census c2 ON (CAST(c2.date AS DATE) = CAST(dates.lastCensusDate as DATE))

LEFT JOIN Site.{substitutePath moduleProperty('EHR','EHRStudyContainer')}.onprc_billing.slaPerDiemFeeDefinition pdf
ON (
  pdf.cagetype = c2.cagetype AND
  pdf.cagesize = c2.cagesize AND
  pdf.species = c2.species AND
  pdf.active = true
)

WHERE (c2.cagecount > 0 OR dates.lastCensusDate IS NULL)