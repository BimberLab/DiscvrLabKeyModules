CREATE TABLE singlecell.cdna_libraries (
  rowid int IDENTITY(1,1),
  sortid int,
  chemistry nvarchar(200),
  concentration float,
  plateId nvarchar(200),
  well nvarchar(100),
  readsetid int,
  tcrreadsetid int,
  hashingReadsetId int,
  citeseqReadsetId int,
  citeseqPanel nvarchar(100),

  comment nvarchar(4000),
  status nvarchar(200),

  lsid LSIDtype,
  container ENTITYID,
  created datetime,
  createdby int,
  modified datetime,
  modifiedby int,
  CONSTRAINT PK_cdna_libraries PRIMARY KEY (rowid)
);

CREATE TABLE singlecell.stim_types(
  rowid int IDENTITY(1,1),
  name nvarchar(200) NOT NULL,
  category nvarchar(200),
  type nvarchar(200),

  container ENTITYID,
  created datetime,
  createdby int,
  modified datetime,
  modifiedby int,

  CONSTRAINT PK_stim_types PRIMARY KEY (rowid)
);

CREATE TABLE singlecell.assay_types(
  rowid int IDENTITY(1,1),
  name nvarchar(200) NOT NULL,
  treatment nvarchar(1000),
  description nvarchar(4000),

  container ENTITYID,
  created datetime,
  createdby int,
  modified datetime,
  modifiedby int,

  CONSTRAINT PK_assay_types PRIMARY KEY (rowid)
);

CREATE TABLE singlecell.samples(
  rowid int IDENTITY(1,1),
  subjectId nvarchar(100),
  sampledate datetime,
  tissue nvarchar(4000),
  celltype nvarchar(1000),
  stim nvarchar(4000),
  assaytype nvarchar(4000),
  comment nvarchar(4000),
  status nvarchar(200),

  lsid LSIDtype,
  container ENTITYID,
  created datetime,
  createdby int,
  modified datetime,
  modifiedby int,

  CONSTRAINT PK_stims PRIMARY KEY (rowid)
);

CREATE TABLE singlecell.sorts(
  rowid int IDENTITY(1,1),
  sampleid int,
  population nvarchar(1000),
  replicate nvarchar(100),
  cells int,
  plateId nvarchar(100),
  well nvarchar(100),
  buffer nvarchar(200),
  hto nvarchar(100),
  comment nvarchar(4000),

  lsid LSIDtype,
  container ENTITYID,
  created datetime,
  createdby int,
  modified datetime,
  modifiedby int,

  CONSTRAINT PK_sorts PRIMARY KEY (rowid)
);

CREATE TABLE singlecell.citeseq_panels (
  rowid int IDENTITY(1,1),
  name nvarchar(100),
  antibody nvarchar(100),
  markerLabel nvarchar(100),

  container entityid,
  created datetime,
  createdby int,
  modified datetime,
  modifiedby int,

  constraint PK_citeseq_panels PRIMARY KEY (rowid)
);

CREATE TABLE singlecell.citeseq_antibodies (
  rowid int IDENTITY(1,1),
  antibodyName nvarchar(100),
  markerName nvarchar(100),
  markerLabel nvarchar(100),
  cloneName nvarchar(100),
  vendor nvarchar(100),
  productId nvarchar(100),
  barcodeName nvarchar(100),  
  adaptersequence nvarchar(4000),
  
  container entityid,
  created datetime,
  createdby int,
  modified datetime,
  modifiedby int,
  
  constraint PK_citeseq_antibodies PRIMARY KEY (rowid)
);
