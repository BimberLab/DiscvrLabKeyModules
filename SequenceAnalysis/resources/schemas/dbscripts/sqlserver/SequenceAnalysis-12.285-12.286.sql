CREATE TABLE sequenceanalysis.readset_status (
  rowid int identity(1,1),
  status varchar(100),
  container entityid,
  created datetime,
  createdby int,
  modified datetime,
  modifiedby int
);