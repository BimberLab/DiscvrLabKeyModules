--DB FKs are too restrictive
ALTER TABLE laboratory.samples DROP CONSTRAINT fk_samples_samplespecies;
ALTER TABLE laboratory.samples DROP CONSTRAINT fk_samples_additive;
ALTER TABLE laboratory.samples DROP CONSTRAINT fk_samples_molecule_type;
ALTER TABLE laboratory.subjects DROP CONSTRAINT fk_subjects_gender;
ALTER TABLE laboratory.subjects DROP CONSTRAINT fk_subjects_samplespecies;
ALTER TABLE laboratory.subjects DROP CONSTRAINT fk_subjects_geographic_origin;

--this is a quick change, but in the long run I think consistency of column names will be valuable
--adding container means rows will be auto-deleted on delete of the parent container
ALTER TABLE laboratory.workbooks ADD container entityid;
ALTER TABLE laboratory.workbooks ADD parentContainer varchar(36);
ALTER TABLE laboratory.workbooks ADD containerRowId integer;
GO
UPDATE laboratory.workbooks SET container = folder;
UPDATE laboratory.workbooks SET parentContainer = (select c.parent from core.containers c where container = entityid);
UPDATE laboratory.workbooks SET containerRowId = (select c.rowid from core.containers c where container = entityid);
GO
ALTER TABLE laboratory.workbooks ALTER COLUMN container entityid not null;
GO
ALTER TABLE laboratory.workbooks DROP CONSTRAINT PK_workbooks;
ALTER TABLE laboratory.workbooks DROP COLUMN folder;
ALTER TABLE laboratory.workbooks ADD CONSTRAINT PK_workbooks PRIMARY KEY (container);
