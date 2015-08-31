Select
rf.ProjectID as Project,
 CountDate as date,
(select max(cast(i.objectid as varchar(36))) from labkey.onprc_ehr.investigators i where i.firstname = ri.firstname and i.lastname = ri.lastname group by i.LastName, i.firstname having count(*) <= 1) as InvestigatorId,

rl.Location as Room,
s1.Value as Species,
s2.Value as CageType,
s3.Value as CageSize,
afs.CountType,
afs.AnimalCount,
afs.CageCount,
afs.DLAMInventory,
cast(afs.objectid as varchar(36)) as objectid ,
18   as QCState

From Af_SmallLabAnimals afs
LEFT JOIN vw_SLAProjects rf ON (rf.ProjectID = afs.IACUCID )
LEFT JOIN Ref_Investigator ri on (ri.InvestigatorID = afs.investigatorid)
LEFT JOIN Ref_RowCage rc on  (rc.CageID = afs.CageID)
LEFT JOIN Ref_Location rl on (rc.LocationID = rl.LocationId)
LEFT JOIN Sys_Parameters s1 on (afs.Species = s1.Flag And s1.Field = 'SmallAnimals')
LEFT JOIN Sys_Parameters s2 on (afs.CageType  = s2.Flag And s2.Field = 'SmallCageType')
LEFT JOIN Sys_Parameters s3 on (afs.CageSize  = s3.Flag And s3.Field = 'SmallCageSize')

Where  rl.Location != 'No Location'
and (afs.ts > ? OR rc.ts > ? OR rl.ts > ?)
