CREATE TABLE laboratory.project_usage (
  rowid int identity(1,1),
  subjectId varchar(100),
  project varchar(100),
  groupname varchar(200),
  startdate datetime,
  enddate datetime,
  comment varchar(4000),

  container entityid NOT NULL,
  createdBy int,
  created datetime,
  modifiedBy int,
  modified datetime,

  CONSTRAINT PK_project_usage PRIMARY KEY (rowid)
);

