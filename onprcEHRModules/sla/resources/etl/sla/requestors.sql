Select 
rt.Lastname,
rt.firstname,
max(rt.initials) as initials,

max(
case rt.phonenumber
  when  '' then sl.RequestorPhone
  when  null then sl.RequestorPhone
  else rt.phonenumber
End) as phone,

rt.emailaddress as Email,

(SELECT max(userid) as userid FROM labkey.core.users u WHERE email =
case max(rt.emailaddress)
  when  '' then max(sl.RequestorEmail)
  when  null then max(sl.RequestorEmail)
  else max(rt.emailaddress)
End) as userid,
max(CAST(rt.objectid as varchar(36))) as objectid

From sla_purchase sl
LEFT JOIN Ref_Technicians rt ON (rt.id = sl.RequestorID and rt.DeptCode = 8)
and (rt.ts > ? OR sl.ts > ?)

GROUP BY rt.lastname, rt.firstname, rt.emailaddress
having max(sl.ts) > ?