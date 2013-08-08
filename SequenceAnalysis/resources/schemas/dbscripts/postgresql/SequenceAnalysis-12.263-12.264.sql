ALTER TABLE sequenceanalysis.sequence_readsets DROP COLUMN inputMaterial;
DROP TABLE sequenceanalysis.input_material;
ALTER TABLE sequenceanalysis.sequence_readsets ADD inputMaterial varchar(1000);