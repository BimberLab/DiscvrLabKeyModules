ALTER TABLE onprc_billing.chargeRateExemptions ADD remark varchar(4000);
ALTER TABLE onprc_billing.chargeRateExemptions ADD subsidy double precision;

CREATE TABLE onprc_billing.projectFARates (
  rowid serial,
  project int,
  fa double precision,
  remark varchar(4000),
  startdate timestamp,
  enddate timestamp,

  container entityid,
  createdby int,
  created timestamp,
  modifiedby int,
  modified timestamp
);