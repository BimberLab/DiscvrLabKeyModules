ALTER TABLE laboratory.workbook_tags DROP COLUMN created;
ALTER TABLE laboratory.workbook_tags DROP COLUMN createdby;

ALTER TABLE laboratory.workbook_tags ADD created timestamp;
ALTER TABLE laboratory.workbook_tags ADD createdby int;