SELECT
CSPLYLI_PK AS csplyliPk,
CSPLYTM_FK AS csplytmFk,
REQUISITION_NUM AS requisitionNum,
SERVICE_CODE AS serviceCode,
RC_CODE AS rcCode,
LINE_NO AS lineNum,
ITEM_SKU AS itemSku,
DESCRIPTION AS description,
COST_CODE AS costCode,
ITEM_QTY AS itemQty,
UNIT_OF_MEASURE AS unitOfMeasure,
ITEM_COST AS itemCost,
CHARGE_AMT AS chargeAmt,
OVERRIDE_VALIDATION_ERROR_YN AS overrideValidationError,
OVERRIDE_VALIDATION_ERROR_USER AS overrideValidationErrorUser,
REQUISITION_NUM_SUFFIX AS requisitionNumSuffix,
OBJECTID AS objectId,
DATE_TIME
FROM
cnprcSrc_billing_fin.CENTRAL_SUPPLY_LINE_ITEM;

