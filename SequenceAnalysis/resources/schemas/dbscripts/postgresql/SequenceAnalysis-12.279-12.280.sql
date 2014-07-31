ALTER TABLE sequenceanalysis.reference_library_tracks ADD datedisabled timestamp;
ALTER TABLE sequenceanalysis.reference_library_tracks ADD category varchar(200);
ALTER TABLE sequenceanalysis.reference_library_tracks DROP COLUMN type;