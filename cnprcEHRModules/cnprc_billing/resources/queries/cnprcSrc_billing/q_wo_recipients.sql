SELECT
CWO_NO AS workOrderNumber,
CWO_TO_1 AS toRecipient,
CWO_CC_1 AS ccRecipient,
OBJECTID || '-' || '1' AS objectid,
DATE_TIME
FROM cnprcSrc_billing.ZCWOS_WO
WHERE
CWO_BEGIN_DATE > to_date('01-01-1900', 'DD-MM-YYYY') AND
(CWO_TO_1 IS NOT NULL OR CWO_CC_1 IS NOT NULL)

UNION ALL

SELECT
CWO_NO AS workOrderNumber,
CWO_TO_2 AS toRecipient,
CWO_CC_2 AS ccRecipient,
OBJECTID || '-' || '2'  AS objectid,
DATE_TIME
FROM cnprcSrc_billing.ZCWOS_WO
WHERE
CWO_BEGIN_DATE > to_date('01-01-1900', 'DD-MM-YYYY') AND
(CWO_TO_2 IS NOT NULL OR CWO_CC_2 IS NOT NULL)

UNION ALL

SELECT
CWO_NO AS workOrderNumber,
CWO_TO_3 AS toRecipient,
CWO_CC_3 AS ccRecipient,
OBJECTID || '-' || '3'  AS objectid,
DATE_TIME
FROM cnprcSrc_billing.ZCWOS_WO
WHERE
CWO_BEGIN_DATE > to_date('01-01-1900', 'DD-MM-YYYY') AND
(CWO_TO_3 IS NOT NULL OR CWO_CC_3 IS NOT NULL)

UNION ALL

SELECT
CWO_NO AS workOrderNumber,
CWO_TO_4 AS toRecipient,
CWO_CC_4 AS ccRecipient,
OBJECTID || '-' || '4'  AS objectid,
DATE_TIME
FROM cnprcSrc_billing.ZCWOS_WO
WHERE
CWO_BEGIN_DATE > to_date('01-01-1900', 'DD-MM-YYYY') AND
(CWO_TO_4 IS NOT NULL OR CWO_CC_4 IS NOT NULL)

UNION ALL

SELECT
CWO_NO AS workOrderNumber,
CWO_TO_5 AS toRecipient,
CWO_CC_5 AS ccRecipient,
OBJECTID || '-' || '5'  AS objectid,
DATE_TIME
FROM cnprcSrc_billing.ZCWOS_WO
WHERE
CWO_BEGIN_DATE > to_date('01-01-1900', 'DD-MM-YYYY') AND
(CWO_TO_5 IS NOT NULL OR CWO_CC_5 IS NOT NULL)

UNION ALL

SELECT
CWO_NO AS workOrderNumber,
CWO_TO_6 AS toRecipient,
CWO_CC_6 AS ccRecipient,
OBJECTID || '-' || '6'  AS objectid,
DATE_TIME
FROM cnprcSrc_billing.ZCWOS_WO
WHERE
CWO_BEGIN_DATE > to_date('01-01-1900', 'DD-MM-YYYY') AND
(CWO_TO_6 IS NOT NULL OR CWO_CC_6 IS NOT NULL)

UNION ALL

SELECT
CWO_NO AS workOrderNumber,
CWO_TO_7 AS toRecipient,
CWO_CC_7 AS ccRecipient,
OBJECTID || '-' || '7'  AS objectid,
DATE_TIME
FROM cnprcSrc_billing.ZCWOS_WO
WHERE
CWO_BEGIN_DATE > to_date('01-01-1900', 'DD-MM-YYYY') AND
(CWO_TO_7 IS NOT NULL OR CWO_CC_7 IS NOT NULL)

UNION ALL

SELECT
CWO_NO AS workOrderNumber,
CWO_TO_8 AS toRecipient,
CWO_CC_8 AS ccRecipient,
OBJECTID || '-' || '8'  AS objectid,
DATE_TIME
FROM cnprcSrc_billing.ZCWOS_WO
WHERE
CWO_BEGIN_DATE > to_date('01-01-1900', 'DD-MM-YYYY') AND
(CWO_TO_8 IS NOT NULL OR CWO_CC_8 IS NOT NULL)

UNION ALL

SELECT
CWO_NO AS workOrderNumber,
CWO_TO_9 AS toRecipient,
CWO_CC_9 AS ccRecipient,
OBJECTID || '-' || '9'  AS objectid,
DATE_TIME
FROM cnprcSrc_billing.ZCWOS_WO
WHERE
CWO_BEGIN_DATE > to_date('01-01-1900', 'DD-MM-YYYY') AND
(CWO_TO_9 IS NOT NULL OR CWO_CC_9 IS NOT NULL)

UNION ALL

SELECT
CWO_NO AS workOrderNumber,
CWO_TO_10 AS toRecipient,
CWO_CC_10 AS ccRecipient,
OBJECTID || '-' || '10'  AS objectid,
DATE_TIME
FROM cnprcSrc_billing.ZCWOS_WO
WHERE
CWO_BEGIN_DATE > to_date('01-01-1900', 'DD-MM-YYYY') AND
(CWO_TO_10 IS NOT NULL OR CWO_CC_10 IS NOT NULL);