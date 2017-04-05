--author Ron Dashwood
SELECT
  SS_PK                        AS sample_pk,
  SS_SO_FK                     AS order_fk,
  SS_ID                        AS animalId,
  SS_LOG_NUM                   AS logNumber,
  SS_SPECIES                   AS species,
  SS_SAMPLE_TYPE               AS sampleType,
  SS_SAMPLE_DATE               AS sampleDate,
  SS_RECD_DATE                 AS receivedDate,
  (CASE WHEN SS_HIDE_COMMENT   = 'Y' THEN 1 ELSE 0 END) AS isHideComment,
  SS_COMMENT                   AS comments,
  (CASE WHEN SS_EST_SAMPLE_DATE= 'Y' THEN 1 ELSE 0 END) AS isEstimateSampleDate,
  (CASE WHEN SS_PROCESSING     = 'Y' THEN 1 ELSE 0 END) AS isProcessing,
  (CASE WHEN SS_ASSIGN         = 'Y' THEN 1 ELSE 0 END) AS isAssign,
  (CASE WHEN SS_ALL_TESTS_DONE = 'Y' THEN 1 ELSE 0 END) AS isAllTestsDone,
  OBJECTID AS objectid,
  DATE_TIME
FROM cnprcSrc_srl.SRL_SAMPLES;