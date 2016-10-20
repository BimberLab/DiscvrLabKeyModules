--author Ron Dashwood
SELECT
SO_PK                 AS order_pk,
SO_SC_FK              AS client_fk,
SO_SBC_FK             AS billingContact_fk,
SO_SRC_FK             AS reportContact_fk,
SO_BILL_PO            AS purchaseOrder,
SO_ORDER_DATE         AS orderDate,
SO_REQUEST_NUM        AS requestNumber,
SO_BILLING_DATE       AS billingDate,
SO_REPORT_DATE        AS reportDate,
(CASE WHEN SO_HIDE_COMMENT = 'Y' THEN 1 ELSE 0 END) AS isHideComment,
SO_COMMENT            AS comments,
SO_CHARGE_ID          AS chargeId,
SO_ACCOUNT_ID         AS accountId,
SO_ORDER_CLOSED_DATE  AS orderClosedDate,
SO_PROJECT            AS project,
SO_ENTERED_BY         AS enteredBy,
SO_PC_INVOICE_NUMBER  AS invoiceNumber,
OBJECTID AS objectid,
DATE_TIME
FROM cnprcSrc_srl.SRL_ORDERS