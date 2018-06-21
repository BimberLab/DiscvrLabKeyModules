ALTER TABLE sequenceanalysis.instrument_runs ADD facility varchar(500);
ALTER TABLE sequenceanalysis.instrument_runs ADD lane varchar(500);
ALTER TABLE sequenceanalysis.instrument_runs ADD instrumentType varchar(500);

ALTER TABLE sequenceanalysis.instrument_runs DROP instrumentid;

ALTER TABLE sequenceanalysis.saved_analyses ALTER COLUMN json TYPE text;

INSERT into sequenceanalysis.instruments (displayname, platform) VALUES ('MiSeq', 'ILLUMINA');
INSERT into sequenceanalysis.instruments (displayname, platform) VALUES ('HiSeq3000', 'ILLUMINA');
INSERT into sequenceanalysis.instruments (displayname, platform) VALUES ('HiSeq2500', 'ILLUMINA');
INSERT into sequenceanalysis.instruments (displayname, platform) VALUES ('NovaSeq', 'ILLUMINA');