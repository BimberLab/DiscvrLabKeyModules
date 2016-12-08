SELECT
ts.TS_SKILL AS RequirementName,
st.TSS_DATE,
tt.TTR_NAME AS Trainer,
st.TSS_COMMENT,
cp.CP_PK AS EmployeeId,
st.TSS_STATUS_TYPE AS result
FROM cnprcSrc_complianceAndTraining.ZTRAIN_SKILL_STATUS st
LEFT JOIN cnprcSrc_complianceAndTraining.ZTRAIN_TRACK_SKILLS sk
ON sk.TTS_PK = st.TSS_TRACK_FK
LEFT JOIN cnprcSrc_complianceAndTraining.ZTRAIN_SKILLS ts
ON sk.TTS_SKILL_FK = ts.TS_SKILL_PK
LEFT JOIN cnprcSrc_complianceAndTraining.ZTRAIN_EMPLOYEES te
ON sk.TTS_EMP_FK = te.TE_EMP_PK
LEFT JOIN cnprcSrc_complianceAndTraining.ZCRPRC_PERSON cp
ON te.TE_NAME_FK = cp.CP_PK
LEFT JOIN cnprcSrc_complianceAndTraining.ZTRAIN_TRAINERS tt
ON st.TSS_EVALUATOR = tt.TTR_TRAIN_PK
WHERE TSS_DATE IS NOT NULL AND TSS_DATE > to_date('01-01-1900', 'DD-MM-YYYY');