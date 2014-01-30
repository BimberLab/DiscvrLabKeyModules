Select
SLAVendorName as name,
Phone1 ,
Phone2,
FundingSourceRequired,
Comments,
cast(vl.objectid as varchar(36)) as objectid

From Ref_SLAVendors vl
Where ( vl.ts > ?)
