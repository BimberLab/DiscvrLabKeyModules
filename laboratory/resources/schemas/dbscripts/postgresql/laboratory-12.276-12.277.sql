CREATE TABLE laboratory.major_events (
  rowid SERIAL,
  subjectId varchar(100),
  date timestamp,
  event varchar(100),
  category varchar(100),

  comment varchar(4000),

  container entityid NOT NULL,
  createdBy int,
  created timestamp,
  modifiedBy int,
  modified timestamp,

  CONSTRAINT PK_major_events PRIMARY KEY (rowid)
);

ALTER table laboratory.subjects ADD strain varchar(100);