ALTER TABLE onprc_billing.chargeRateExemptions ADD remark varchar(4000);
ALTER TABLE onprc_billing.chargeRateExemptions ADD subsidy double precision;

CREATE TABLE onprc_billing.projectFARates (
  rowid int identity(1,1),
  project int,
  fa double precision,
  remark varchar(4000),
  startdate datetime,
  enddate datetime,

  container entityid,
  createdby int,
  created datetime,
  modifiedby int,
  modified datetime
);