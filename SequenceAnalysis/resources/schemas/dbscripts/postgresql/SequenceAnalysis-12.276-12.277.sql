CREATE TABLE sequenceanalysis.sequence_applications (
  application varchar(200),

  CONSTRAINT PK_sequence_applications PRIMARY KEY (application)
);

ALTER TABLE sequenceanalysis.sequence_readsets add column application varchar(200);

INSERT INTO sequenceanalysis.sequence_applications (application) values ('RNA-seq');
INSERT INTO sequenceanalysis.sequence_applications (application) values ('DNA Sequencing (Genome)');
INSERT INTO sequenceanalysis.sequence_applications (application) values ('DNA Sequencing (Amplicon)');
INSERT INTO sequenceanalysis.sequence_applications (application) values ('Other');
