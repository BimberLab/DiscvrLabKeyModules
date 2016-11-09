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

CREATE TABLE cnprc_billing.core_service_basic_components (

  rowid                   INT IDENTITY(1,1) NOT NULL,
  Csbc_pk                 int,
  Component_code          nvarchar(6),
  Service_code            nvarchar(2),
  Rc_code                 nvarchar(2),
  Description             nvarchar(40),
  Basic_uom               nvarchar(10),
  Basic_rate              float,
  Cost_code               nvarchar(20),
  Sundry_debtor_markup_yn nvarchar(2),
  Labor_yn                nvarchar(1),
  Objectid                nvarchar(100),
  Created                 DATETIME,
  CreatedBy               USERID,
  Modified                DATETIME,
  ModifiedBy              USERID,
  Container	              entityId NOT NULL,

  CONSTRAINT PK_CNPRC_CORE_SERVICE_BASIC_COMPONENTS PRIMARY KEY (rowid),
  CONSTRAINT FK_CNPRC_CORE_SERVICE_BASIC_COMPONENTS_CONTAINER FOREIGN KEY (container) REFERENCES core.Containers (EntityId)

);
GO

CREATE INDEX CNPRC_CORE_SERVICE_BASIC_COMPONENTS_CONT_IDX ON cnprc_billing.core_service_basic_components (Container);
GO


CREATE TABLE cnprc_billing.account (

  rowid                 INT IDENTITY(1,1) NOT NULL,
  Acct_id                   nvarchar(6),
  Charge_id                 nvarchar(4),
  Begin_date                datetime,
  End_date                  datetime,
  Replacement_acct_id       nvarchar(6),
  Title                     nvarchar(36),
  Investigator              nvarchar(18),
  Fund_type                 nvarchar(1),
  Cost_type_scheme          int,
  Billing_method            nvarchar(4),
  Blanket_id                nvarchar(4),
  Blanket_sub_override      nvarchar(5),
  Lafs_location             nvarchar(1),
  Lafs_account              nvarchar(7),
  Lafs_fund                 nvarchar(6),
  Lafs_sub                  nvarchar(2),
  Closing_months            int,
  Po_reference              nvarchar(16),
  Po_on_file                nvarchar(1),
  Tax_exempt_ref            nvarchar(14),
  Client_type               nvarchar(1),
  Ship_to_ca                nvarchar(1),
  Bill_contact_name         nvarchar(12),
  Bill_contact_phone        nvarchar(18),
  Mail_company_name         nvarchar(50),
  Mail_division_name        nvarchar(50),
  Mail_street_address       nvarchar(40),
  Mail_city                 nvarchar(24),
  Mail_state_abbrev         nvarchar(2),
  Mail_zip_code             nvarchar(10),
  Mail_country              nvarchar(18),
  Idfile_blanket_id         nvarchar(4),
  Idfile_lafs_location      nvarchar(1),
  Idfile_lafs_account       nvarchar(7),
  Idfile_lafs_fund          nvarchar(6),
  Idfile_lafs_sub           nvarchar(2),
  Idfile_title              nvarchar(35),
  Idfile_lookup_date        datetime,
  Cur_blanket_id            nvarchar(4),
  Cur_lafs_location         nvarchar(1),
  Cur_lafs_account          nvarchar(7),
  Cur_lafs_fund             nvarchar(6),
  Cur_lafs_sub              nvarchar(2),
  Cur_clear_flag            nvarchar(1),
  Cur_mailroom_zip          nvarchar(5),
  Cur_mail_line_1           nvarchar(40),
  Cur_mail_line_2           nvarchar(40),
  Cur_mail_line_3           nvarchar(40),
  Cur_mail_line_4           nvarchar(40),
  Cur_mail_line_5           nvarchar(40),
  Objectid                  nvarchar(100),
  Created                   DATETIME,
  CreatedBy                 USERID,
  Modified                  DATETIME,
  ModifiedBy                USERID,
  Container	                entityId NOT NULL,

  CONSTRAINT PK_CNPRC_ACCOUNT PRIMARY KEY (rowid),
  CONSTRAINT FK_CNPRC_ACCOUNT_CONTAINER FOREIGN KEY (container) REFERENCES core.Containers (EntityId)

);
GO

CREATE INDEX CNPRC_ACCOUNT_CONT_IDX ON cnprc_billing.account (Container);
GO


