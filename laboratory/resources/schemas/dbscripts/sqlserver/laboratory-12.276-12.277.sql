CREATE TABLE laboratory.major_events (
  rowid int identity(1,1),
  subjectId varchar(100),
  date datetime,
  event varchar(100),
  category varchar(100),

  comment varchar(4000),

  container entityid NOT NULL,
  createdBy int,
  created datetime,
  modifiedBy int,
  modified datetime,

  CONSTRAINT PK_major_events PRIMARY KEY (rowid)
);

ALTER table laboratory.subjects ADD strain varchar(100);
