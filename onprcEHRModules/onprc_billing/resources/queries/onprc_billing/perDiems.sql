/*
 * Copyright (c) 2011-2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
--this query displays all animals co-housed with each housing record
--to be considered co-housed, they only need to overlap by any period of time

SELECT
    pd.Id,
    --note: we want the per diems to report using the last available day for this charge
    max(pd.date) as date,
    pd.project,
    pd.chargeId,
    group_concat(distinct pd.category) as categories,
    group_concat(distinct pd.overlappingProjects) as overlappingProjects,
    group_concat(distinct pd.tier) as tiers,

    sum(pd.effectiveDays) as effectiveDays,
    count(pd.Id) as totalDaysAssigned,
    min(pd.startDate) as startDate @hidden,
    group_concat(distinct pd.housingRecords) as housingRecords,
    group_concat(distinct pd.assignmentRecords) as assignmentRecords,
FROM onprc_billing.perDiemsByDay pd

GROUP BY pd.Id, pd.project, pd.chargeId