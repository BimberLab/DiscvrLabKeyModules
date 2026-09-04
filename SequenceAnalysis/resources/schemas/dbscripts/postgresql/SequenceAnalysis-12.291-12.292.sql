-- The schema and table names were reversed in a previous attempt to drop this constraint, so it was never dropped.
-- New syntax results in a SQL exception, so it's commented out, but left as historical documentation of the intent.
-- ALTER TABLE barcodes.sequenceanalysis DROP CONSTRAINT IF EXISTS UNIQUE_barcodes;

CREATE TABLE sequenceanalysis.genomeAliases (
  rowid serial,
  genomeId int,
  externalDb varchar(100),
  externalName varchar(1000),

  container entityid,
  created timestamp,
  createdby int,
  modified timestamp,
  modifiedby int,

  CONSTRAINT PK_genomeAliases PRIMARY KEY (rowid)
);