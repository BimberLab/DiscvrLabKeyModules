/*
 * Copyright (c) 2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

SELECT CONVERT('OutOfSystem', SQL_VARCHAR) AS ContactType,
RowId AS Id,
DisplayName, FirstName, LastName, Email,
AddressStreet1, AddressStreet2, AddressCity, AddressState, AddressZip,
Location, Institution, InstitutionOther,
PhoneNumber, MobileNumber, PagerNumber,
CASE WHEN Inactive IS NULL OR Inactive=false THEN CONVERT('No', SQL_VARCHAR) ELSE CONVERT('Yes', SQL_VARCHAR) END AS Inactive
FROM biotrust.Contacts

UNION

SELECT CONVERT('InSystem', SQL_VARCHAR) AS ContactType,
UserId AS Id,
DisplayName, FirstName, LastName, Email,
IFDEFINED(AddressStreet1), IFDEFINED(AddressStreet2), IFDEFINED(AddressCity),
IFDEFINED(AddressState), IFDEFINED(AddressZip),
IFDEFINED(Location), IFDEFINED(Institution), IFDEFINED(InstitutionOther),
IFDEFINED(PhoneNumber), IFDEFINED(MobileNumber), IFDEFINED(PagerNumber),
CONVERT('No', SQL_VARCHAR) AS Inactive
FROM core.Users