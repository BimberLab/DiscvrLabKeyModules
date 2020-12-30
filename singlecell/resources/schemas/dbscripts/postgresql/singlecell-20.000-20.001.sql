CREATE TABLE singlecell.cdna_libraries (
  rowid serial,
  sortid int,
  chemistry varchar(200),
  concentration float,
  plateId varchar(200),
  well varchar(100),
  readsetid int,
  tcrreadsetid int,
  hashingReadsetId int,
  citeseqReadsetId int,
  citeseqPanel varchar(100),

  comment varchar(4000),
  status varchar(200),

  lsid LSIDtype,
  container ENTITYID,
  created timestamp,
  createdby int,
  modified timestamp,
  modifiedby int,
  CONSTRAINT PK_cdna_libraries PRIMARY KEY (rowid)
);

CREATE TABLE singlecell.stim_types(
  rowid serial,
  name varchar(200) NOT NULL,
  category varchar(200),
  type varchar(200),

  container ENTITYID,
  created timestamp,
  createdby int,
  modified timestamp,
  modifiedby int,

  CONSTRAINT PK_stim_types PRIMARY KEY (rowid)
);

CREATE TABLE singlecell.assay_types(
  rowid serial,
  name varchar(200) NOT NULL,
  treatment varchar(1000),
  description varchar(4000),

  container ENTITYID,
  created timestamp,
  createdby int,
  modified timestamp,
  modifiedby int,

  CONSTRAINT PK_assay_types PRIMARY KEY (rowid)
);

CREATE TABLE singlecell.samples(
  rowid serial,
  subjectId varchar(100),
  sampledate timestamp,
  tissue varchar(4000),
  celltype varchar(1000),
  stim varchar(4000),
  assaytype varchar(4000),
  comment varchar(4000),
  status varchar(200),

  lsid LSIDtype,
  container ENTITYID,
  created timestamp,
  createdby int,
  modified timestamp,
  modifiedby int,

  CONSTRAINT PK_stims PRIMARY KEY (rowid)
);

CREATE TABLE singlecell.sorts(
  rowid serial,
  sampleid int,
  population varchar(1000),
  replicate varchar(100),
  cells int,
  plateId varchar(100),
  well varchar(100),
  buffer varchar(200),
  hto varchar(100),
  comment varchar(4000),

  lsid LSIDtype,
  container ENTITYID,
  created timestamp,
  createdby int,
  modified timestamp,
  modifiedby int,

  CONSTRAINT PK_sorts PRIMARY KEY (rowid)
);

CREATE TABLE singlecell.citeseq_panels (
  rowid serial,
  name varchar(100),
  antibody varchar(100),
  markerLabel varchar(100),

  container entityid,
  created timestamp,
  createdby int,
  modified timestamp,
  modifiedby int,

  constraint PK_citeseq_panels PRIMARY KEY (rowid)
);

CREATE TABLE singlecell.citeseq_antibodies (
  rowid serial,
  antibodyName varchar(100),
  markerName varchar(100),
  markerLabel varchar(100),
  cloneName varchar(100),
  vendor varchar(100),
  productId varchar(100),
  barcodeName varchar(100),  
  adaptersequence varchar(4000),
  
  container entityid,
  created timestamp,
  createdby int,
  modified timestamp,
  modifiedby int,
  
  constraint PK_citeseq_antibodies PRIMARY KEY (rowid)
);
