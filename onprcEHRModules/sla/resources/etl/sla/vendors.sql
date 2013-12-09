Select
SLAVendorName,
Phone1 ,
Phone2,
FundingSourceRequired,
Comments,
objectid,
DateCreated as Created,
DateDisabled as Modified

From Ref_SLAVendors vl
Where ( vl.ts > ?)
