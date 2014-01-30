Select    

sp.objectid as purchaseid,
Species,
Age as ageString,
Weight as weightString,
Gestation as gestationString,
Sex as gender,
Strain,

--todo: do we need to translate this or lookup to a reference table?
CageID,

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
Where (slp.ts > ? OR sp.ts > ?)
