DELETE FROM laboratory.samplecategory WHERE category = 'Empty Well';
DELETE FROM laboratory.samplecategory WHERE category = 'Blank';
DELETE FROM laboratory.samplecategory WHERE category = 'Sample';
DELETE FROM laboratory.samplecategory WHERE category = 'Standard';
DELETE FROM laboratory.samplecategory WHERE category = 'NTC';
DELETE FROM laboratory.samplecategory WHERE category = 'CTL';
DELETE FROM laboratory.samplecategory WHERE category = 'STD';
DELETE FROM laboratory.samplecategory WHERE category = 'Pos Control';
DELETE FROM laboratory.samplecategory WHERE category = 'Neg Control';
DELETE FROM laboratory.samplecategory WHERE category = 'Control';

INSERT INTO laboratory.samplecategory (category) VALUES ('Pos Control');
INSERT INTO laboratory.samplecategory (category) VALUES ('Neg Control');
INSERT INTO laboratory.samplecategory (category) VALUES ('Control');
INSERT INTO laboratory.samplecategory (category) VALUES ('Blank');
INSERT INTO laboratory.samplecategory (category) VALUES ('Standard');

ALTER TABLE laboratory.samplecategory ADD control bit;
GO
UPDATE laboratory.samplecategory SET control = 1;
UPDATE laboratory.samplecategory SET control = 0 where category = 'Unknown';