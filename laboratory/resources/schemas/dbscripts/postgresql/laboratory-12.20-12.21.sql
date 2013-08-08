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
CREATE TABLE laboratory.samplecategory
(
    category VARCHAR(255) NOT NULL,
    CONSTRAINT PK_samplecategory PRIMARY KEY (category)
);

-- ----------------------------
-- Records of samplecategory
-- ----------------------------
INSERT INTO laboratory.samplecategory VALUES ('CTL');
INSERT INTO laboratory.samplecategory VALUES ('Sample');
INSERT INTO laboratory.samplecategory VALUES ('STD');

CREATE TABLE laboratory.qual_results
(
    rowid serial,
    meaning VARCHAR(100) NOT NULL,
    description varchar(500),
    CONSTRAINT PK_qual_results PRIMARY KEY (rowid)
);

INSERT INTO laboratory.qual_results (meaning, description) VALUES ('POS', 'Positive');
INSERT INTO laboratory.qual_results (meaning, description) VALUES ('NEG', 'Negative');
INSERT INTO laboratory.qual_results (meaning, description) VALUES ('OUTLIER', 'Outlier');
INSERT INTO laboratory.qual_results (meaning, description) VALUES ('ND', 'No Data');

CREATE TABLE laboratory.assay_requests
(
    rowid serial,
    assay VARCHAR(100) NOT NULL,
    sampleName varchar(100),
    sampleId int,
    requestor int,
    institution varchar(200),
    investigator varchar(200),
    project varchar(100),
    account int,

    string1 varchar(200),
    string2 varchar(200),
    string3 varchar(200),
    double1 double precision,
    double2 double precision,
    double3 double precision,

    comment varchar(4000),
    CONSTRAINT PK_assay_requests PRIMARY KEY (rowid)
);
