SELECT
CP_LAST_NAME AS LastName,
CP_FIRST_NAME AS FirstName,
CP_EMPLOYEE_ID AS EmployeeId,
CP_POSITION AS Title,
CP_PHONE1 AS OfficePhone,
CP_EMAIL1 AS Email,
CP_EXPIRATION AS EndDate,
CP_MIDDLE_NAME AS middleName,
CP_UNITFK AS Unit,
CP_SUPERVISOR AS Supervisor,
CP_CELL_PHONE AS CellPhone,
CP_WORK_LOCATION AS Location
FROM
cnprcSrc_complianceAndTraining.ZCRPRC_PERSON
WHERE CP_EMPLOYEE_ID IS NOT NULL;