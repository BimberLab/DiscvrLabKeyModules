CREATE TABLE singlecell.hashing_labels (
  rowid serial,
  name varchar(100),
  groupName varchar(100),
  adaptersequence varchar(4000),
  barcodePattern varchar(100),

  container entityid,
  created timestamp,
  createdby int,
  modified timestamp,
  modifiedby int,

  constraint PK_hashing_labels PRIMARY KEY (rowid)
);