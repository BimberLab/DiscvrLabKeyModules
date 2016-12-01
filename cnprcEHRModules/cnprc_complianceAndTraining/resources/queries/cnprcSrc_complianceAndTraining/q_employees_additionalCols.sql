SELECT
CP_EMPLOYEE_ID AS employeeId,
CP_PK AS person_cnprc_pk,
CP_STATUS AS status,
CP_EMP_STUD AS emp_stud,
CP_PAGER AS pager,
CP_DEPARTFK AS departfk,
CP_INSTITUTION AS institution,
CP_SPONSOR AS sponsor,
CP_COMMENT AS comments
FROM
cnprcSrc_complianceAndTraining.ZCRPRC_PERSON;