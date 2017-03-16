SELECT
OBJECTID || '-' || '1' AS objectid,
DATE_TIME
FROM cnprcSrc_billing_aud.ACWOS_WO
WHERE
CWO_AUD_CODE = 'D' AND (CWO_TO_1 IS NOT NULL OR CWO_CC_1 IS NOT NULL)

UNION ALL

SELECT
OBJECTID || '-' || '2' AS objectid,
DATE_TIME
FROM cnprcSrc_billing_aud.ACWOS_WO
WHERE
CWO_AUD_CODE = 'D' AND (CWO_TO_2 IS NOT NULL OR CWO_CC_2 IS NOT NULL)

UNION ALL

SELECT
OBJECTID || '-' || '3' AS objectid,
DATE_TIME
FROM cnprcSrc_billing_aud.ACWOS_WO
WHERE
CWO_AUD_CODE = 'D' AND (CWO_TO_3 IS NOT NULL OR CWO_CC_3 IS NOT NULL)

UNION ALL

SELECT
OBJECTID || '-' || '4' AS objectid,
DATE_TIME
FROM cnprcSrc_billing_aud.ACWOS_WO
WHERE
CWO_AUD_CODE = 'D' AND (CWO_TO_4 IS NOT NULL OR CWO_CC_4 IS NOT NULL)

UNION ALL

SELECT
OBJECTID || '-' || '5' AS objectid,
DATE_TIME
FROM cnprcSrc_billing_aud.ACWOS_WO
WHERE
CWO_AUD_CODE = 'D' AND (CWO_TO_5 IS NOT NULL OR CWO_CC_5 IS NOT NULL)

UNION ALL

SELECT
OBJECTID || '-' || '6' AS objectid,
DATE_TIME
FROM cnprcSrc_billing_aud.ACWOS_WO
WHERE
CWO_AUD_CODE = 'D' AND (CWO_TO_6 IS NOT NULL OR CWO_CC_6 IS NOT NULL)

UNION ALL

SELECT
OBJECTID || '-' || '7' AS objectid,
DATE_TIME
FROM cnprcSrc_billing_aud.ACWOS_WO
WHERE
CWO_AUD_CODE = 'D' AND (CWO_TO_7 IS NOT NULL OR CWO_CC_7 IS NOT NULL)

UNION ALL

SELECT
OBJECTID || '-' || '8' AS objectid,
DATE_TIME
FROM cnprcSrc_billing_aud.ACWOS_WO
WHERE
CWO_AUD_CODE = 'D' AND (CWO_TO_8 IS NOT NULL OR CWO_CC_8 IS NOT NULL)

UNION ALL

SELECT
OBJECTID || '-' || '9' AS objectid,
DATE_TIME
FROM cnprcSrc_billing_aud.ACWOS_WO
WHERE
CWO_AUD_CODE = 'D' AND (CWO_TO_9 IS NOT NULL OR CWO_CC_9 IS NOT NULL)

UNION ALL

SELECT
OBJECTID || '-' || '10' AS objectid,
DATE_TIME
FROM cnprcSrc_billing_aud.ACWOS_WO
WHERE
CWO_AUD_CODE = 'D' AND (CWO_TO_10 IS NOT NULL OR CWO_CC_10 IS NOT NULL)

UNION ALL

SELECT
OBJECTID || '-' || '1'  AS objectid,
DATE_TIME
FROM cnprcSrc_billing.ZCWOS_WO
WHERE CWO_TO_1 IS NULL AND CWO_CC_1 IS NULL

UNION ALL

SELECT
OBJECTID || '-' || '2'  AS objectid,
DATE_TIME
FROM cnprcSrc_billing.ZCWOS_WO
WHERE CWO_TO_2 IS NULL AND CWO_CC_2 IS NULL

UNION ALL

SELECT
OBJECTID || '-' || '3'  AS objectid,
DATE_TIME
FROM cnprcSrc_billing.ZCWOS_WO
WHERE CWO_TO_3 IS NULL AND CWO_CC_3 IS NULL

UNION ALL

SELECT
OBJECTID || '-' || '4'  AS objectid,
DATE_TIME
FROM cnprcSrc_billing.ZCWOS_WO
WHERE CWO_TO_4 IS NULL AND CWO_CC_4 IS NULL

UNION ALL

SELECT
OBJECTID || '-' || '5'  AS objectid,
DATE_TIME
FROM cnprcSrc_billing.ZCWOS_WO
WHERE CWO_TO_5 IS NULL AND CWO_CC_5 IS NULL

UNION ALL

SELECT
OBJECTID || '-' || '6'  AS objectid,
DATE_TIME
FROM cnprcSrc_billing.ZCWOS_WO
WHERE CWO_TO_6 IS NULL AND CWO_CC_6 IS NULL

UNION ALL

SELECT
OBJECTID || '-' || '7'  AS objectid,
DATE_TIME
FROM cnprcSrc_billing.ZCWOS_WO
WHERE CWO_TO_7 IS NULL AND CWO_CC_7 IS NULL

UNION ALL

SELECT
OBJECTID || '-' || '8'  AS objectid,
DATE_TIME
FROM cnprcSrc_billing.ZCWOS_WO
WHERE CWO_TO_8 IS NULL AND CWO_CC_8 IS NULL

UNION ALL

SELECT
OBJECTID || '-' || '9'  AS objectid,
DATE_TIME
FROM cnprcSrc_billing.ZCWOS_WO
WHERE CWO_TO_9 IS NULL AND CWO_CC_9 IS NULL

UNION ALL

SELECT
OBJECTID || '-' || '10'  AS objectid,
DATE_TIME
FROM cnprcSrc_billing.ZCWOS_WO
WHERE CWO_TO_10 IS NULL AND CWO_CC_10 IS NULL;