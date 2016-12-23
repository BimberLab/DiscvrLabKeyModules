PARAMETERS (ProjectFA DOUBLE DEFAULT .2)
SELECT n.category,
n.name,
(n.UnitCost *(1+(.47 - ProjectFA))) as CurrentYear,
(n.year1 *(1+(.47 - ProjectFA))) as Year1,
(n.year2 *(1+(.47 - ProjectFA))) as Year2,
(n.year3 *(1+(.47 - ProjectFA))) as Year3,
(n.year4 *(1+(.47 - ProjectFA))) as Year4,
(n.year5 *(1+(.47 - ProjectFA))) as Year5,
(n.year6 *(1+(.47 - ProjectFA))) as Year6,
(n.year7 *(1+(.47 - ProjectFA))) as Year7,
(n.year8 *(1+(.47 - ProjectFA))) as Year8,
n.PostedDate
FROM NIHRateConfig n