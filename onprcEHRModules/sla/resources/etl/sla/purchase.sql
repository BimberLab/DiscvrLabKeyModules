
Select slp.ProjectID as Project,
UserId,
PriInvPhone,
PriInvEmail,
RequestorID,
VendorId,
Username,
OHSUAlias,
HazardousAgentsUsed ,
HazardsList ,
DOBRequired ,
AdditionalVendorInfo ,
OtherVendor ,
VendorContact ,
ConfirmationNum ,
HousingConfirmed ,
IACUCConfirmed ,
RequestDate  ,
OrderDate ,
AdminComments ,
DARComments ,
OrderedBy ,
ProjFundingSource ,
objectid

From SLA_Purchase slp
Where slp.ts > ?
