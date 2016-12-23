SELECT
c.category,
c.itemcode as Item,
c.name,
b.UnitCost,
c.activerate.subsidy,
b.year1,
b.year2,
b.year3,
b.year4,
b.year5,
b.year6,
b.year7,
b.year8,
b.PostedDate
FROM NIHRateCalc_base b
join chargeableItems c on b.item = c.rowid