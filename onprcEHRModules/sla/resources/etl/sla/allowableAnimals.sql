SELECT * FROM (

select
CASE WHEN t.protocolId IS NULL THEN project WHEN t.protocolId = t.project THEN null ELSE t.project END as project,
CASE WHEN t.protocolId = project THEN RTRIM(ltrim(i2.IACUCCode)) ELSE null END as protocol,
t.species,
t.gender,
t.allowed,
t.startdate,
t.endDate,
t.objectid

from (

select 
y.ProjectID as project,
(select top 1 ipc.projectparentid
  from Ref_ProjectsIACUC rpi2 join Ref_IACUCParentChildren ipc on (rpi2.ProjectID = ipc.ProjectParentID and ipc.datedisabled is null)
  where ipc.projectchildid = r.projectid order by ipc.datecreated desc
) as protocolId,

(select max(rpi2.ts) as maxTs
 from Ref_ProjectsIACUC rpi2 join Ref_IACUCParentChildren ipc on (rpi2.ProjectID = ipc.ProjectParentID and ipc.datedisabled is null)
 where ipc.projectchildid = r.projectid
) as maxTs,
y.CurrentYearStartDate as startdate,
y.CurrentYearEndDate as enddate,
--a.Species,
sp.Value as species,
a.Strain,
a.Sex,
s.Value as gender,
a.Age,
a.NumAnimals as allowed,
a.Remarks as comment,
a.objectid

from IACUC_SLAYearly y
join IACUC_SLAAnimals a on (y.SLAYearlyID = a.SLAYearlyID)
join ref_ProjectsIACUC r on (r.ProjectID = y.ProjectID)
left join Sys_Parameters sp on (sp.field = 'SmallAnimals' and sp.Flag = a.Species)
left join Sys_Parameters s on (s.field = 'RodentSex' and s.Flag = a.Sex)

where (y.DateDisabled is null and a.DateDisabled is Null) and y.CurrentYearEndDate > '1/1/2010'
and (y.ts > ? OR a.ts > ? OR r.ts > ?)

) t

LEFT JOIN Ref_ProjectsIACUC i2 ON (i2.ProjectID = t.protocolId)

WHERE (maxTs > ? or maxTs IS NULL)

) t

WHERE (t.project IS NOT NULL OR t.protocol IS NOT NULL)