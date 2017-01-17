--author Ron Dashwood
SELECT
ST_PK                   AS test_pk,
ST_SS_FK                AS sample_fk,
ST_TYPE                 AS type,
ST_FLAG                 AS isFlag,
ST_RESULTS              AS results,
ST_RPT_DATE             AS reportDate,
ST_TEST_DONE            AS testDoneDate,
(CASE WHEN ST_NO_CHARGE = 'Y' THEN 1 ELSE 0 END)     AS isNoCharge,
ST_COMMENT          AS comments,
(CASE WHEN ST_HIDE_COMMENT   = 'Y' THEN 1 ELSE 0 END) AS isHideComment,
(CASE WHEN ST_PANEL_YN       = 'Y' THEN 1 ELSE 0 END) AS isPanelYn,
(CASE WHEN ST_HIDE_ON_RPT    = 'Y' THEN 1 ELSE 0 END) AS isHideOnReport,
OBJECTID AS objectid,
DATE_TIME
FROM cnprcSrc_srl.SRL_TESTS;