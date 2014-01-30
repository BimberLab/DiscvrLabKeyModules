CREATE TABLE onprc_billing.slaPerDiemFeeDefinition (
  rowid int IDENTITY(1,1) NOT NULL,
  chargeid int,
  cagetype varchar(100),
  cagesize varchar(100),
  species varchar(100),
  active bit,
  objectid ENTITYID,
  createdby int,
  created datetime,
  modifiedby int,
  modified datetime,

  CONSTRAINT PK_slaPerDiemFeeDefinition PRIMARY KEY (rowid)
);
