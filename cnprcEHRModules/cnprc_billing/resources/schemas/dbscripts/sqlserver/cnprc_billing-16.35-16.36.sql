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

ALTER TABLE cnprc_billing.account ADD Cur_mail_line_6              nvarchar(40);
ALTER TABLE cnprc_billing.account ADD Cur_mail_line_7              nvarchar(40);
ALTER TABLE cnprc_billing.account ADD Bill_contact_fax             nvarchar(18);
ALTER TABLE cnprc_billing.account ADD Bill_contact_email           nvarchar(50);
ALTER TABLE cnprc_billing.account ADD Ar_customer_no               nvarchar(9);
ALTER TABLE cnprc_billing.account ADD Rev_lafs_location            nvarchar(1);
ALTER TABLE cnprc_billing.account ADD Rev_lafs_account             nvarchar(7);
ALTER TABLE cnprc_billing.account ADD Rev_lafs_fund                nvarchar(6);
ALTER TABLE cnprc_billing.account ADD Rev_lafs_sub                 nvarchar(2);
ALTER TABLE cnprc_billing.account ADD Lafs_obj_override            nvarchar(4);
ALTER TABLE cnprc_billing.account ADD Cur_lafs_obj_override        nvarchar(4);
ALTER TABLE cnprc_billing.account ADD Blanket_project_override     nvarchar(10);
ALTER TABLE cnprc_billing.account ADD Casp_fin_coa_cd              nvarchar(2);
ALTER TABLE cnprc_billing.account ADD Casp_account_nbr             nvarchar(7);
ALTER TABLE cnprc_billing.account ADD Casp_sub_acct_nbr            nvarchar(5);
ALTER TABLE cnprc_billing.account ADD Casp_project_cd              nvarchar(10);
ALTER TABLE cnprc_billing.account ADD Casp_obj_override            nvarchar(4);
ALTER TABLE cnprc_billing.account ADD Idt_casp_fin_coa_cd          nvarchar(2);
ALTER TABLE cnprc_billing.account ADD Idt_casp_account_nbr         nvarchar(7);
ALTER TABLE cnprc_billing.account ADD Idt_casp_sub_acct_nbr        nvarchar(5);
ALTER TABLE cnprc_billing.account ADD Idt_casp_project_cd          nvarchar(10);
ALTER TABLE cnprc_billing.account ADD Cur_casp_fin_coa_cd          nvarchar(2);
ALTER TABLE cnprc_billing.account ADD Cur_casp_account_nbr         nvarchar(7);
ALTER TABLE cnprc_billing.account ADD Cur_casp_sub_acct_nbr        nvarchar(5);
ALTER TABLE cnprc_billing.account ADD Cur_casp_project_cd          nvarchar(10);
ALTER TABLE cnprc_billing.account ADD Cur_casp_obj_override        nvarchar(4);
ALTER TABLE cnprc_billing.account ADD Rev_casp_fin_coa_cd          nvarchar(2);
ALTER TABLE cnprc_billing.account ADD Rev_account_nbr              nvarchar(7);
ALTER TABLE cnprc_billing.account ADD Rev_sub_acct_nbr             nvarchar(5);
ALTER TABLE cnprc_billing.account ADD Rev_casp_project_cd          nvarchar(10);
ALTER TABLE cnprc_billing.account ADD Idt_lookup_date              DATETIME;
ALTER TABLE cnprc_billing.account ADD Casp_sub_obj_cd              nvarchar(3);
ALTER TABLE cnprc_billing.account ADD Cur_casp_sub_obj_cd          nvarchar(3);
ALTER TABLE cnprc_billing.account ADD Rev_casp_account_nbr         nvarchar(7);
ALTER TABLE cnprc_billing.account ADD Rev_casp_object_cd           nvarchar(4);
ALTER TABLE cnprc_billing.account ADD Rev_casp_sub_obj_cd          nvarchar(3);
ALTER TABLE cnprc_billing.account ADD Idt_billing_id               nvarchar(4);
ALTER TABLE cnprc_billing.account ADD Cur_casp_billing_id          nvarchar(4);
ALTER TABLE cnprc_billing.account ADD Billing_id                   nvarchar(4);
ALTER TABLE cnprc_billing.account ADD Idt_acct_nm                  nvarchar(40);
ALTER TABLE cnprc_billing.account ADD Cur_billing_id               nvarchar(4);
ALTER TABLE cnprc_billing.account ADD Rev_casp_sub_acct_nbr        nvarchar(5);
ALTER TABLE cnprc_billing.account ADD Billing_fund_source_code     nvarchar(12);
ALTER TABLE cnprc_billing.account ADD Sd_client_nud_exempt_yn      nvarchar(1);
ALTER TABLE cnprc_billing.account ADD Sd_client_nud_rate_code      nvarchar(1);
ALTER TABLE cnprc_billing.account ADD Sd_client_mrkup_exempt_yn    nvarchar(1);
ALTER TABLE cnprc_billing.account ADD Type_description             nvarchar(20);
ALTER TABLE cnprc_billing.account ADD Cnprc_grant_info_type        nvarchar(4);
ALTER TABLE cnprc_billing.account ADD Grant_project_period_fk      int;
ALTER TABLE cnprc_billing.account ADD Stub_grant_number            nvarchar(20);
ALTER TABLE cnprc_billing.account ADD Stub_grant_agency            nvarchar(60);
ALTER TABLE cnprc_billing.account ADD Grant_funding_type           nvarchar(4);
ALTER TABLE cnprc_billing.account ADD Stub_grant_pi_fk             int;
ALTER TABLE cnprc_billing.account ADD Cnprc_person_fk              int;
ALTER TABLE cnprc_billing.account ADD Cnprc_income_account_yn      nvarchar(1);
ALTER TABLE cnprc_billing.account ADD Dafis_org_id                 nvarchar(4);
ALTER TABLE cnprc_billing.account ADD Dafis_org_id_lookup_date     DATETIME;
ALTER TABLE cnprc_billing.account ADD Dafis_reports_to_org_id      nvarchar(4);
ALTER TABLE cnprc_billing.account ADD Rate_tier_code_fk            nvarchar(4);
                                                                   
                                                                   
                                                                   
                                                                   









