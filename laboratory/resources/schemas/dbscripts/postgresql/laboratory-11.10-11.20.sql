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



/* laboratory-11.12-11.13.sql */

ALTER TABLE laboratory.inventory 
  add column ratio float,
  drop column freezer,
  add column freezer varchar(100)
;

/* laboratory-11.13-11.14.sql */

INSERT INTO laboratory.sample_type VALUES
('BLCL'),
('DNA'),
('Whole Blood')
;

ALTER TABLE laboratory.inventory
  add column cell_number float
;

/* laboratory-11.14-11.15.sql */

ALTER TABLE laboratory.inventory
  add column preparationmethod varchar(200),
  add column samplesubtype varchar(200),
  add column quantity varchar(200),
  add column quantity_units varchar(50),
  drop column cell_number
;

-- ----------------------------
-- Table structure for laboratory.cell_type
-- ----------------------------
DROP TABLE IF EXISTS laboratory.cell_type;
CREATE TABLE laboratory.cell_type (
  type varchar(105) NOT NULL,

  CONSTRAINT PK_cell_type PRIMARY KEY (type)
)
WITH (OIDS=FALSE)

;

/* laboratory-11.15-11.16.sql */

DROP TABLE IF EXISTS laboratory.Employees;
CREATE TABLE laboratory.Employees (
    rowid serial not null,
    userid integer not null,
    LastName varchar(255) not null,
    FirstName varchar(255),
    Email varchar(255),
    Email2 varchar(255),
    category varchar(255),
    Title varchar(255),
    Supervisor varchar(255),
    EmergencyContact varchar(255),
    EmergencyContactPhone varchar(255),
    HomePhone varchar(255),
    OfficePhone varchar(255),
    CellPhone varchar(255),
    StartDate timestamp,
    EndDate timestamp,
    Notes varchar(255),

    Container ENTITYID NOT NULL,
    CreatedBy USERID,
    Created TIMESTAMP,
    ModifiedBy USERID,
    Modified TIMESTAMP,

    CONSTRAINT PK_Employees PRIMARY KEY (rowid),
    CONSTRAINT UNIQUE_Employees UNIQUE (userid, container)
);

DROP TABLE IF EXISTS laboratory.reports;
CREATE TABLE laboratory.reports
(
  rowid serial NOT NULL,
  reportname character varying(255) DEFAULT NULL::character varying,
  category character varying(255) DEFAULT NULL::character varying,
  reporttype character varying(255) DEFAULT NULL::character varying,
  reporttitle character varying(255) DEFAULT NULL::character varying,
  visible boolean,
  containerpath character varying(255) DEFAULT NULL::character varying,
  schemaname character varying(255) DEFAULT NULL::character varying,
  queryname character varying(255) DEFAULT NULL::character varying,
  viewname character varying(255) DEFAULT NULL::character varying,
  report character varying(255) DEFAULT NULL::character varying,
  container entityid NOT NULL,
  createdby userid NOT NULL,
  created timestamp without time zone NOT NULL,
  modifiedby userid NOT NULL,
  modified timestamp without time zone NOT NULL,
  jsonconfig character varying(4000),
  description character varying(4000),
  sort_order integer,
  CONSTRAINT pk_reports PRIMARY KEY (rowid )
);


DROP TABLE IF EXISTS laboratory.report_types;
CREATE TABLE laboratory.report_types
(
  type varchar(100) NOT NULL,
  CONSTRAINT pk_report_types PRIMARY KEY (type )
);

INSERT INTO laboratory.report_types
(type) VALUES
('query'),
('js'),
('report'),
('webpart'),
('details')
;


-- ----------------------------
-- Table structure for laboratory.subjects
-- ----------------------------
DROP TABLE IF EXISTS laboratory.subjects;
CREATE TABLE laboratory.subjects (
RowId serial NOT NULL,
SubjectName varchar(255),

gender varchar(100),
species varchar(255),
birth TIMESTAMP,
death TIMESTAMP,
mother varchar(100),
father varchar(100),
geographic_origin varchar(500),

comments text default null,

Container ENTITYID NOT NULL,
CreatedBy USERID,
Created TIMESTAMP,
ModifiedBy USERID,
Modified TIMESTAMP,

CONSTRAINT Unique_subjects UNIQUE (container, subjectname),
CONSTRAINT PK_subjects PRIMARY KEY (rowId)

);

-- ----------------------------
-- Table structure for laboratory.species
-- ----------------------------
DROP TABLE IF EXISTS laboratory.species;
CREATE TABLE laboratory.species (
common_name varchar(255) not null,
scientific_name varchar(255) DEFAULT NULL,
mhc_prefix varchar(255) DEFAULT NULL,

CONSTRAINT PK_species PRIMARY KEY (common_name)
)
WITH (OIDS=FALSE)

;

-- ----------------------------
-- Records of laboratory.species
-- ----------------------------
INSERT INTO laboratory.species
(common_name, scientific_name, mhc_prefix)
VALUES
('Baboon', null, null),
('Cotton-top Tamarin', 'Saguinus oedipus', 'Saoe'),
('Cynomolgus', 'Macaca fascicularis', 'Mafa'),
('Marmoset', 'Callithrix jacchus', 'Caja'),
('Pigtail', 'Macaca Nemestrina', 'Mane'),
('Rhesus', 'Macaca mulatta', 'Mamu'),
('Sooty Mangabey', 'Cercocebus atys', 'Ceat'),
('Stump Tailed', 'Macaca Arctoides', 'Maar'),
('Vervet', 'Chlorocebus sabaeus', 'Chsa'),
('Human', 'Homo Sapiens', 'HLA'),
('Mouse', 'Mus musculus', null),
('Rat', 'Rattus norvegicus', null)
;


