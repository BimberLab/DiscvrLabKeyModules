ALTER TABLE sequenceanalysis.sequence_readsets ADD chemistry VARCHAR(1000);

CREATE TABLE sequenceanalysis.sequence_chemistries (
  chemistry VARCHAR(100),

  CONSTRAINT PK_sequence_chemistries PRIMARY KEY (chemistry)
);

INSERT INTO sequenceanalysis.sequence_chemistries (chemistry) VALUES ('Illumina HiSeq3000');
INSERT INTO sequenceanalysis.sequence_chemistries (chemistry) VALUES ('Illumina MiSeq 2x250');
INSERT INTO sequenceanalysis.sequence_chemistries (chemistry) VALUES ('Illumina MiSeq 2x300');
INSERT INTO sequenceanalysis.sequence_chemistries (chemistry) VALUES ('Illumina XTen');
INSERT INTO sequenceanalysis.sequence_chemistries (chemistry) VALUES ('Illumina NextSeq MidOutput');
INSERT INTO sequenceanalysis.sequence_chemistries (chemistry) VALUES ('Illumina NextSeq HighOutput');
