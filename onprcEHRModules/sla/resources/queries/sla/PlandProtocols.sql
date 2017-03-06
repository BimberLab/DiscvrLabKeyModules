
SELECT a.project as ProjectID,
a.account as Alias,
y.grantNumber as OGAGrantNumber,
a.protocol as ParentIACUC,
a.title as Title,
a.name as IACUCCode,
a.startdate as StartDate,
a.enddate as EndDate,
i.FirstName,
i.LastName,
i.Division,
p.external_id,
i.LastName + ': ' + a.name + '('+ p.external_id +')' + ' - ' + a.title as PIIacuc
FROM Site.{substitutePath moduleProperty('EHR','EHRStudyContainer')}.ehr.project a
LEFT JOIN Site.{substitutePath moduleProperty('EHR','EHRStudyContainer')}.ehr.protocol p ON p.protocol = a.protocol
LEFT JOIN onprc_ehr.investigators i ON i.rowId = a.investigatorId
LEFT JOIN Site.{substitutePath moduleProperty('EHR','EHRStudyContainer')}.sla.allowableAnimals aa ON a.protocol = aa.protocol
LEFT JOIN "/onprc/admin/finance/public".onprc_billing_public.aliases y ON y.alias = a.account
WHERE
  -- filter based on the current date compared with the start and end dates
  (
    (aa.StartDate IS NOT NULL AND aa.EndDate IS NULL AND now() > aa.StartDate) OR
    (aa.StartDate IS NULL AND aa.EndDate IS NOT NULL AND now() < aa.EndDate) OR
    (now() between aa.StartDate AND aa.EndDate)
  )
  -- and filtered based on dataAccess for the given user
  AND
  (
    (SELECT max(rowid) as expr FROM onprc_billing.dataAccess da
      -- current logged in user is the dataAccess user
      WHERE isMemberOf(da.userid)
      -- has access to all data
      AND (da.allData = true
      -- has access to the specified investigatorId and the specified project (if applicable)
        OR (da.investigatorId = i.rowId AND (da.project IS NULL OR da.project = a.project)))
    ) IS NOT NULL
  )
