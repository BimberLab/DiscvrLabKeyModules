SELECT
rowid,
owner.DisplayName AS owner,
created
FROM sla.purchaseDrafts
WHERE owner = userid()