CREATE TABLE singlecell.hashing_labels (
  rowid int IDENTITY(1,1),
  name nvarchar(100),
  groupName nvarchar(100),
  markerLabel nvarchar(100),
  adaptersequence nvarchar(4000),
  barcodePattern nvarchar(100),

  container entityid,
  created datetime,
  createdby int,
  modified datetime,
  modifiedby int,

  constraint PK_hashing_labels PRIMARY KEY (rowid)
);