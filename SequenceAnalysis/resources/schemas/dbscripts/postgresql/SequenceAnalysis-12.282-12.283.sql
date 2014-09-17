ALTER TABLE sequenceanalysis.sequence_readsets ADD sampletype VARCHAR(200);

CREATE TABLE sequenceanalysis.input_material (
  material varchar(1000),

  CONSTRAINT PK_input_material PRIMARY KEY (material)
);

INSERT INTO sequenceanalysis.input_material (material) VALUES ('gDNA');
INSERT INTO sequenceanalysis.input_material (material) VALUES ('RNA');

DELETE FROM sequenceanalysis.sequence_applications WHERE application = 'Other';