DROP TABLE IF EXISTS laboratory.geographic_origins;
CREATE TABLE laboratory.geographic_origins (
origin varchar(255) not null,

CONSTRAINT PK_geographic_origins PRIMARY KEY (origin)
)
WITH (OIDS=FALSE)

;


DROP TABLE IF EXISTS laboratory.well_layout;
CREATE TABLE laboratory.well_layout (
  rowid serial not null,
  Plate integer,
  Well_96 varchar(10),
  Well_96_Padded varchar(10),
  Well_384 varchar(10),
  Well_384_Padded varchar(10),
  AddressByRow_96 integer,
  AddressByColumn_96 integer,
  AddressByColumn_384 integer,
  AddressByRow_384 integer,

  CONSTRAINT PK_well_layout PRIMARY KEY (rowid)
);

INSERT INTO laboratory.well_layout
(Plate,Well_96,Well_96_Padded,Well_384,Well_384_Padded,AddressByRow_96,AddressByColumn_96,AddressByColumn_384,AddressByRow_384)
VALUES
(1, 'A1', 'A01', 'A1', 'A01', 1, 1, 1, 1),
(1, 'A2', 'A02', 'A2', 'A02', 2, 9, 17, 2),
(1, 'A3', 'A03', 'A3', 'A03', 3, 17, 33, 3),
(1, 'A4', 'A04', 'A4', 'A04', 4, 25, 49, 4),
(1, 'A5', 'A05', 'A5', 'A05', 5, 33, 65, 5),
(1, 'A6', 'A06', 'A6', 'A06', 6, 41, 81, 6),
(1, 'A7', 'A07', 'A7', 'A07', 7, 49, 97, 7),
(1, 'A8', 'A08', 'A8', 'A08', 8, 57, 113, 8),
(1, 'A9', 'A09', 'A9', 'A09', 9, 65, 129, 9),
(1, 'A10', 'A10', 'A10', 'A10', 10, 73, 145, 10),
(1, 'A11', 'A11', 'A11', 'A11', 11, 81, 161, 11),
(1, 'A12', 'A12', 'A12', 'A12', 12, 89, 177, 12),
(1, 'B1', 'B01', 'A13', 'A13', 13, 2, 193, 13),
(1, 'B2', 'B02', 'A14', 'A14', 14, 10, 209, 14),
(1, 'B3', 'B03', 'A15', 'A15', 15, 18, 225, 15),
(1, 'B4', 'B04', 'A16', 'A16', 16, 26, 241, 16),
(1, 'B5', 'B05', 'A17', 'A17', 17, 34, 257, 17),
(1, 'B6', 'B06', 'A18', 'A18', 18, 42, 273, 18),
(1, 'B7', 'B07', 'A19', 'A19', 19, 50, 289, 19),
(1, 'B8', 'B08', 'A20', 'A20', 20, 58, 305, 20),
(1, 'B9', 'B09', 'A21', 'A21', 21, 66, 321, 21),
(1, 'B10', 'B10', 'A22', 'A22', 22, 74, 337, 22),
(1, 'B11', 'B11', 'A23', 'A23', 23, 82, 353, 23),
(1, 'B12', 'B12', 'A24', 'A24', 24, 90, 369, 24),
(1, 'C1', 'C01', 'B1', 'B01', 25, 3, 2, 25),
(1, 'C2', 'C02', 'B2', 'B02', 26, 11, 18, 26),
(1, 'C3', 'C03', 'B3', 'B03', 27, 19, 34, 27),
(1, 'C4', 'C04', 'B4', 'B04', 28, 27, 50, 28),
(1, 'C5', 'C05', 'B5', 'B05', 29, 35, 66, 29),
(1, 'C6', 'C06', 'B6', 'B06', 30, 43, 82, 30),
(1, 'C7', 'C07', 'B7', 'B07', 31, 51, 98, 31),
(1, 'C8', 'C08', 'B8', 'B08', 32, 59, 114, 32),
(1, 'C9', 'C09', 'B9', 'B09', 33, 67, 130, 33),
(1, 'C10', 'C10', 'B10', 'B10', 34, 75, 146, 34),
(1, 'C11', 'C11', 'B11', 'B11', 35, 83, 162, 35),
(1, 'C12', 'C12', 'B12', 'B12', 36, 91, 178, 36),
(1, 'D1', 'D01', 'B13', 'B13', 37, 4, 194, 37),
(1, 'D2', 'D02', 'B14', 'B14', 38, 12, 210, 38),
(1, 'D3', 'D03', 'B15', 'B15', 39, 20, 226, 39),
(1, 'D4', 'D04', 'B16', 'B16', 40, 28, 242, 40),
(1, 'D5', 'D05', 'B17', 'B17', 41, 36, 258, 41),
(1, 'D6', 'D06', 'B18', 'B18', 42, 44, 274, 42),
(1, 'D7', 'D07', 'B19', 'B19', 43, 52, 290, 43),
(1, 'D8', 'D08', 'B20', 'B20', 44, 60, 306, 44),
(1, 'D9', 'D09', 'B21', 'B21', 45, 68, 322, 45),
(1, 'D10', 'D10', 'B22', 'B22', 46, 76, 338, 46),
(1, 'D11', 'D11', 'B23', 'B23', 47, 84, 354, 47),
(1, 'D12', 'D12', 'B24', 'B24', 48, 92, 370, 48),
(1, 'E1', 'E01', 'C1', 'C01', 49, 5, 3, 49),
(1, 'E2', 'E02', 'C2', 'C02', 50, 13, 19, 50),
(1, 'E3', 'E03', 'C3', 'C03', 51, 21, 35, 51),
(1, 'E4', 'E04', 'C4', 'C04', 52, 29, 51, 52),
(1, 'E5', 'E05', 'C5', 'C05', 53, 37, 67, 53),
(1, 'E6', 'E06', 'C6', 'C06', 54, 45, 83, 54),
(1, 'E7', 'E07', 'C7', 'C07', 55, 53, 99, 55),
(1, 'E8', 'E08', 'C8', 'C08', 56, 61, 115, 56),
(1, 'E9', 'E09', 'C9', 'C09', 57, 69, 131, 57),
(1, 'E10', 'E10', 'C10', 'C10', 58, 77, 147, 58),
(1, 'E11', 'E11', 'C11', 'C11', 59, 85, 163, 59),
(1, 'E12', 'E12', 'C12', 'C12', 60, 93, 179, 60),
(1, 'F1', 'F01', 'C13', 'C13', 61, 6, 195, 61),
(1, 'F2', 'F02', 'C14', 'C14', 62, 14, 211, 62),
(1, 'F3', 'F03', 'C15', 'C15', 63, 22, 227, 63),
(1, 'F4', 'F04', 'C16', 'C16', 64, 30, 243, 64),
(1, 'F5', 'F05', 'C17', 'C17', 65, 38, 259, 65),
(1, 'F6', 'F06', 'C18', 'C18', 66, 46, 275, 66),
(1, 'F7', 'F07', 'C19', 'C19', 67, 54, 291, 67),
(1, 'F8', 'F08', 'C20', 'C20', 68, 62, 307, 68),
(1, 'F9', 'F09', 'C21', 'C21', 69, 70, 323, 69),
(1, 'F10', 'F10', 'C22', 'C22', 70, 78, 339, 70),
(1, 'F11', 'F11', 'C23', 'C23', 71, 86, 355, 71),
(1, 'F12', 'F12', 'C24', 'C24', 72, 94, 371, 72),
(1, 'G1', 'G01', 'D1', 'D01', 73, 7, 4, 73),
(1, 'G2', 'G02', 'D2', 'D02', 74, 15, 20, 74),
(1, 'G3', 'G03', 'D3', 'D03', 75, 23, 36, 75),
(1, 'G4', 'G04', 'D4', 'D04', 76, 31, 52, 76),
(1, 'G5', 'G05', 'D5', 'D05', 77, 39, 68, 77),
(1, 'G6', 'G06', 'D6', 'D06', 78, 47, 84, 78),
(1, 'G7', 'G07', 'D7', 'D07', 79, 55, 100, 79),
(1, 'G8', 'G08', 'D8', 'D08', 80, 63, 116, 80),
(1, 'G9', 'G09', 'D9', 'D09', 81, 71, 132, 81),
(1, 'G10', 'G10', 'D10', 'D10', 82, 79, 148, 82),
(1, 'G11', 'G11', 'D11', 'D11', 83, 87, 164, 83),
(1, 'G12', 'G12', 'D12', 'D12', 84, 95, 180, 84),
(1, 'H1', 'H01', 'D13', 'D13', 85, 8, 196, 85),
(1, 'H2', 'H02', 'D14', 'D14', 86, 16, 212, 86),
(1, 'H3', 'H03', 'D15', 'D15', 87, 24, 228, 87),
(1, 'H4', 'H04', 'D16', 'D16', 88, 32, 244, 88),
(1, 'H5', 'H05', 'D17', 'D17', 89, 40, 260, 89),
(1, 'H6', 'H06', 'D18', 'D18', 90, 48, 276, 90),
(1, 'H7', 'H07', 'D19', 'D19', 91, 56, 292, 91),
(1, 'H8', 'H08', 'D20', 'D20', 92, 64, 308, 92),
(1, 'H9', 'H09', 'D21', 'D21', 93, 72, 324, 93),
(1, 'H10', 'H10', 'D22', 'D22', 94, 80, 340, 94),
(1, 'H11', 'H11', 'D23', 'D23', 95, 88, 356, 95),
(1, 'H12', 'H12', 'D24', 'D24', 96, 96, 372, 96),
(2, 'A1', 'A01', 'E1', 'E01', 1, 1, 5, 97),
(2, 'A2', 'A02', 'E2', 'E02', 2, 9, 21, 98),
(2, 'A3', 'A03', 'E3', 'E03', 3, 17, 37, 99),
(2, 'A4', 'A04', 'E4', 'E04', 4, 25, 53, 100),
(2, 'A5', 'A05', 'E5', 'E05', 5, 33, 69, 101),
(2, 'A6', 'A06', 'E6', 'E06', 6, 41, 85, 102),
(2, 'A7', 'A07', 'E7', 'E07', 7, 49, 101, 103),
(2, 'A8', 'A08', 'E8', 'E08', 8, 57, 117, 104),
(2, 'A9', 'A09', 'E9', 'E09', 9, 65, 133, 105),
(2, 'A10', 'A10', 'E10', 'E10', 10, 73, 149, 106),
(2, 'A11', 'A11', 'E11', 'E11', 11, 81, 165, 107),
(2, 'A12', 'A12', 'E12', 'E12', 12, 89, 181, 108),
(2, 'B1', 'B01', 'E13', 'E13', 13, 2, 197, 109),
(2, 'B2', 'B02', 'E14', 'E14', 14, 10, 213, 110),
(2, 'B3', 'B03', 'E15', 'E15', 15, 18, 229, 111),
(2, 'B4', 'B04', 'E16', 'E16', 16, 26, 245, 112),
(2, 'B5', 'B05', 'E17', 'E17', 17, 34, 261, 113),
(2, 'B6', 'B06', 'E18', 'E18', 18, 42, 277, 114),
(2, 'B7', 'B07', 'E19', 'E19', 19, 50, 293, 115),
(2, 'B8', 'B08', 'E20', 'E20', 20, 58, 309, 116),
(2, 'B9', 'B09', 'E21', 'E21', 21, 66, 325, 117),
(2, 'B10', 'B10', 'E22', 'E22', 22, 74, 341, 118),
(2, 'B11', 'B11', 'E23', 'E23', 23, 82, 357, 119),
(2, 'B12', 'B12', 'E24', 'E24', 24, 90, 373, 120),
(2, 'C1', 'C01', 'F1', 'F01', 25, 3, 6, 121),
(2, 'C2', 'C02', 'F2', 'F02', 26, 11, 22, 122),
(2, 'C3', 'C03', 'F3', 'F03', 27, 19, 38, 123),
(2, 'C4', 'C04', 'F4', 'F04', 28, 27, 54, 124),
(2, 'C5', 'C05', 'F5', 'F05', 29, 35, 70, 125),
(2, 'C6', 'C06', 'F6', 'F06', 30, 43, 86, 126),
(2, 'C7', 'C07', 'F7', 'F07', 31, 51, 102, 127),
(2, 'C8', 'C08', 'F8', 'F08', 32, 59, 118, 128),
(2, 'C9', 'C09', 'F9', 'F09', 33, 67, 134, 129),
(2, 'C10', 'C10', 'F10', 'F10', 34, 75, 150, 130),
(2, 'C11', 'C11', 'F11', 'F11', 35, 83, 166, 131),
(2, 'C12', 'C12', 'F12', 'F12', 36, 91, 182, 132),
(2, 'D1', 'D01', 'F13', 'F13', 37, 4, 198, 133),
(2, 'D2', 'D02', 'F14', 'F14', 38, 12, 214, 134),
(2, 'D3', 'D03', 'F15', 'F15', 39, 20, 230, 135),
(2, 'D4', 'D04', 'F16', 'F16', 40, 28, 246, 136),
(2, 'D5', 'D05', 'F17', 'F17', 41, 36, 262, 137),
(2, 'D6', 'D06', 'F18', 'F18', 42, 44, 278, 138),
(2, 'D7', 'D07', 'F19', 'F19', 43, 52, 294, 139),
(2, 'D8', 'D08', 'F20', 'F20', 44, 60, 310, 140),
(2, 'D9', 'D09', 'F21', 'F21', 45, 68, 326, 141),
(2, 'D10', 'D10', 'F22', 'F22', 46, 76, 342, 142),
(2, 'D11', 'D11', 'F23', 'F23', 47, 84, 358, 143),
(2, 'D12', 'D12', 'F24', 'F24', 48, 92, 374, 144),
(2, 'E1', 'E01', 'G1', 'G01', 49, 5, 7, 145),
(2, 'E2', 'E02', 'G2', 'G02', 50, 13, 23, 146),
(2, 'E3', 'E03', 'G3', 'G03', 51, 21, 39, 147),
(2, 'E4', 'E04', 'G4', 'G04', 52, 29, 55, 148),
(2, 'E5', 'E05', 'G5', 'G05', 53, 37, 71, 149),
(2, 'E6', 'E06', 'G6', 'G06', 54, 45, 87, 150),
(2, 'E7', 'E07', 'G7', 'G07', 55, 53, 103, 151),
(2, 'E8', 'E08', 'G8', 'G08', 56, 61, 119, 152),
(2, 'E9', 'E09', 'G9', 'G09', 57, 69, 135, 153),
(2, 'E10', 'E10', 'G10', 'G10', 58, 77, 151, 154),
(2, 'E11', 'E11', 'G11', 'G11', 59, 85, 167, 155),
(2, 'E12', 'E12', 'G12', 'G12', 60, 93, 183, 156),
(2, 'F1', 'F01', 'G13', 'G13', 61, 6, 199, 157),
(2, 'F2', 'F02', 'G14', 'G14', 62, 14, 215, 158),
(2, 'F3', 'F03', 'G15', 'G15', 63, 22, 231, 159),
(2, 'F4', 'F04', 'G16', 'G16', 64, 30, 247, 160),
(2, 'F5', 'F05', 'G17', 'G17', 65, 38, 263, 161),
(2, 'F6', 'F06', 'G18', 'G18', 66, 46, 279, 162),
(2, 'F7', 'F07', 'G19', 'G19', 67, 54, 295, 163),
(2, 'F8', 'F08', 'G20', 'G20', 68, 62, 311, 164),
(2, 'F9', 'F09', 'G21', 'G21', 69, 70, 327, 165),
(2, 'F10', 'F10', 'G22', 'G22', 70, 78, 343, 166),
(2, 'F11', 'F11', 'G23', 'G23', 71, 86, 359, 167),
(2, 'F12', 'F12', 'G24', 'G24', 72, 94, 375, 168),
(2, 'G1', 'G01', 'H1', 'H01', 73, 7, 8, 169),
(2, 'G2', 'G02', 'H2', 'H02', 74, 15, 24, 170),
(2, 'G3', 'G03', 'H3', 'H03', 75, 23, 40, 171),
(2, 'G4', 'G04', 'H4', 'H04', 76, 31, 56, 172),
(2, 'G5', 'G05', 'H5', 'H05', 77, 39, 72, 173),
(2, 'G6', 'G06', 'H6', 'H06', 78, 47, 88, 174),
(2, 'G7', 'G07', 'H7', 'H07', 79, 55, 104, 175),
(2, 'G8', 'G08', 'H8', 'H08', 80, 63, 120, 176),
(2, 'G9', 'G09', 'H9', 'H09', 81, 71, 136, 177),
(2, 'G10', 'G10', 'H10', 'H10', 82, 79, 152, 178),
(2, 'G11', 'G11', 'H11', 'H11', 83, 87, 168, 179),
(2, 'G12', 'G12', 'H12', 'H12', 84, 95, 184, 180),
(2, 'H1', 'H01', 'H13', 'H13', 85, 8, 200, 181),
(2, 'H2', 'H02', 'H14', 'H14', 86, 16, 216, 182),
(2, 'H3', 'H03', 'H15', 'H15', 87, 24, 232, 183),
(2, 'H4', 'H04', 'H16', 'H16', 88, 32, 248, 184),
(2, 'H5', 'H05', 'H17', 'H17', 89, 40, 264, 185),
(2, 'H6', 'H06', 'H18', 'H18', 90, 48, 280, 186),
(2, 'H7', 'H07', 'H19', 'H19', 91, 56, 296, 187),
(2, 'H8', 'H08', 'H20', 'H20', 92, 64, 312, 188),
(2, 'H9', 'H09', 'H21', 'H21', 93, 72, 328, 189),
(2, 'H10', 'H10', 'H22', 'H22', 94, 80, 344, 190),
(2, 'H11', 'H11', 'H23', 'H23', 95, 88, 360, 191),
(2, 'H12', 'H12', 'H24', 'H24', 96, 96, 376, 192),
(3, 'A1', 'A01', 'I1', 'I01', 1, 1, 9, 193),
(3, 'A2', 'A02', 'I2', 'I02', 2, 9, 25, 194),
(3, 'A3', 'A03', 'I3', 'I03', 3, 17, 41, 195),
(3, 'A4', 'A04', 'I4', 'I04', 4, 25, 57, 196),
(3, 'A5', 'A05', 'I5', 'I05', 5, 33, 73, 197),
(3, 'A6', 'A06', 'I6', 'I06', 6, 41, 89, 198),
(3, 'A7', 'A07', 'I7', 'I07', 7, 49, 105, 199),
(3, 'A8', 'A08', 'I8', 'I08', 8, 57, 121, 200),
(3, 'A9', 'A09', 'I9', 'I09', 9, 65, 137, 201),
(3, 'A10', 'A10', 'I10', 'I10', 10, 73, 153, 202),
(3, 'A11', 'A11', 'I11', 'I11', 11, 81, 169, 203),
(3, 'A12', 'A12', 'I12', 'I12', 12, 89, 185, 204),
(3, 'B1', 'B01', 'I13', 'I13', 13, 2, 201, 205),
(3, 'B2', 'B02', 'I14', 'I14', 14, 10, 217, 206),
(3, 'B3', 'B03', 'I15', 'I15', 15, 18, 233, 207),
(3, 'B4', 'B04', 'I16', 'I16', 16, 26, 249, 208),
(3, 'B5', 'B05', 'I17', 'I17', 17, 34, 265, 209),
(3, 'B6', 'B06', 'I18', 'I18', 18, 42, 281, 210),
(3, 'B7', 'B07', 'I19', 'I19', 19, 50, 297, 211),
(3, 'B8', 'B08', 'I20', 'I20', 20, 58, 313, 212),
(3, 'B9', 'B09', 'I21', 'I21', 21, 66, 329, 213),
(3, 'B10', 'B10', 'I22', 'I22', 22, 74, 345, 214),
(3, 'B11', 'B11', 'I23', 'I23', 23, 82, 361, 215),
(3, 'B12', 'B12', 'I24', 'I24', 24, 90, 377, 216),
(3, 'C1', 'C01', 'J1', 'J01', 25, 3, 10, 217),
(3, 'C2', 'C02', 'J2', 'J02', 26, 11, 26, 218),
(3, 'C3', 'C03', 'J3', 'J03', 27, 19, 42, 219),
(3, 'C4', 'C04', 'J4', 'J04', 28, 27, 58, 220),
(3, 'C5', 'C05', 'J5', 'J05', 29, 35, 74, 221),
(3, 'C6', 'C06', 'J6', 'J06', 30, 43, 90, 222),
(3, 'C7', 'C07', 'J7', 'J07', 31, 51, 106, 223),
(3, 'C8', 'C08', 'J8', 'J08', 32, 59, 122, 224),
(3, 'C9', 'C09', 'J9', 'J09', 33, 67, 138, 225),
(3, 'C10', 'C10', 'J10', 'J10', 34, 75, 154, 226),
(3, 'C11', 'C11', 'J11', 'J11', 35, 83, 170, 227),
(3, 'C12', 'C12', 'J12', 'J12', 36, 91, 186, 228),
(3, 'D1', 'D01', 'J13', 'J13', 37, 4, 202, 229),
(3, 'D2', 'D02', 'J14', 'J14', 38, 12, 218, 230),
(3, 'D3', 'D03', 'J15', 'J15', 39, 20, 234, 231),
(3, 'D4', 'D04', 'J16', 'J16', 40, 28, 250, 232),
(3, 'D5', 'D05', 'J17', 'J17', 41, 36, 266, 233),
(3, 'D6', 'D06', 'J18', 'J18', 42, 44, 282, 234),
(3, 'D7', 'D07', 'J19', 'J19', 43, 52, 298, 235),
(3, 'D8', 'D08', 'J20', 'J20', 44, 60, 314, 236),
(3, 'D9', 'D09', 'J21', 'J21', 45, 68, 330, 237),
(3, 'D10', 'D10', 'J22', 'J22', 46, 76, 346, 238),
(3, 'D11', 'D11', 'J23', 'J23', 47, 84, 362, 239),
(3, 'D12', 'D12', 'J24', 'J24', 48, 92, 378, 240),
(3, 'E1', 'E01', 'K1', 'K01', 49, 5, 11, 241),
(3, 'E2', 'E02', 'K2', 'K02', 50, 13, 27, 242),
(3, 'E3', 'E03', 'K3', 'K03', 51, 21, 43, 243),
(3, 'E4', 'E04', 'K4', 'K04', 52, 29, 59, 244),
(3, 'E5', 'E05', 'K5', 'K05', 53, 37, 75, 245),
(3, 'E6', 'E06', 'K6', 'K06', 54, 45, 91, 246),
(3, 'E7', 'E07', 'K7', 'K07', 55, 53, 107, 247),
(3, 'E8', 'E08', 'K8', 'K08', 56, 61, 123, 248),
(3, 'E9', 'E09', 'K9', 'K09', 57, 69, 139, 249),
(3, 'E10', 'E10', 'K10', 'K10', 58, 77, 155, 250),
(3, 'E11', 'E11', 'K11', 'K11', 59, 85, 171, 251),
(3, 'E12', 'E12', 'K12', 'K12', 60, 93, 187, 252),
(3, 'F1', 'F01', 'K13', 'K13', 61, 6, 203, 253),
(3, 'F2', 'F02', 'K14', 'K14', 62, 14, 219, 254),
(3, 'F3', 'F03', 'K15', 'K15', 63, 22, 235, 255),
(3, 'F4', 'F04', 'K16', 'K16', 64, 30, 251, 256),
(3, 'F5', 'F05', 'K17', 'K17', 65, 38, 267, 257),
(3, 'F6', 'F06', 'K18', 'K18', 66, 46, 283, 258),
(3, 'F7', 'F07', 'K19', 'K19', 67, 54, 299, 259),
(3, 'F8', 'F08', 'K20', 'K20', 68, 62, 315, 260),
(3, 'F9', 'F09', 'K21', 'K21', 69, 70, 331, 261),
(3, 'F10', 'F10', 'K22', 'K22', 70, 78, 347, 262),
(3, 'F11', 'F11', 'K23', 'K23', 71, 86, 363, 263),
(3, 'F12', 'F12', 'K24', 'K24', 72, 94, 379, 264),
(3, 'G1', 'G01', 'L1', 'L01', 73, 7, 12, 265),
(3, 'G2', 'G02', 'L2', 'L02', 74, 15, 28, 266),
(3, 'G3', 'G03', 'L3', 'L03', 75, 23, 44, 267),
(3, 'G4', 'G04', 'L4', 'L04', 76, 31, 60, 268),
(3, 'G5', 'G05', 'L5', 'L05', 77, 39, 76, 269),
(3, 'G6', 'G06', 'L6', 'L06', 78, 47, 92, 270),
(3, 'G7', 'G07', 'L7', 'L07', 79, 55, 108, 271),
(3, 'G8', 'G08', 'L8', 'L08', 80, 63, 124, 272),
(3, 'G9', 'G09', 'L9', 'L09', 81, 71, 140, 273),
(3, 'G10', 'G10', 'L10', 'L10', 82, 79, 156, 274),
(3, 'G11', 'G11', 'L11', 'L11', 83, 87, 172, 275),
(3, 'G12', 'G12', 'L12', 'L12', 84, 95, 188, 276),
(3, 'H1', 'H01', 'L13', 'L13', 85, 8, 204, 277),
(3, 'H2', 'H02', 'L14', 'L14', 86, 16, 220, 278),
(3, 'H3', 'H03', 'L15', 'L15', 87, 24, 236, 279),
(3, 'H4', 'H04', 'L16', 'L16', 88, 32, 252, 280),
(3, 'H5', 'H05', 'L17', 'L17', 89, 40, 268, 281),
(3, 'H6', 'H06', 'L18', 'L18', 90, 48, 284, 282),
(3, 'H7', 'H07', 'L19', 'L19', 91, 56, 300, 283),
(3, 'H8', 'H08', 'L20', 'L20', 92, 64, 316, 284),
(3, 'H9', 'H09', 'L21', 'L21', 93, 72, 332, 285),
(3, 'H10', 'H10', 'L22', 'L22', 94, 80, 348, 286),
(3, 'H11', 'H11', 'L23', 'L23', 95, 88, 364, 287),
(3, 'H12', 'H12', 'L24', 'L24', 96, 96, 380, 288),
(4, 'A1', 'A01', 'M1', 'M01', 1, 1, 13, 289),
(4, 'A2', 'A02', 'M2', 'M02', 2, 9, 29, 290),
(4, 'A3', 'A03', 'M3', 'M03', 3, 17, 45, 291),
(4, 'A4', 'A04', 'M4', 'M04', 4, 25, 61, 292),
(4, 'A5', 'A05', 'M5', 'M05', 5, 33, 77, 293),
(4, 'A6', 'A06', 'M6', 'M06', 6, 41, 93, 294),
(4, 'A7', 'A07', 'M7', 'M07', 7, 49, 109, 295),
(4, 'A8', 'A08', 'M8', 'M08', 8, 57, 125, 296),
(4, 'A9', 'A09', 'M9', 'M09', 9, 65, 141, 297),
(4, 'A10', 'A10', 'M10', 'M10', 10, 73, 157, 298),
(4, 'A11', 'A11', 'M11', 'M11', 11, 81, 173, 299),
(4, 'A12', 'A12', 'M12', 'M12', 12, 89, 189, 300),
(4, 'B1', 'B01', 'M13', 'M13', 13, 2, 205, 301),
(4, 'B2', 'B02', 'M14', 'M14', 14, 10, 221, 302),
(4, 'B3', 'B03', 'M15', 'M15', 15, 18, 237, 303),
(4, 'B4', 'B04', 'M16', 'M16', 16, 26, 253, 304),
(4, 'B5', 'B05', 'M17', 'M17', 17, 34, 269, 305),
(4, 'B6', 'B06', 'M18', 'M18', 18, 42, 285, 306),
(4, 'B7', 'B07', 'M19', 'M19', 19, 50, 301, 307),
(4, 'B8', 'B08', 'M20', 'M20', 20, 58, 317, 308),
(4, 'B9', 'B09', 'M21', 'M21', 21, 66, 333, 309),
(4, 'B10', 'B10', 'M22', 'M22', 22, 74, 349, 310),
(4, 'B11', 'B11', 'M23', 'M23', 23, 82, 365, 311),
(4, 'B12', 'B12', 'M24', 'M24', 24, 90, 381, 312),
(4, 'C1', 'C01', 'N1', 'N01', 25, 3, 14, 313),
(4, 'C2', 'C02', 'N2', 'N02', 26, 11, 30, 314),
(4, 'C3', 'C03', 'N3', 'N03', 27, 19, 46, 315),
(4, 'C4', 'C04', 'N4', 'N04', 28, 27, 62, 316),
(4, 'C5', 'C05', 'N5', 'N05', 29, 35, 78, 317),
(4, 'C6', 'C06', 'N6', 'N06', 30, 43, 94, 318),
(4, 'C7', 'C07', 'N7', 'N07', 31, 51, 110, 319),
(4, 'C8', 'C08', 'N8', 'N08', 32, 59, 126, 320),
(4, 'C9', 'C09', 'N9', 'N09', 33, 67, 142, 321),
(4, 'C10', 'C10', 'N10', 'N10', 34, 75, 158, 322),
(4, 'C11', 'C11', 'N11', 'N11', 35, 83, 174, 323),
(4, 'C12', 'C12', 'N12', 'N12', 36, 91, 190, 324),
(4, 'D1', 'D01', 'N13', 'N13', 37, 4, 206, 325),
(4, 'D2', 'D02', 'N14', 'N14', 38, 12, 222, 326),
(4, 'D3', 'D03', 'N15', 'N15', 39, 20, 238, 327),
(4, 'D4', 'D04', 'N16', 'N16', 40, 28, 254, 328),
(4, 'D5', 'D05', 'N17', 'N17', 41, 36, 270, 329),
(4, 'D6', 'D06', 'N18', 'N18', 42, 44, 286, 330),
(4, 'D7', 'D07', 'N19', 'N19', 43, 52, 302, 331),
(4, 'D8', 'D08', 'N20', 'N20', 44, 60, 318, 332),
(4, 'D9', 'D09', 'N21', 'N21', 45, 68, 334, 333),
(4, 'D10', 'D10', 'N22', 'N22', 46, 76, 350, 334),
(4, 'D11', 'D11', 'N23', 'N23', 47, 84, 366, 335),
(4, 'D12', 'D12', 'N24', 'N24', 48, 92, 382, 336),
(4, 'E1', 'E01', 'O1', 'O01', 49, 5, 15, 337),
(4, 'E2', 'E02', 'O2', 'O02', 50, 13, 31, 338),
(4, 'E3', 'E03', 'O3', 'O03', 51, 21, 47, 339),
(4, 'E4', 'E04', 'O4', 'O04', 52, 29, 63, 340),
(4, 'E5', 'E05', 'O5', 'O05', 53, 37, 79, 341),
(4, 'E6', 'E06', 'O6', 'O06', 54, 45, 95, 342),
(4, 'E7', 'E07', 'O7', 'O07', 55, 53, 111, 343),
(4, 'E8', 'E08', 'O8', 'O08', 56, 61, 127, 344),
(4, 'E9', 'E09', 'O9', 'O09', 57, 69, 143, 345),
(4, 'E10', 'E10', 'O10', 'O10', 58, 77, 159, 346),
(4, 'E11', 'E11', 'O11', 'O11', 59, 85, 175, 347),
(4, 'E12', 'E12', 'O12', 'O12', 60, 93, 191, 348),
(4, 'F1', 'F01', 'O13', 'O13', 61, 6, 207, 349),
(4, 'F2', 'F02', 'O14', 'O14', 62, 14, 223, 350),
(4, 'F3', 'F03', 'O15', 'O15', 63, 22, 239, 351),
(4, 'F4', 'F04', 'O16', 'O16', 64, 30, 255, 352),
(4, 'F5', 'F05', 'O17', 'O17', 65, 38, 271, 353),
(4, 'F6', 'F06', 'O18', 'O18', 66, 46, 287, 354),
(4, 'F7', 'F07', 'O19', 'O19', 67, 54, 303, 355),
(4, 'F8', 'F08', 'O20', 'O20', 68, 62, 319, 356),
(4, 'F9', 'F09', 'O21', 'O21', 69, 70, 335, 357),
(4, 'F10', 'F10', 'O22', 'O22', 70, 78, 351, 358),
(4, 'F11', 'F11', 'O23', 'O23', 71, 86, 367, 359),
(4, 'F12', 'F12', 'O24', 'O24', 72, 94, 383, 360),
(4, 'G1', 'G01', 'P1', 'P01', 73, 7, 16, 361),
(4, 'G2', 'G02', 'P2', 'P02', 74, 15, 32, 362),
(4, 'G3', 'G03', 'P3', 'P03', 75, 23, 48, 363),
(4, 'G4', 'G04', 'P4', 'P04', 76, 31, 64, 364),
(4, 'G5', 'G05', 'P5', 'P05', 77, 39, 80, 365),
(4, 'G6', 'G06', 'P6', 'P06', 78, 47, 96, 366),
(4, 'G7', 'G07', 'P7', 'P07', 79, 55, 112, 367),
(4, 'G8', 'G08', 'P8', 'P08', 80, 63, 128, 368),
(4, 'G9', 'G09', 'P9', 'P09', 81, 71, 144, 369),
(4, 'G10', 'G10', 'P10', 'P10', 82, 79, 160, 370),
(4, 'G11', 'G11', 'P11', 'P11', 83, 87, 176, 371),
(4, 'G12', 'G12', 'P12', 'P12', 84, 95, 192, 372),
(4, 'H1', 'H01', 'P13', 'P13', 85, 8, 208, 373),
(4, 'H2', 'H02', 'P14', 'P14', 86, 16, 224, 374),
(4, 'H3', 'H03', 'P15', 'P15', 87, 24, 240, 375),
(4, 'H4', 'H04', 'P16', 'P16', 88, 32, 256, 376),
(4, 'H5', 'H05', 'P17', 'P17', 89, 40, 272, 377),
(4, 'H6', 'H06', 'P18', 'P18', 90, 48, 288, 378),
(4, 'H7', 'H07', 'P19', 'P19', 91, 56, 304, 379),
(4, 'H8', 'H08', 'P20', 'P20', 92, 64, 320, 380),
(4, 'H9', 'H09', 'P21', 'P21', 93, 72, 336, 381),
(4, 'H10', 'H10', 'P22', 'P22', 94, 80, 352, 382),
(4, 'H11', 'H11', 'P23', 'P23', 95, 88, 368, 383),
(4, 'H12', 'H12', 'P24', 'P24', 96, 96, 384, 384)
;


