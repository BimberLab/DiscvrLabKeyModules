--author Ron Dashwood
SELECT
SUBT_PK AS subtest_pk,
SUBT_ST_FK AS test_fk,
SUBT_TYPE AS type,
(CASE WHEN SUBT_FLAG = 'Y' THEN 1 ELSE 0 END) AS isFlag,
SUBT_RESULTS AS results,
SUBT_RPT_DATE AS reportDate,
SUBT_TEST_DONE AS testDoneDate,
(CASE WHEN SUBT_HIDE_ON_RPT = 'Y' THEN 1 ELSE 0 END) AS isHideOnReport,
SUBT_COMMENT AS comments,
(CASE WHEN SUBT_HIDE_COMMENT = 'Y' THEN 1 ELSE 0 END) AS isHideComment,
OBJECTID AS objectid,
DATE_TIME
FROM cnprcSrc_srl.SRL_SUB_TESTS