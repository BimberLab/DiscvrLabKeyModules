/*
 * Copyright (c) 2016 LabKey Corporation
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

CREATE TABLE cnprc_billing.grant_affiliated_person (

  rowid INT IDENTITY(1,1) NOT NULL,
  Person_id                    int,
  Last_name                    NVARCHAR(40),
  First_name                   NVARCHAR(20),
  Middle_initial               NVARCHAR(1),
  Pref_first_name              NVARCHAR(20),
  Name_prefix                  NVARCHAR(20),
  Name_suffix                  NVARCHAR(20),
  Ucd_emp_id                   NVARCHAR(9),
  Ucd_stu_id                   NVARCHAR(9),
  Degree_list                  NVARCHAR(20),
  Title                        NVARCHAR(30),
  Bus_addr_company             NVARCHAR(50),
  Bus_addr_division            NVARCHAR(50),
  Bus_addr_street              NVARCHAR(40),
  Bus_addr_city                NVARCHAR(24),
  Bus_addr_state_abbrev        NVARCHAR(2),
  Bus_addr_zip_code            NVARCHAR(10),
  Bus_addr_country             NVARCHAR(18),
  Bus_phone                    NVARCHAR(30),
  Bus_fax                      NVARCHAR(18),
  Bus_email                    NVARCHAR(50),
  Inst_iid                     int,
  Caff_code                    NVARCHAR(6),
  Caff_research_unit           NVARCHAR(6),
  Caff_nonres_unit             NVARCHAR(8),
  Caff_start_date              DATETIME,
  Inst_school                  NVARCHAR(2),
  Inst_dept_mailcode           NVARCHAR(6),
  Inst_start_date              DATETIME,
  Caff_sponsor_pid             int,
  Handle                       NVARCHAR(12),
  Ucd_school                   NVARCHAR(40),
  Ucd_dept                     NVARCHAR(40),
  Inst_name_temp               NVARCHAR(60),
  Active_yn                    NVARCHAR(1),
  objectid                     nvarchar(100),
  Created                      DATETIME,
  CreatedBy                    USERID,
  Modified                     DATETIME,
  ModifiedBy                   USERID,
  Container	                   entityId NOT NULL,

  CONSTRAINT PK_CNPRC_BILLING_GAP PRIMARY KEY (rowid),
  CONSTRAINT FK_CNPRC_BILLING_GAP FOREIGN KEY (container) REFERENCES core.Containers (EntityId)

);
GO

CREATE INDEX CNPRC_BILLING_GAP_CONTAINER_INDEX ON cnprc_billing.grant_affiliated_person (Container);
GO