-- ----------------------------
-- Table structure for laboratory.genders
-- ----------------------------
DROP TABLE IF EXISTS laboratory.genders;
CREATE TABLE laboratory.genders (
code varchar(50) not null,
meaning varchar(100) DEFAULT NULL,
numericvalue integer DEFAULT NULL,

CONSTRAINT PK_genders PRIMARY KEY (code)
)
WITH (OIDS=FALSE)

;

INSERT INTO laboratory.genders
(code, meaning, numericvalue)
VALUES
('m', 'Male', 1),
('f', 'Female', 2),
('u', 'Unknown', 0)
;

drop table if exists laboratory.samples;

insert into laboratory.sample_type (type) values ('gDNA');
insert into laboratory.sample_source (source) values ('gDNA');
insert into laboratory.sample_source (source) values ('DNA');

/* laboratory-11.16-11.17.sql */

alter table laboratory.reports
  add column subjectFieldName varchar(100);

/* laboratory-11.17-11.18.sql */

alter table laboratory.inventory
  add column parentSample integer
;

/* laboratory-11.18-11.19.sql */

DROP TABLE IF EXISTS laboratory.datatypes;
CREATE TABLE laboratory.datatypes
(
  rowid serial NOT NULL,
  name varchar(255),
  isImportable boolean,
  importAsWorkbook boolean,
  isBrowsable boolean,
  isSearchable boolean,
  title varchar(255),
  containerpath varchar(255),
  schemaname varchar(255),
  queryname varchar(255),
  viewname varchar(255),
  reportname varchar(255),
  jsonconfig varchar(4000),
  description varchar(4000),
  sort_order integer,

  container entityid NOT NULL,
  createdby userid NOT NULL,
  created timestamp,
  modifiedby userid,
  modified timestamp,
  CONSTRAINT pk_datatypes PRIMARY KEY (rowid )
);


