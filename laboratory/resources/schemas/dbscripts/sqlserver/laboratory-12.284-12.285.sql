DROP TABLE laboratory.workbook_group_members;

CREATE TABLE laboratory.workbook_tags (
  rowid int identity(1,1),
  tag varchar(200),
  container entityid,
  created int,
  createdby datetime,

  CONSTRAINT pk_workbook_tags PRIMARY KEY (rowid)
);
