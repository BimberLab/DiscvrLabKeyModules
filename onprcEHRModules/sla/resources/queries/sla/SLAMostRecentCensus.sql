/*
 * Copyright (c) 2011 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

  /*   Created  Blasa    2-6-2015  Provide most SLA Census   Modified 8-28-2015 */

select
  a.project [project],  a.Investigatorid [Investigator],  rtrim(a.room) [room],
  a.species [Species], a.cagetype [Cage_Type], a.cageSize [Cage_Size], a.date [Date] ,
  Case a.cagecount
      when  0 then 0
      else a.animalcount
      end [Animal_Count],
  a.cagecount [Cage_Count] , now() [recentdate] from  sla.census a

Where a.date in (select max(b.date) from  sla.census b where  a.project = b.project
And a.species = b.species and a.cageSize = b.cageSize and a.Cagetype = b.cageType and a.room = b.room and b.cagecount >0 AND timestampdiff('SQL_TSI_DAY', b.date, now())  <  6)
And  timestampdiff('SQL_TSI_DAY', a.date, now())  <  6



group by a.project, a.Investigatorid, a.species, a.cagetype, a.cageSize, a.room,a.date,a.animalcount, a.cagecount
order by   a.room  , a.date desc




