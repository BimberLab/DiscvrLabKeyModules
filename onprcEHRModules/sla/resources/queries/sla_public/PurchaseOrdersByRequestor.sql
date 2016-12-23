SELECT requestorid,
requestor,
SUM(CASE WHEN confirmationnum IS NULL AND datecancelled IS NULL THEN 1 ELSE 0 END) AS pendingorders,
COUNT(rowid) AS numberoforders
FROM PurchaseOrderDetails
GROUP By requestorid, requestor