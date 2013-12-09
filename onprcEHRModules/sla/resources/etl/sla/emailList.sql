
Select Name,
Email,
PrimaryNotifier,
objectid,
DateCreated as Created,
DateDisabled as Modified

From Ref_SLAEmailList el
Where ( el.ts > ?)
