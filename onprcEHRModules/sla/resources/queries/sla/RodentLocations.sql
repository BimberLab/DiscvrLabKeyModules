
SELECT room as location, datedisabled
From "/onprc/ehr".ehr_lookups.rooms
Where housingtype = 589 and dateDisabled is null