CREATE TABLE cnprc_billing.billing_control (

  rowid                 INT IDENTITY(1,1) NOT NULL,
  Billc_key                        int,
  Fed_ein                          nvarchar(10),
  Dept_name                        nvarchar(16),
  Service_dept_no                  nvarchar(2),
  Journal_suffix                   nvarchar(3),
  Preparer_name                    nvarchar(16),
  Preparer_phone                   nvarchar(14),
  Exmemo_name                      nvarchar(26),
  Icmemo_name                      nvarchar(26),
  Period_ending_date               datetime,
  Sales_tax_rate                   float,
  First_invoice_no                 int,
  Next_invoice_no                  int,
  Perdiem_start_date               datetime,
  Perdiem_ending_date              datetime,
  Nud_a_rate_part_a                float,
  Nud_a_service_code               nvarchar(2),
  Nud_a_rc_code                    nvarchar(2),
  Nud_a_cost_code                  nvarchar(20),
  Nud_a_billing_method             nvarchar(2),
  Nud_a_collection_acct            nvarchar(5),
  Nud_a_sub_object_code            nvarchar(3),
  Nud_b_rate_part_b                float,
  Nud_b_service_code               nvarchar(2),
  Nud_b_rc_code                    nvarchar(2),
  Nud_b_cost_code                  nvarchar(20),
  Nud_b_billing_method             nvarchar(2),
  Nud_b_collection_acct            nvarchar(5),
  Nud_b_sub_object_code            nvarchar(3),
  Nud_c_rate_part_c                float,
  Nud_c_service_code               nvarchar(2),
  Nud_c_rc_code                    nvarchar(2),
  Nud_c_cost_code                  nvarchar(20),
  Nud_c_billing_method             nvarchar(2),
  Nud_c_collection_acct            nvarchar(5),
  Nud_c_sub_object_code            nvarchar(3),
  Central_spply_status_closed_yn   nvarchar(1),
  Central_spply_status_set_by      nvarchar(50),
  Central_spply_status_timestamp   datetime,
  Perdiem_status_closed_yn         nvarchar(1),
  Perdiem_status_set_by            nvarchar(50),
  Perdiem_status_timestamp         datetime,
  Work_orders_status_closed_yn     nvarchar(1),
  Work_orders_status_set_by        nvarchar(50),
  Work_orders_status_timestamp     datetime,
  Core_services_status_closed_yn   nvarchar(1),
  Core_services_status_set_by      nvarchar(50),
  Core_services_status_timestamp   datetime,
  Period_start_date                datetime,
  Sd_next_credit_memo_no           int,
  Objectid                          nvarchar(100),
  Created                           DATETIME,
  CreatedBy                         USERID,
  Modified                          DATETIME,
  ModifiedBy                        USERID,
  Container	                        entityId NOT NULL,

  CONSTRAINT PK_CNPRC_BILLING_CONTROL PRIMARY KEY (rowid),
  CONSTRAINT FK_CNPRC_BILLING_CONTROL_CONTAINER FOREIGN KEY (container) REFERENCES core.Containers (EntityId)

);
GO

CREATE INDEX CNPRC_BILLING_CONTROL_CONT_IDX ON cnprc_billing.billing_control (Container);
GO


