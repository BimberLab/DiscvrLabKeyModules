--there should not have been time user any users to add rows, but guard anyway
DELETE FROM laboratory.reference_peptides WHERE rowid in
 (SELECT min(rowid) FROM laboratory.reference_peptides GROUP BY sequence HAVING COUNT(*) > 1);

ALTER TABLE laboratory.reference_peptides DROP CONSTRAINT PK_reference_peptides;
ALTER TABLE laboratory.reference_peptides DROP COLUMN rowid;
alter table laboratory.reference_peptides ALTER COLUMN sequence SET not null;
ALTER TABLE laboratory.reference_peptides ADD CONSTRAINT PK_reference_peptides PRIMARY KEY (sequence);


