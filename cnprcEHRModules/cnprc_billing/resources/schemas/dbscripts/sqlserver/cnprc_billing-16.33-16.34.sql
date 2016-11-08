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

CREATE TABLE cnprc_billing.grant_charge (

  rowid                 INT IDENTITY(1,1) NOT NULL,
  Gch_pk                int,
  Gpp_fk                int,
  Pc_charge_id          nvarchar(4),
  Pc_account_id         nvarchar(6),
  Status                nvarchar(2),
  Objectid              nvarchar(100),
  Created               DATETIME,
  CreatedBy             USERID,
  Modified              DATETIME,
  ModifiedBy            USERID,
  Container	            entityId NOT NULL,

  CONSTRAINT PK_CNPRC_GRNT_CHRG PRIMARY KEY (rowid),
  CONSTRAINT FK_CNPRC_GRNT_CHRG_CONTAINER FOREIGN KEY (container) REFERENCES core.Containers (EntityId)

);
GO

CREATE INDEX CNPRC_GRNT_CHRG_CONT_IDX ON cnprc_billing.grant_charge (Container);
GO

CREATE TABLE cnprc_billing.grant_person_effort (

  rowid                         INT IDENTITY(1,1) NOT NULL,
  Gpe_pk                        int,
  Pid_fk                        int,
  Gpp_fk                        int,
  Gpe_Percent                   float,
  Person_months                 float,
  Nih_key_personnel_yn          nvarchar(1),
  Exclude_from_reports_yn       nvarchar(1),
  Objectid                      nvarchar(100),
  Created                       DATETIME,
  CreatedBy                     USERID,
  Modified                      DATETIME,
  ModifiedBy                    USERID,
  Container	                    entityId NOT NULL,

  CONSTRAINT PK_CNPRC_GRANT_PER_EFF PRIMARY KEY (rowid),
  CONSTRAINT FK_CNPRC_GRANT_PER_EFF_CONTAINER FOREIGN KEY (container) REFERENCES core.Containers (EntityId)

);
GO

CREATE INDEX GRANT_PER_EFF_CONT_IDX ON cnprc_billing.grant_person_effort (Container);
GO


CREATE TABLE cnprc_billing.grant_project_period (

  rowid                 INT IDENTITY(1,1) NOT NULL,
  Gpp_pk                      int,
  Grant_type                  nvarchar(3),
  Ref_no                      nvarchar(20),
  Title                       nvarchar(150),
  Agency                      nvarchar(60),
  Status                      nvarchar(1),
  Ucd_per_fk                  int,
  Center_unit_fk              float,
  Funding_type                nvarchar(4),
  Begin_date                  datetime,
  End_date                    datetime,
  Direct_costs                float,
  Total_costs                 float,
  Brate_direct_costs          float,
  Brate_total_costs           float,
  Admin_department            nvarchar(6),
  Primate_yn                  nvarchar(1),
  Aids_grant_yn               nvarchar(1),
  Phs_yn                      nvarchar(1),
  Service_only_yn             nvarchar(1),
  Flag_1                      nvarchar(6),
  Flag_2                      nvarchar(6),
  Sub_institution             nvarchar(100),
  Sub_pi_name                 nvarchar(80),
  Comment_1                   nvarchar(2000),
  Comment_2                   nvarchar(2000),
  Dafis_award_num             nvarchar(20),
  Sub_agency                  nvarchar(60),
  Grant_affiliation_type      nvarchar(6),
  Sub_pi_per_fk               int,
  Submission_date             datetime,
  Special_funding_type        nvarchar(4),
  Last_reported_date          datetime,
  Base_grant_indicator_yn     nvarchar(1),
  Objectid                        nvarchar(100),
  Created                         DATETIME,
  CreatedBy                       USERID,
  Modified                        DATETIME,
  ModifiedBy                      USERID,
  Container	                      entityId NOT NULL,

  CONSTRAINT PK_CNPRC_GRANT_PROJ_PER PRIMARY KEY (rowid),
  CONSTRAINT FK_CNPRC_GRANT_PROJ_PER_CONTAINER FOREIGN KEY (container) REFERENCES core.Containers (EntityId)

);
GO

CREATE INDEX CNPRC_GRANT_PROJ_PER_CONT_IDX ON cnprc_billing.grant_project_period (Container);
GO

