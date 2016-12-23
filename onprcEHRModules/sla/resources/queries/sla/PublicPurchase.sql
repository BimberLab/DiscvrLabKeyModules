SELECT p.rowid,
p.objectid,
p.project,
p.project.name as projectname,
p.project.protocol as protocol,
p.project.protocol.external_id as eIACUCNum,
p.project.investigatorid.lastname || ', ' || p.project.investigatorid.firstname AS investigator,
p.account,
p.requestorid,
p.requestorid.lastname || ', ' || p.requestorid.firstname as requestor,
p.vendorid,
p.vendorid.name as vendor,
p.hazardslist,
p.dobrequired,
p.darcomments,
p.comments,
p.confirmationnum,
p.vendorcontact,
p.housingconfirmed,
p.iacucconfirmed,
p.created as requestdate,
p.orderdate,
p.orderedby
FROM sla.purchase p
WHERE (
  (SELECT max(rowid) as expr FROM onprc_billing.dataAccess da
    -- current logged in user is the dataAccess user
    WHERE isMemberOf(da.userid)
    -- has access to all data
    AND (da.allData = true
    -- has access to the specified investigatorId and the specified project (if applicable)
      OR (da.investigatorId = p.project.investigatorId AND (da.project IS NULL OR da.project = p.project)))
  ) IS NOT NULL
)