CREATE TABLE cnprc_billing.billing_cost_code (

  rowid                 INT IDENTITY(1,1) NOT NULL,
  Service_code                nvarchar(2),
  Cost_code                   nvarchar(20),
  Begin_date                  datetime,
  Cost_code_type              nvarchar(2),
  Base_grant_exempt_factor    float,
  Routine_serv_exempt_factor  float,
  Old_cost_code               nvarchar(4),
  Sales_taxable_flag          nvarchar(2),
  Charge_obj                  nvarchar(4),
  Charge_ct_0                 nvarchar(5),
  Charge_ct_1                 nvarchar(5),
  Charge_ct_2                 nvarchar(5),
  Charge_ct_3                 nvarchar(5),
  Charge_ct_4                 nvarchar(5),
  Charge_ct_5                 nvarchar(5),
  Charge_ct_6                 nvarchar(5),
  Charge_ct_7                 nvarchar(5),
  Charge_ct_8                 nvarchar(5),
  Charge_ct_9                 nvarchar(5),
  Charge_ct_10                nvarchar(5),
  Charge_ct_11                nvarchar(5),
  Charge_ct_12                nvarchar(5),
  Charge_ct_13                nvarchar(5),
  Charge_ct_14                nvarchar(5),
  Charge_ct_15                nvarchar(5),
  Charge_ct_16                nvarchar(5),
  Charge_ct_17                nvarchar(5),
  Charge_ct_18                nvarchar(5),
  Charge_ct_19                nvarchar(5),
  Income_id                   nvarchar(4),
  Income_sub                  nvarchar(2),
  Income_obj                  nvarchar(4),
  Income_ct                   nvarchar(5),
  Costxfer_to_id              nvarchar(4),
  Costxfer_to_sub             nvarchar(2),
  Costxfer_to_obj             nvarchar(4),
  Costxfer_to_ct              nvarchar(5),
  Costxfer_fr_id              nvarchar(4),
  Costxfer_fr_sub             nvarchar(2),
  Costxfer_fr_obj             nvarchar(4),
  Costxfer_fr_ct              nvarchar(5),
  External_descr              nvarchar(32),
  End_date                    datetime,
  Rc_code                     nvarchar(2),
  Acct_type                   nvarchar(2),
  Nud_exempt_flag             nvarchar(1),
  Objectid              nvarchar(100),
  Created               DATETIME,
  CreatedBy             USERID,
  Modified              DATETIME,
  ModifiedBy            USERID,
  Container	            entityId NOT NULL,

  CONSTRAINT PK_CNPRC_BILLING_COST_CODE PRIMARY KEY (rowid),
  CONSTRAINT FK_CNPRC_BILLING_COST_CODE_CONTAINER FOREIGN KEY (container) REFERENCES core.Containers (EntityId)

);
GO

CREATE INDEX CNPRC_BILLING_COST_CODE_CONT_IDX ON cnprc_billing.billing_cost_code (Container);
GO


CREATE TABLE cnprc_billing.cost_code (

  rowid                 INT IDENTITY(1,1) NOT NULL,
  Cost_code             nvarchar(20),
  Objectid              nvarchar(100),
  Created               DATETIME,
  CreatedBy             USERID,
  Modified              DATETIME,
  ModifiedBy            USERID,
  Container	            entityId NOT NULL,

  CONSTRAINT PK_CNPRC_COST_CODE PRIMARY KEY (rowid),
  CONSTRAINT FK_CNPRC_COST_CODE_CONTAINER FOREIGN KEY (container) REFERENCES core.Containers (EntityId)

);
GO

CREATE INDEX CNPRC_COST_CODE_CONT_IDX ON cnprc_billing.cost_code (Container);
GO

CREATE TABLE cnprc_billing.core_service_billing_master (

  rowid                 INT IDENTITY(1,1) NOT NULL,
  Csbm_pk                           int,
  Billing_master_origin             nvarchar(10),
  Billing_master_create_date        datetime,
  Period_ending_date                datetime,
  Dafis_fin_coa_cd                  nvarchar(2),
  Dafis_account_nbr                 nvarchar(7),
  Dafis_sub_acct_nbr                nvarchar(5),
  Dafis_project_cd                  nvarchar(10),
  Dafis_obj_override                nvarchar(4),
  Service_code                      nvarchar(2),
  Rc_code                           nvarchar(2),
  Charge_id                         nvarchar(4),
  Account_id                        nvarchar(6),
  Billing_method_code               nvarchar(2),
  Project_code                      nvarchar(5),
  Order_date                        datetime,
  Client_name                       nvarchar(100),
  Client_po_num                     nvarchar(20),
  Client_request_num                nvarchar(20),
  Billing_contact_name              nvarchar(100),
  Total_charge_amt                  float,
  Total_exempt_amt                  float,
  Order_fk                          int,
  Pc_invoice_number                 nvarchar(8),
  Report_contact_name               nvarchar(100),
  Objectid              nvarchar(100),
  Created               DATETIME,
  CreatedBy             USERID,
  Modified              DATETIME,
  ModifiedBy            USERID,
  Container	            entityId NOT NULL,

  CONSTRAINT PK_CNPRC_CORE_SERVICE_BILLING_MASTER PRIMARY KEY (rowid),
  CONSTRAINT FK_CORE_SERVICE_BILLING_MASTER_CONTAINER FOREIGN KEY (container) REFERENCES core.Containers (EntityId)

);
GO

CREATE INDEX CORE_SERVICE_BILLING_MASTER_CONT_IDX ON cnprc_billing.core_service_billing_master (Container);
GO


