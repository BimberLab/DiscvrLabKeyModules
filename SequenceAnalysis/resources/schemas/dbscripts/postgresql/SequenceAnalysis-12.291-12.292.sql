SELECT core.fn_dropifexists('sequenceanalysis', 'barcodes', 'CONSTRAINT', 'UNIQUE_barcodes');

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