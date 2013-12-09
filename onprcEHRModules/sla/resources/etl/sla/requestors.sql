Select distinct
sl.RequestorId,
rt.DeptCode,
rt.Lastname, rt.firstname, rt.initials,

case rt.phonenumber
when  '' then sl.RequestorPhone
when  null then sl.RequestorPhone
else rt.phonenumber
End PhoneNumber,

case rt.emailaddress
when  '' then sl.RequestorEmail
when  null then sl.RequestorEmail
else rt.emailaddress
End emailaddress,

rt.objectid,
ActiveFrom as Created,
InactiveFrom as Modified

From Ref_Technicians  rt, sla_purchase sl Where rt.id = sl.RequestorID and rt.DeptCode =8
and (rt.ts > ? OR sl.ts > ?)
