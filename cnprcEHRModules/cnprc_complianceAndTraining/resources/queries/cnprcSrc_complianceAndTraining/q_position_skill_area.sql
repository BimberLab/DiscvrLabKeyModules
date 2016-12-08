SELECT
tpc.TPC_POSITION AS "position",
ts.TS_SKILL AS skillRequired,
ta.TA_AREA AS area
FROM cnprcSrc_complianceAndTraining.ZTRAIN_POSITION tp
LEFT JOIN cnprcSrc_complianceAndTraining.ZTRAIN_POS_SKILLS tps
ON tp.TP_POS_PK = tps.TPS_POS_FK
LEFT JOIN cnprcSrc_complianceAndTraining.ZTRAIN_SKILLS ts
ON tps.TPS_SKILL_FK = ts.TS_SKILL_PK
LEFT JOIN cnprcSrc_complianceAndTraining.ZTRAIN_POSITION_CLASS tpc
ON tp.TP_TPC_FK = tpc.TPC_POS_PK
LEFT JOIN cnprcSrc_complianceAndTraining.ZTRAIN_AREA ta
ON tp.TP_AREA_FK = ta.TA_AREA_PK;
