DROP TABLE laboratory.workbook_group_members;

CREATE TABLE laboratory.workbook_tags (
  rowid serial,
  tag varchar(200),
  container entityid,
  created int,
  createdby timestamp,

  CONSTRAINT pk_workbook_tags PRIMARY KEY (rowid)
);
