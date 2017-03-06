
SELECT room as location, datedisabled
From Site.{substitutePath moduleProperty('EHR','EHRStudyContainer')}.ehr_lookups.rooms
Where housingtype = 589 and dateDisabled is null