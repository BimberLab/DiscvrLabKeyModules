/*
 * Copyright (c) 2012 LabKey Corporation
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
drop table laboratory.employees;

alter table laboratory.peptides drop ref_aa;
alter table laboratory.peptides drop ref_aa_name;
alter table laboratory.peptides drop ref_aa_start;
alter table laboratory.peptides drop ref_aa_stop;
alter table laboratory.peptides drop mhc_restriction;
alter table laboratory.peptides drop mhc_nt_id;

CREATE TABLE laboratory.reference_peptides (
    rowid SERIAL NOT NULL,
    sequence varchar(200),

    ref_aa_id integer,
    ref_aa_start integer,
    ref_aa_stop integer,
    mhc_restriction varchar(1000),

    createdBy USERID,
    created TIMESTAMP,
    modifiedBy USERID,
    modified TIMESTAMP,

    constraint PK_reference_peptides PRIMARY KEY (rowid)
);