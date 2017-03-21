
SELECT
'[Update order]' AS updatelink,
'[Print view]' AS printviewlink,
p.rowid,
p.projectname,
p.protocol,
p.investigator,
p.requestorid,
p.requestor,
p.vendor,
pd.species,
pd.gender,
pd.strain,
pd.weight,
pd.gestation,
pd.room,
pd.animalsordered,
p.requestdate,
pd.expectedarrivaldate,
pd.housingInstructions,
p.confirmationnum,
p.housingconfirmed,
p.orderdate,
pd.sla_DOB,
pd.vendorLocation,
pd.receiveddate,
pd.receivedby,
pd.datecancelled,
pd.cancelledby

FROM PublicPurchase p, PublicPurchaseDetails pd
WHERE p.objectid = pd.purchaseid

