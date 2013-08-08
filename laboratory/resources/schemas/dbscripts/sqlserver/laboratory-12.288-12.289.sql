ALTER TABLE laboratory.workbook_tags DROP COLUMN created;
ALTER TABLE laboratory.workbook_tags DROP COLUMN createdby;

GO

ALTER TABLE laboratory.workbook_tags ADD created datetime;
ALTER TABLE laboratory.workbook_tags ADD createdby int;