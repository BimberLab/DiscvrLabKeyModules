ALTER TABLE onprc_billing.projectMultipliers ADD account varchar(100);
GO
UPDATE onprc_billing.projectMultipliers SET account = (
  SELECT max(account) FROM onprc_billing.projectAccountHistory a
  WHERE a.project = projectMultipliers.project
   AND a.startdate <= CURRENT_TIMESTAMP
   AND a.enddate >= CURRENT_TIMESTAMP
);
GO
ALTER TABLE onprc_billing.projectMultipliers DROP COLUMN project;