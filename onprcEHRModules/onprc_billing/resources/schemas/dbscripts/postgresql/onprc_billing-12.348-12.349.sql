CREATE TABLE onprc_billing.slaPerDiemFeeDefinition (
  rowid serial,
  chargeid int,
  cagetype varchar(100),
  cagesize varchar(100),
  species varchar(100),
  active bool,
  objectid ENTITYID,
  createdby int,
  created timestamp,
  modifiedby int,
  modified timestamp,

  CONSTRAINT PK_slaPerDiemFeeDefinition PRIMARY KEY (rowid)
);
