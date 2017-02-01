ALTER TABLE sequenceanalysis.readdata ADD sra_accession VARCHAR(100);
ALTER TABLE sequenceanalysis.sequence_analyses ADD sra_accession VARCHAR(100);
ALTER TABLE sequenceanalysis.outputfiles ADD sra_accession VARCHAR(100);

ALTER TABLE sequenceanalysis.reference_libraries ADD assemblyId VARCHAR(100);
