select  a.label as description,
        a.value as value,
        a.sort_order as sort_order
 from  sla.Reference_Data a
 where a.columnname = 'ObservationScore'
 And a. enddate is null
order by sort_order