CREATE TABLE cnprc_billing.core_service_costing (

  rowid                 INT IDENTITY(1,1) NOT NULL,
  Csc_pk                      int,
  Csbm_fk                     int,
  Csli_fk                     int,
  Line_no                     int,
  Item_code                   nvarchar(6),
  Item_description            nvarchar(40),
  Line_item_qty               float,
  Line_item_uom               nvarchar(10),
  Component_seq               int,
  Component_description       nvarchar(40),
  Component_rate              float,
  Component_uom               nvarchar(10),
  Component_qty               float,
  Component_service_code      nvarchar(2),
  Component_rc_code           nvarchar(2),
  Component_cost_code         nvarchar(20),
  Component_sd_markup_yn      nvarchar(2),
  Component_labor_yn          nvarchar(1),
  Component_cost_rate         float,
  Costing_qty                 float,
  Costing_charge_amt          float,
  Costing_exempt_amt          float,
  Objectid              nvarchar(100),
  Created               DATETIME,
  CreatedBy             USERID,
  Modified              DATETIME,
  ModifiedBy            USERID,
  Container	            entityId NOT NULL,

  CONSTRAINT PK_CNPRC_CORE_SERVICE_COSTING PRIMARY KEY (rowid),
  CONSTRAINT FK_CNPRC_CORE_SERVICE_COSTING_CONTAINER FOREIGN KEY (container) REFERENCES core.Containers (EntityId)

);
GO

CREATE INDEX CNPRC_CORE_SERVICE_COSTING_CONT_IDX ON cnprc_billing.core_service_costing (Container);
GO


CREATE TABLE cnprc_billing.eom_transaction_detail (

  rowid                 INT IDENTITY(1,1) NOT NULL,
  Feed_system_code                   nvarchar(10),
  Feed_date                          datetime,
  Period_ending_date                 datetime,
  Charge_account_id                  nvarchar(6),
  Charge_object_code_old             nvarchar(2),
  Charge_object_code                 nvarchar(4),
  Charge_sub_account                 nvarchar(5),
  Income_account_id                  nvarchar(6),
  Income_object_code_old             nvarchar(2),
  Income_object_code                 nvarchar(4),
  Income_sub_account                 nvarchar(5),
  Service_code                       nvarchar(2),
  Transaction_type_flag              nvarchar(2),
  Sales_taxable_flag                 nvarchar(2),
  Bill_description_external          nvarchar(32),
  Transaction_description            nvarchar(32),
  Transaction_reference              nvarchar(10),
  Transaction_amount                 float,
  Transaction_sign                   nvarchar(1),
  Invoice_number                     nvarchar(8),
  Nud_exempt_flag                    nvarchar(1),
  Cost_code                          nvarchar(20),
  Rc_code                            nvarchar(2),
  Billing_method_code                nvarchar(4),
  Client_po_number                   nvarchar(20),
  Transaction_comment                nvarchar(40),
  Etd_aud_username                   nvarchar(20),
  Etd_aud_time                       datetime,
  Etd_aud_code                       nvarchar(1),
  Etd_aud_comment                    nvarchar(25),
  Credit_entry_flag                  nvarchar(2),
  Credit_entry_comment               nvarchar(120),
  Credit_entry_prior_ref_no          nvarchar(10),
  Credit_entry_prior_invoice_no      nvarchar(8),
  Credit_memo_number                 nvarchar(8),
  Income_sub_object_code             nvarchar(3),
  Charge_sub_object_code             nvarchar(3),
  Transaction_project_num            nvarchar(10),
  Gl_feed_processed_flag             nvarchar(1),
  Ar_feed_processed_flag             nvarchar(1),
  Objectid              nvarchar(100),
  Created               DATETIME,
  CreatedBy             USERID,
  Modified              DATETIME,
  ModifiedBy            USERID,
  Container	            entityId NOT NULL,

  CONSTRAINT PK_CNPRC_EOM_TRANSACTION_DETAIL PRIMARY KEY (rowid),
  CONSTRAINT FK_CNPRC_EOM_TRANSACTION_DETAIL_CONTAINER FOREIGN KEY (container) REFERENCES core.Containers (EntityId)

);
GO

CREATE INDEX CNPRC_EOM_TRANSACTION_DETAIL_CONT_IDX ON cnprc_billing.eom_transaction_detail (Container);
GO
