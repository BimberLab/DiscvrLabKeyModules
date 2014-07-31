ALTER TABLE sequenceanalysis.reference_library_tracks ADD datedisabled datetime;
ALTER TABLE sequenceanalysis.reference_library_tracks ADD category varchar(200);
ALTER TABLE sequenceanalysis.reference_library_tracks DROP COLUMN type;