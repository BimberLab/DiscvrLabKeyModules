CREATE TABLE laboratory.project_usage (
  rowid serial,
  subjectId varchar(100),
  project varchar(100),
  groupname varchar(200),
  startdate timestamp,
  enddate timestamp,
  comment varchar(4000),

  container entityid NOT NULL,
  createdBy int,
  created timestamp,
  modifiedBy int,
  modified timestamp,

  CONSTRAINT PK_project_usage PRIMARY KEY (rowid)
);