--rename inventory
alter table laboratory.inventory rename to samples;

alter table laboratory.samples
  add column labwareIdentifier varchar(200)
;

DROP TABLE IF EXISTS laboratory.reports;
CREATE TABLE laboratory.reports
(
  rowid serial NOT NULL,

  category varchar(255),
  reporttype varchar(255),
  reporttitle varchar(255),
  description varchar(4000),
  sort_order integer,

  containerpath varchar(255),
  schemaname varchar(255),
  queryname varchar(255),
  viewname varchar(255),
  reportname varchar(255),
  jsfunctionname varchar(255),
  jsonconfig varchar(4000),
  subjectfieldname varchar(100),
  
  container entityid NOT NULL,
  createdby userid NOT NULL,
  created timestamp NOT NULL,
  modifiedby userid NOT NULL,
  modified timestamp NOT NULL,

  CONSTRAINT pk_reports PRIMARY KEY (rowid)
);


alter table laboratory.site_module_properties
  drop column container
  ;

/* laboratory-11.19-11.20.sql */

alter table laboratory.samples
  add column cane2 varchar(100),
  add column box2 varchar(100),
  add column box_row2 varchar(100),
  add column box_column2 varchar(100)
;

update laboratory.samples set cane2 = cane;
update laboratory.samples set box2 = box;
update laboratory.samples set box_row2 = box_row;
update laboratory.samples set box_column2 = box_column;

alter table laboratory.samples
  drop column cane,
  drop column box,
  drop column box_row,
  drop column box_column
;

alter table laboratory.samples rename column cane2 to cane;
alter table laboratory.samples rename column box2 to box;
alter table laboratory.samples rename column box_row2 to box_row;
alter table laboratory.samples rename column box_column2 to box_column;