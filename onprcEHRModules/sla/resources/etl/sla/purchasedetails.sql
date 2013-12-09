Select    
PurchaseID,
Species,
Age,
Weight,
Gestation,
Sex,
Strain,
CageID,
NumAnimalsOrdered,
NumAnimalsReceived,
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
objectid 

From SLA_Purchasedetails slp
Where slp.ts > ?
