ALTER TABLE sequenceanalysis.barcodes DROP CONSTRAINT UNIQUE_barcodes;

CREATE TABLE sequenceanalysis.genomeAliases (
  rowid int identity(1,1),
  genomeId int,
  externalDb varchar(100),
  externalName varchar(1000),

  container entityid,
  created datetime,
  createdby int,
  modified datetime,
  modifiedby int,

  CONSTRAINT PK_genomeAliases PRIMARY KEY (rowid)
);