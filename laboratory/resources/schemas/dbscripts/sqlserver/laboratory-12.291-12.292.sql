EXEC sp_rename 'laboratory.samples.quantity', 'quantity_string', 'COLUMN'
GO
ALTER TABLE laboratory.samples ADD quantity double precision;