CREATE TABLE cnprc_billing.grant_year (

  rowid                            INT IDENTITY(1,1) NOT NULL,
  Gyr_pk                           int,
  Gpp_fk                           int,
  Budget_yr                        float,
  Award_status                     nvarchar(2),
  Supplement_yn                    nvarchar(1),
  Begin_date                       datetime,
  End_date                         datetime,
  Direct_costs                     float,
  Total_costs                      float,
  Brate_direct_costs               float,
  Brate_total_costs                float,
  Sponsored_pgm_ref_no             nvarchar(20),
  Totals_indicator_yn              nvarchar(1),
  Base_grant_year_end              int,
  Submission_date                  datetime,
  Special_funding_type             nvarchar(4),
  Last_reported_date               datetime,
  Objectid                         nvarchar(100),
  Created                          DATETIME,
  CreatedBy                        USERID,
  Modified                         DATETIME,
  ModifiedBy                       USERID,
  Container	                       entityId NOT NULL,

  CONSTRAINT PK_CNPRC_GRANT_YR PRIMARY KEY (rowid),
  CONSTRAINT FK_CNPRC_GRANT_YR_CONTAINER FOREIGN KEY (container) REFERENCES core.Containers (EntityId)

);
GO

CREATE INDEX CNPRC_GRANT_YR_CONT_IDX ON cnprc_billing.grant_year (Container);
GO




 CREATE TABLE cnprc_billing.org_resource_title (

  rowid                 INT IDENTITY(1,1) NOT NULL,
  Org_resource_title_pk int,
  Resource_title_scheme nvarchar(10),
  Rc_code               nvarchar(2),
  Resource_group        nvarchar(40),
  Resource_center       nvarchar(40),
  Resource_group_center nvarchar(100),
  Group_sort_order      int,
  Center_sort_order     int,
  Active_yn             nvarchar(1),
  Display_yn            nvarchar(1),
  Resource_comment      nvarchar(120),
  Objectid              nvarchar(100),
  Created               DATETIME,
  CreatedBy             USERID,
  Modified              DATETIME,
  ModifiedBy            USERID,
  Container	            entityId NOT NULL,

  CONSTRAINT PK_CNPRC_ORG_RES_TTL_TITLE PRIMARY KEY (rowid),
  CONSTRAINT FK_CNPRC_ORG_RES_TTL_CONTAINER FOREIGN KEY (container) REFERENCES core.Containers (EntityId)

);
GO

CREATE INDEX CNPRC_ORG_RES_TTL_CONTAINER_INDEX ON cnprc_billing.org_resource_title (Container);
GO


CREATE TABLE cnprc_billing.school (

  rowid               INT IDENTITY(1,1) NOT NULL,
  School_code         nvarchar(2),
  School_desc         nvarchar(30),
  School_name         nvarchar(50),
  Objectid            nvarchar(100),
  Created             DATETIME,
  CreatedBy           USERID,
  Modified            DATETIME,
  ModifiedBy          USERID,
  Container	          entityId NOT NULL,

  CONSTRAINT PK_CNPRC_SCHOOL PRIMARY KEY (rowid),
  CONSTRAINT FK_CNPRC_SCHOOL_CONTAINER FOREIGN KEY (container) REFERENCES core.Containers (EntityId)

);
GO

CREATE INDEX CNPRC_SCHOOL ON cnprc_billing.school (Container);
GO



 CREATE TABLE cnprc_billing.billing_fiscal (

  rowid                          INT IDENTITY(1,1) NOT NULL,
  Period_end                     datetime,
  Fiscal_year                    int,
  Fiscal_period                  int,
  Basegrant_year                 int,
  Basegrant_cycle                int,
  Fiscalyear_begin               datetime,
  Fiscalyear_end                 datetime,
  Fiscal_range                   nvarchar(10),
  Dafis_univ_fiscal_yr           int,
  Dafis_univ_fiscal_prd_cd       nvarchar(2),
  Period_begin                   datetime,
  Fiscal_range_abbrev_1          nvarchar(7),
  Fiscal_range_abbrev_2          nvarchar(7),
  Fiscal_range_abbrev_3          nvarchar(5),
  Dafis_univ_fy_range            nvarchar(10),
  Dafis_univ_fy_range_abbrev_1   nvarchar(7),
  Days_in_period                 int,
  Objectid                       nvarchar(100),
  Created                        DATETIME,
  CreatedBy                      USERID,
  Modified                       DATETIME,
  ModifiedBy                     USERID,
  Container	                     entityId NOT NULL,

  CONSTRAINT PK_CNPRC_BILLING_FISCAL PRIMARY KEY (rowid),
  CONSTRAINT FK_CNPRC_BILLING_FISCAL_CONTAINER FOREIGN KEY (container) REFERENCES core.Containers (EntityId)

);
GO

CREATE INDEX CNPRC_BILLING_FISCAL_CONTAINER_INDEX ON cnprc_billing.billing_fiscal (Container);
GO
