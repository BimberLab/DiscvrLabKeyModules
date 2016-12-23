Select    
sp.objectid as purchaseid,
--Species,
s1.Value as Species,
Age ,
Weight ,
Gestation ,
--Sex as gender,
s2.Value as Gender,
Strain,

--todo: do we need to translate this or lookup to a reference table?
--CageID,
rl.Location as Room,

NumAnimalsOrdered as animalsordered,
NumAnimalsReceived as animalsreceived,
BoxesQuantity,
CostPerAnimal,
ShippingCost,
TotalCost,
HousingInstructions,
RequestedArrivalDate,
ExpectedArrivalDate,
ReceivedDate,
ReceivedBy,
CancelledBy,
DateCancelled,
slp.objectid as objectid

From SLA_Purchasedetails slp
left join SLA_Purchase sp ON (slp.PurchaseID = sp.PurchaseID)
LEFT JOIN Sys_Parameters s1 on (slp.Species = s1.Flag And s1.Field = 'SmallAnimals')
LEFT JOIN Sys_Parameters s2 on (slp.Sex = s2.Flag And s2.Field = 'RodentSex')
LEFT JOIN Ref_RowCage rc on  (rc.CageID = slp.CageID)
LEFT JOIN Ref_Location rl on (rc.LocationID = rl.LocationId)

Where (slp.ts > ? OR sp.ts > ?)
