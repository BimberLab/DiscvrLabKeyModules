Select
slp.ProjectID as Project,
OHSUAlias as account,
req.objectid as RequestorID,
v1.objectid as VendorId,

--skipping
--UserId,
--PriInvPhone,
--PriInvEmail,

Username,

--HazardousAgentsUsed ,
HazardsList,
DOBRequired,
AdditionalVendorInfo as comments,
AdminComments ,
DARComments ,
--OtherVendor ,
VendorContact ,

ConfirmationNum ,
HousingConfirmed ,
IACUCConfirmed ,
RequestDate  ,
OrderDate ,
OrderedBy ,
--ProjFundingSource ,
slp.objectid

From SLA_Purchase slp
LEFT JOIN Ref_Technicians req ON (req.id = slp.RequestorID and req.DeptCode = 8)
LEFT JOIN Ref_SLAVendors v1 ON (v1.SLAVendorID = slp.vendorid)
Where (slp.ts > ? or v1.ts > ?)