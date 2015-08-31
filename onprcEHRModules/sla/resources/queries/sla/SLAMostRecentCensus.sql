/*
 * Copyright (c) 2011 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

  /*   Created  Blasa    2-6-2015  Provide most SLA Census */

  select
  a.project [project], a.Investigatorid [Investigator], rtrim(a.room) [room],
  a.species [Species], a.cagetype [Cage_Type], a.cageSize [Cage_Size], a.date [Date] ,a.animalcount [Animal_Count],
  a.cagecount [Cage_Count] , now() [recentdate] from  sla.census a

Where a.date in (select max(b.date) from  sla.census b where  a.project = b.project
And a.species = b.species and a.cageSize = b.cageSize and a.Cagetype = b.cageType )
And  timestampdiff('SQL_TSI_DAY', a.date, now())  <  15

group by a.project, a.species, a.cagetype, a.cageSize, a.room,a.date,a.animalcount, a.cagecount, a.Investigatorid
order by   a.room  , a.date desc



