CREATE TABLE sequenceanalysis.reference_libraries (
  rowid int identity(1,1),
  name varchar(200),
  description varchar(4000),

  fasta_file int,
  snps_file int,

  container entityid,
  created datetime,
  createdby int,
  modified datetime,
  modifiedby int,

  CONSTRAINT PK_reference_libraries PRIMARY KEY (rowid)
);

CREATE TABLE sequenceanalysis.reference_library_members (
  rowid int identity(1,1),
  library_id int,
  ref_nt_id int,

  container entityid,
  created datetime,
  createdby int,
  modified datetime,
  modifiedby int,

  CONSTRAINT PK_reference_library_members PRIMARY KEY (rowid)
);

CREATE TABLE sequenceanalysis.reference_library_tracks (
  rowid int identity(1,1),
  name varchar(200),
  description varchar(4000),

  library_id int,
  fileid int,
  type varchar(200),

  container entityid,
  created datetime,
  createdby int,
  modified datetime,
  modifiedby int,

  CONSTRAINT PK_reference_library_tracks PRIMARY KEY (rowid)
);

CREATE TABLE sequenceanalysis.saved_analyses (
  rowid int identity(1,1),
  name varchar(200),
  description varchar(4000),

  json varchar(4000),
  originalAnalysisId int,

  container entityid,
  created datetime,
  createdby int,
  modified datetime,
  modifiedby int,

  CONSTRAINT PK_saved_analyses PRIMARY KEY (rowid)
);
