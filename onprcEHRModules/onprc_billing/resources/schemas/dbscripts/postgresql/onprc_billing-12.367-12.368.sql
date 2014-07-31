ALTER TABLE onprc_billing.projectMultipliers ADD account varchar(100);

UPDATE onprc_billing.projectMultipliers SET account = (
  SELECT max(account) FROM onprc_billing.projectAccountHistory a
  WHERE a.project = projectMultipliers.project
   AND a.startdate <= now()
   AND a.enddate >= now()
);

ALTER TABLE onprc_billing.projectMultipliers DROP COLUMN project;