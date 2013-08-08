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

/* SequenceAnalysis-11.31-11.32.sql */

update sequenceanalysis.ref_aa_features set category = 'Protein Domain' where "name" in (
'p17 Matrix',
'p24 Capsid',
'p2',
'p7 Nucleocapsid',
'p1',
'p6',
'Gag-Pol Fusion TF protein',
'Protease',
'p66 RT',
'RNAse H',
'Integrase',
'gp120',
'gp41',
'Signal',
'C1',
'V1',
'V2',
'C2',
'V3',
'C3',
'V4',
'C4',
'V5',
'C5',
'gp120',
'gp41'
);

--add epitopes
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('KL9', 'KRQQELLRL', '4', '16', '581', '589', 'Mamu-B*00801', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('KF9', 'KRGVFVLGF', '4', '16', '532', '540', 'Mamu-B*00801', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('LF9', 'LRQGYRPVF', '4', '16', '725', '733', 'Mamu-B*00801', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('CL9', 'CAPPGYALL', '4', '16', '236', '244', 'Mamu-A1*00101', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('YL9', 'YRRWIQLGL', '4', '11', '264', '272', 'Mamu-B*00801', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('YL9', 'YRRWIQLGL', '4', '11', '264', '272', 'Mamu-B*08001', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('YL9', 'YRRWIQLGL', '4', '11', '264', '272', 'Mamu-B*00801', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('Integrase LV9', 'LLWKGEGAV', '4', '12', '967', '975', 'HLA-A*02010101', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('IF9', 'IRILQRALF', '4', '15', '62', '70', 'Mamu-B*00801', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('GL10', 'GPPPPPPPGL', '4', '14', '102', '111', 'Mamu-A1*00101', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('Env KV9', 'KLTSCNTSV', '5', '42', '192', '200', 'HLA-A*02010101', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('PY9', 'PIDNDTTSY', '5', '42', '183', '191', 'HLA-A*01', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('RY9', 'RRGWEVLKY', '5', '42', '787', '795', 'HLA-A*01', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('VV10', 'VPTDPNPPEV', '5', '42', '75', '84', 'HLA-A*02', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('KL9', 'KLTPLCVTL', '5', '42', '121', '129', 'HLA-A*02', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('KV9', 'KLTSCNTSV', '5', '42', '192', '200', 'HLA-A*02', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('RT10', 'RGPGRAFYTT', '5', '42', '311', '320', 'HLA-A*02', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('SV10', 'SLLNATAIAV', '5', '42', '813', '822', 'HLA-A*02', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('KV10', 'KLWVTVYYGV', '5', '42', '33', '42', 'HLA-A*02', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('LV9', 'LWVTVYYGV', '5', '42', '34', '42', 'HLA-A*02', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('QL9', 'QMHEDIISL', '5', '42', '103', '111', 'HLA-A*02', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('IL9', 'IISLWDQSL', '5', '42', '108', '116', 'HLA-A*02', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('P18', 'RIQRGPGRAFVTIGK', '5', '42', '308', '322', 'HLA-A*02', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('TL9', 'TLSQIVTKL', '5', '42', '341', '349', 'HLA-A*02', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('WI9', 'WLWYIKIFI', '5', '42', '678', '686', 'HLA-A*02', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('FV9', 'FIMIVGGLV', '5', '42', '685', '693', 'HLA-A*02', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('RV9', 'RLRDLLLIV', '5', '42', '770', '778', 'HLA-A*02', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('LL9', 'LLQYWSQEL', '5', '42', '799', '807', 'HLA-A*02', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('LV9', 'LLNATAIAV', '5', '42', '814', '822', 'HLA-A*02', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('RA9', 'RVIEVLQRA', '5', '42', '828', '836', 'HLA-A*02', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('RL17', 'RLVSGFLALAWDDLRSL', '5', '42', '747', '763', 'HLA-A*02', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('RA9', 'RIRQGLERA', '5', '42', '846', '854', 'HLA-A*02', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('TW9', 'TVYYGVPVW', '5', '42', '37', '45', 'HLA-A*03', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('VK11', 'VTVYYGVPVWK', '5', '42', '36', '46', 'HLA-A*03', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('TK10', 'TVYYGVPVWK', '5', '42', '37', '46', 'HLA-A*03', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('TK10', 'TTLFCASDAK', '5', '42', '50', '59', 'HLA-A*03', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('TK9', 'TLFCASDAK', '5', '42', '51', '59', 'HLA-A*03', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('RR11', 'RLRDLLLIVTR', '5', '42', '770', '780', 'HLA-A*03', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('SR14(gp41)', 'SYHRLRDLLLIVTR', '5', '42', '767', '780', 'HLA-A*31', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('RR11', 'RLRDLLLIVTR', '5', '42', '770', '780', 'HLA-A*31', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('RW9', 'RIKQIINMW', '5', '42', '419', '427', 'HLA-A*32', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('EL9(gp41)', 'ERYLKDQQL', '5', '42', '584', '592', 'HLA-A*32', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('VH20', 'VLSIVNRVRQGYSPLSFQTH', '5', '42', '701', '720', 'HLA-A*32', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('TF17', 'TVYYGVPVWKEAKTTLF', '5', '42', '37', '53', 'HLA-A*32', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('TK10', 'TVYYGVPVMK', '5', '42', '37', '46', 'HLA-A*68', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('IL9(gp41)', 'IVTRIVELL', '5', '42', '777', '785', 'HLA-A*68', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('gp41 SV10', 'SLLNATDIAV', '5', '42', '813', '822', 'HLA-A*68', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('VK11', 'VTVYYGVPVWK', '5', '42', '36', '46', 'HLA-A*68', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('EL9', 'ERYLKDQQL', '5', '42', '584', '592', 'HLA-B*14', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('SF9', 'SFNCGGEFF', '5', '42', '375', '383', 'HLA-B*15', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('RL9', 'RAIEAQQHL', '5', '42', '557', '565', 'HLA-B*15', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('VT20', 'VLSIVNQVRRQGYSPLSFQT', '5', '42', '701', '719', 'HLA-B*15', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('TA25', 'TEKLWVTVYYGVPVWKEATTTLFCA', '5', '42', '31', '55', 'HLA-B*18', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('1283', 'TVYYGVPVWK', '5', '42', '37', '46', 'HLA-B*18', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('PF14', 'PSSGGDLEITTHSF', '5', '42', '363', '376', 'HLA-B*18', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('AY9', 'AENLWVTVY', '5', '42', '31', '39', 'HLA-B*18', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('YW9', 'YETEVHNVW', '5', '42', '61', '69', 'HLA-B*18', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('AY11', 'AAENLWVTVYY', '5', '42', '30', '40', 'HLA-B*44', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('AY9(gp120)', 'AENLWVTVY', '5', '42', '31', '39', 'HLA-B*44', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('DL9', 'DPNPQEVVL', '5', '42', '78', '86', 'HLA-B*51', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('LI9', 'LPCRIKQII', '5', '42', '416', '424', 'HLA-B*51', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('RL9', 'RAIEAQQHL', '5', '42', '557', '565', 'HLA-B*51', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('RI9', 'RAYRAILHI', '5', '42', '835', '843', 'HLA-B*51', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('PK20', 'PIPIHYCAPAGFAILKCNNK', '5', '42', '212', '231', 'HLA-B*57', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('RL9', 'RAIEAQQHL', '5', '42', '557', '565', 'HLA-B*57', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('GV15', 'GIWGCSGKLICTTAV', '5', '42', '594', '608', 'HLA-B*57', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('GK9', 'GRRGWEALK', '5', '42', '786', '794', 'HLA-B*57', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('TW9', 'TTVPWNVSW', '5', '42', '606', '614', 'HLA-B*57', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('SL9', 'SLYNTVATL', '5', '38', '77', '85', 'HLA-A*02010101', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('Gag ND32', 'NPPIPVGEIYKRWIILGLNKIVRMYSPTSILD', '5', '38', '253', '284', 'HLA-A*02010101', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('GY9', 'GSEELRSLY', '5', '38', '71', '79', 'HLA-A*01', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('QS10', 'QLQPSLQTGS', '5', '38', '63', '72', 'HLA-A*02', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('SL9', 'SLYNTVATL', '5', '38', '77', '85', 'HLA-A*02', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('TV9', 'TLNAWVKVV', '5', '38', '151', '159', 'HLA-A*02', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('GE11', 'GHQAAMQMLKE', '5', '38', '193', '203', 'HLA-A*02', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('HR17', 'HPVHAGPIAPGQMREPR', '5', '38', '216', '232', 'HLA-A*02', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('SM10', 'STLQEQIGWM', '5', '38', '241', '250', 'HLA-A*02', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('MV9', 'MTNNPPIPV', '5', '38', '250', '258', 'HLA-A*02', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('YL9', 'YVDRFYKTL', '5', '38', '296', '304', 'HLA-A*02', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('VV9', 'VLAEAMSQV', '5', '38', '362', '370', 'HLA-A*02', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('SI9', 'SQVTNSATI', '5', '38', '368', '376', 'HLA-A*02', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('FK10', 'FLGKIWPSHK', '5', '38', '434', '443', 'HLA-A*02', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('ND32', 'NPPIPVGEIYKRWIILGLNKIVRMYSPTSILD', '5', '38', '253', '284', 'HLA-A*02', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('EF10', 'EPFRDYVDRF', '5', '38', '291', '300', 'HLA-A*02', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('E9V', 'EMMTACQGV', '5', '38', '345', '353', 'HLA-A*02', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('AA9', 'ATLEEMMTA', '5', '38', '341', '349', 'HLA-A*02', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('SH20', 'SNFKGNKRMVKCFNCGKEGH', '5', '38', '381', '400', 'HLA-A*02', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('FK10', 'FLGKIWPSHK', '5', '38', '433', '442', 'HLA-A*02', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('KK9', 'KIRLRPGGK', '5', '38', '18', '26', 'HLA-A*03', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('RK9', 'RLRPGGKKK', '5', '38', '20', '28', 'HLA-A*03', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('IK10', 'IAKNCRAPRK', '5', '38', '401', '410', 'HLA-A*03', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('RY10', 'RLRPGGKKKY', '5', '38', '20', '29', 'HLA-A*03', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('RK9', 'RLRPGGKKK', '5', '38', '20', '28', 'HLA-A*31', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('CR9', 'CGKEGHIAR', '5', '38', '395', '403', 'HLA-A*31', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('LK10', 'LARNCRAPRK', '5', '38', '401', '410', 'HLA-A*31', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('A*3101 KR9', 'KIWPSYKGR', '5', '38', '436', '444', 'HLA-A*31', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('RK9', 'RLRPGGKKK', '5', '38', '20', '28', 'HLA-A*68', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('SL9', 'SLYNTVATL', '5', '38', '77', '85', 'HLA-A*68', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('A68-QV9', 'QVSQNYPIV', '5', '38', '127', '135', 'HLA-A*68', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('LV18', 'LKDTINEEAAEWDRLHPV', '5', '38', '201', '218', 'HLA-A*68', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('DA9', 'DRFYKTLRA', '5', '38', '298', '306', 'HLA-B*14', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('DV9', 'DLNTMLNTV', '5', '38', '183', '191', 'HLA-B*14', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('VF9 (p24)', 'VKVIEEKAF', '5', '38', '156', '32', 'HLA-B*15', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('GL9(24)', 'GHQAAMQML', '5', '38', '193', '201', 'HLA-B*15', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('WP15', 'WIILGLNKIVRMYSP', '5', '38', '265', '279', 'HLA-B*15', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('GY9', 'GLNKIVRMY', '5', '38', '269', '277', 'HLA-B*15', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('B15-YL9(p24)', 'YVDRFFKTL', '5', '38', '296', '304', 'HLA-B*15', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('KL9', 'KALGPAATL', '5', '38', '335', '343', 'HLA-B*15', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('VL10', 'VHQAISPRTL', '5', '38', '143', '152', 'HLA-B*15', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('HL9', 'HQAISPRTL', '5', '38', '144', '152', 'HLA-B*15', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('IW9', 'ISPRTLNAW', '5', '38', '147', '155', 'HLA-B*15', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('FK10', 'FRDYVDRFYK', '5', '38', '293', '302', 'HLA-B*18', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('TL9', 'TPQDLNTML', '5', '38', '180', '188', 'HLA-B*39', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('GL9(p24)', 'GHQAAMQML', '5', '38', '193', '201', 'HLA-B*39', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('HA9(p24)', 'HPVHAGPIA', '5', '38', '216', '224', 'HLA-B*39', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('NL13', 'NANPDCKTILRAL', '5', '38', '324', '337', 'HLA-B*39', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('SP15', 'SGGELDRWEKIRLRP', '5', '38', '9', '23', 'HLA-B*44', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('SR15', 'SLYNTVATLYCVHQR', '5', '38', '77', '91', 'HLA-B*44', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('KM15', 'KVVEEKAFSPEVIPM', '5', '38', '157', '171', 'HLA-B*44', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('EV9(p24)', 'EEKAFSPEV', '5', '38', '160', '168', 'HLA-B*44', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('ID15', 'IPMFSALSEGATPQD', '5', '38', '169', '183', 'HLA-B*44', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('SM15', 'SALSEGATPQDLNTM', '5', '38', '173', '187', 'HLA-B*44', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('LL10', 'LSEGATPQDL', '5', '38', '175', '184', 'HLA-B*44', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('AI15', 'AAEWDRVHPVHAGPI', '5', '38', '209', '223', 'HLA-B*44', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('NI15', 'NPPIPVGEIYKRWII', '5', '38', '253', '267', 'HLA-B*44', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('FE15', 'FRDYVDRFYKTLRAE', '5', '38', '293', '307', 'HLA-B*44', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('RL11', 'RDYVDRFYKTL', '5', '38', '294', '304', 'HLA-B*44', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('RE15', 'RAEQASQEVKNWMTE', '5', '38', '305', '319', 'HLA-B*44', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('AW11(p24)', 'AEQASQEVKNW', '5', '38', '306', '316', 'HLA-B*44', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('QY9', 'QEPIDKELY', '5', '38', '476', '484', 'HLA-B*44', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('LY-9', 'LYNTVATLY', '5', '38', '78', '86', 'HLA-B*44', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('ET20', 'EPFRDYVDRFFKTLRAEQAT', '5', '38', '291', '310', 'HLA-B*44', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('VL15', 'VQNANPDCKTILKAL', '5', '38', '323', '337', 'HLA-B*51', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('NI9', 'NANPDSKTI', '5', '38', '325', '333', 'HLA-B*51', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('KK15', 'KIRLRPGGKKKYKLK', '5', '38', '18', '32', 'HLA-B*57', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('LF-11', 'LVWASRELERF', '5', '38', '34', '44', 'HLA-B*57', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('GY9', 'GSEELRSLY', '5', '38', '71', '79', 'HLA-B*57', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('RY11', 'RSLYNTVATLY', '5', '38', '76', '86', 'HLA-B*57', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('KK9', 'KTQQAAADK', '5', '38', '114', '122', 'HLA-B*57', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('GP10', 'GQMVHQAISP', '5', '38', '140', '149', 'HLA-B*57', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('AW10', 'AISPRTLNAW', '5', '38', '146', '155', 'HLA-B*57', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('IW9', 'ISPRTLNAW', '5', '38', '147', '155', 'HLA-B*57', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('NF20', 'NAWVKVVEEKAFSPEVIPMF', '5', '38', '153', '172', 'HLA-B*57', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('GAG-KI8', 'KAFSPEVI', '5', '38', '162', '169', 'HLA-B*57', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('KF11', 'KAFSPEVIPMF', '5', '38', '162', '172', 'HLA-B*57', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('FF9', 'FSPEVIPMF', '5', '38', '164', '172', 'HLA-B*57', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('EV15', 'EGATPQDLNTMLNTV', '5', '38', '177', '191', 'HLA-B*57', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('TW9', 'TINEEAAEW', '5', '38', '204', '212', 'HLA-B*57', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('TW10', 'TSTLQEQIGW', '5', '38', '240', '249', 'HLA-B*57', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('VQ15', 'VDRFYKTLRAEQASQ', '5', '38', '297', '311', 'HLA-B*57', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('YT20', 'YKTLRAEQASQEVKNWMTET', '5', '38', '301', '320', 'HLA-B*57', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('QW9', 'QASQEVKNW', '5', '38', '308', '316', 'HLA-B*57', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('Integrase LV9', 'LLWKGEGAV', '5', '107', '241', '249', 'HLA-A*02010101', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('Integrase AR9', 'AVFIHNFKR', '5', '107', '179', '187', 'HLA-A*02010101', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('RL9', 'RAMASDFNL', '5', '107', '20', '28', 'HLA-A*02', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('LR28', 'LLWKGEGAV', '5', '107', '241', '249', 'HLA-A*02', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('QL9', 'QVRDQAEHL', '5', '107', '164', '172', 'HLA-A*02', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('IK11', 'ILKLAGRWPVK', '5', '107', '101', '111', 'HLA-A*03', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('QK10', 'QMAVFIHNFK', '5', '107', '177', '186', 'HLA-A*03', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('AR9', 'AVFIHNFKR', '5', '107', '179', '187', 'HLA-A*03', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('AK10', 'AVFIHNFKRK', '5', '107', '179', '188', 'HLA-A*03', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('1264', 'AVFIHNFKRK', '5', '107', '179', '188', 'HLA-A*68', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('PR18', 'PAETGQETAYFILKLAGR', '5', '107', '90', '107', 'HLA-A*68', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('EL9', 'ETAYFILKL', '5', '107', '96', '104', 'HLA-A*68', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('TL9(Integrase)', 'THLEGKIIL', '5', '107', '66', '74', 'HLA-B*15', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('IY9(Integrase)', 'IQQEFGIPY', '5', '107', '135', '143', 'HLA-B*15', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('FY10(Integrase)', 'FKRKGGIGGY', '5', '107', '185', '194', 'HLA-B*15', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('B15-RY9(Int)', 'RKAKIIRDY', '5', '107', '263', '271', 'HLA-B*15', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('KY8', 'KQEFGIPY', '5', '107', '136', '143', 'HLA-B*15', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('TY10', 'TKIQNFRVYY', '5', '107', '218', '227', 'HLA-B*15', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('QW11', 'QEEHEKYHSNW', '5', '107', '9', '19', 'HLA-B*44', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('EW10', 'EEHEKYHSNW', '5', '107', '10', '19', 'HLA-B*44', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('AY10', 'AETGQETAYY', '5', '107', '91', '100', 'HLA-B*44', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('LI9', 'LPPVVAKEI', '5', '107', '28', '36', 'HLA-B*51', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('SW9', 'STTVKAACW', '5', '107', '123', '131', 'HLA-B*57', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('SW10', 'STTVKAACWW', '5', '107', '123', '132', 'HLA-B*57', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('KF9', 'KTAVQMAVF', '5', '107', '173', '181', 'HLA-B*57', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('Nef AL9', 'AAVDLSHFL', '5', '43', '83', '91', 'HLA-A*02010101', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('GL9', 'GVGAVSRDL', '5', '43', '29', '37', 'HLA-A*02', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('AL9', 'AAVDLSHFL', '5', '43', '83', '91', 'HLA-A*02', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('LL9', 'LTFGWCFKL', '5', '43', '137', '145', 'HLA-A*02', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('VL10', 'VLEWRFDSRL', '5', '43', '180', '189', 'HLA-A*02', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('AL9', 'AFHHVAREL', '5', '43', '190', '198', 'HLA-A*02', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('AA9', 'ALTSSNTAA', '5', '43', '42', '50', 'HLA-A*02', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('ML9', 'MTYKAALDL', '5', '43', '79', '97', 'HLA-A*02', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('P10L', 'PLTFGWCFKL', '5', '43', '136', '145', 'HLA-A*02', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('QK10', 'QVPLRPMTYK', '5', '43', '73', '82', 'HLA-A*03', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('GL9', 'GAFDLSFFL', '5', '43', '83', '91', 'HLA-A*03', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('AK9', 'AVDLSHFLK', '5', '43', '84', '92', 'HLA-A*03', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('DK9', 'DLSHFLKEK', '5', '43', '86', '94', 'HLA-A*03', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('SR10', 'SSLAFRHVAR', '5', '43', '187', '196', 'HLA-A*31', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('A68-AL(Nef)', 'AAVDLSHFL', '5', '43', '83', '91', 'HLA-A*68', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('RA9(Nef)', 'RMRRAEPAA', '5', '43', '19', '27', 'HLA-B*15', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('B15-TY11(Nef)', 'TQGYFPDWQNY', '5', '43', '117', '127', 'HLA-B*15', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('FT9', 'FFPDWKNYT', '5', '43', '120', '128', 'HLA-B*15', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('B15-WF9(Nef)', 'WRFDSRLAF', '5', '43', '183', '191', 'HLA-B*15', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('WF9', 'EVLQWKFDSRLALRH', '5', '43', '179', '193', 'HLA-B*15', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('VM15', 'VLVWKFDSRLAFRHM', '5', '43', '180', '194', 'HLA-B*15', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('LA15', 'LVWKFDSHLAFHHMA', '5', '43', '181', '195', 'HLA-B*15', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('VR15', 'VWRFDSHLAFRHMAR', '5', '43', '182', '196', 'HLA-B*15', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('WE15', 'WRFDSRLAFHHMARE', '5', '43', '183', '197', 'HLA-B*15', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('LL9', 'LTFGWCFKL', '5', '43', '137', '145', 'HLA-B*15', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('RY11', 'RRQDILDLWVY', '5', '43', '105', '115', 'HLA-B*18', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('QY9', 'QDILDLWIY', '5', '43', '107', '115', 'HLA-B*18', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('GP16', 'GVRYPLTFGWCYKLVP', '5', '43', '132', '147', 'HLA-B*18', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('RYP', 'RYPLTFGWCYK', '5', '43', '134', '144', 'HLA-B*18', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('YY9', 'YPLTFGWCY', '5', '43', '135', '143', 'HLA-B*18', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('KL9', 'KEKGGLEGL', '5', '43', '92', '100', 'HLA-B*44', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('KY11', 'KRQEILDLWVY', '5', '43', '105', '115', 'HLA-B*44', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('RY11', 'RPQVPLRPMTY', '5', '43', '71', '81', 'HLA-B*51', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('PY10', 'PQVPLRPMTY', '5', '43', '72', '81', 'HLA-B*51', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('FT9', 'FFPDWKNYT', '5', '43', '120', '128', 'HLA-B*51', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('WY20', 'WKFDSRLAFHHMARELHPEY', '5', '43', '183', '202', 'HLA-B*51', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('DV9', 'DSRLAFHHV', '5', '43', '186', '194', 'HLA-B*51', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('AK9', 'AFHHVAREK', '5', '43', '190', '198', 'HLA-B*51', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('KF9', 'KAAFDLSFF', '5', '43', '82', '90', 'HLA-B*57', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('HW9', 'HTQGYFPDW', '5', '43', '116', '124', 'HLA-B*57', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('HQ10', 'HTQGYFPDWQ', '5', '43', '116', '125', 'HLA-B*57', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('YY8', 'YFPDWQNY', '5', '43', '120', '127', 'HLA-B*57', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('YY9', 'YTPGPGIRY', '5', '43', '127', '135', 'HLA-B*57', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('GF14', 'GPGVRYPLTFGWCY', '5', '43', '130', '143', 'HLA-B*57', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('VV16', 'VRYPLTFGWCYKLVPV', '5', '43', '133', '148', 'HLA-B*57', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('LL9', 'LTFGWCFKL', '5', '43', '137', '145', 'HLA-B*57', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('AL9', 'AAFDLSFFL', '5', '43', '83', '91', 'HLA-B*57', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('YT9', 'YFPDWQNYT', '5', '43', '120', '128', 'HLA-B*57', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('SL9', 'SPIETVPVKL', '5', '94', '158', '167', 'HLA-A*02010101', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('Integrase LV9', 'LLWKGEGAV', '5', '94', '956', '964', 'HLA-A*02010101', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('Integrase AR9', 'AVFIHNFKR', '5', '94', '894', '902', 'HLA-A*02010101', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('PW9', 'PTRRELQVW', '5', '94', '26', '34', 'HLA-B*57', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('FW9', 'FSFPQITLW', '5', '94', '54', '62', 'HLA-B*57', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('SL9', 'SPIETVPVKL', '5', '105', '3', '12', 'HLA-A*02010101', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('SL10', 'SPIETVPVKL', '5', '105', '3', '12', 'HLA-A*02', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('AM9', 'ALVEICTEM', '5', '105', '33', '41', 'HLA-A*02', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('VV11', 'VLDVGDAYFSV', '5', '105', '108', '118', 'HLA-A*02', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('VL9', 'VIYQYMDDL', '5', '105', '179', '187', 'HLA-A*02', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('YV9', 'YQYMDDLYV', '5', '105', '181', '189', 'HLA-A*02', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('LK12', 'LLRWGLTTPDKK', '5', '105', '209', '220', 'HLA-A*02', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('IV9', 'ILKEPVHGV', '5', '105', '309', '317', 'HLA-A*02', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('AV9', 'ALQDSGLEV', '5', '105', '485', '493', 'HLA-A*02', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('TI8', 'TAFTIPSI', '5', '105', '128', '135', 'HLA-A*02', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('IV10', 'IYQYMDDLYV', '5', '105', '180', '189', 'HLA-A*02', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('KA9', 'KLVGKLNWA', '5', '105', '259', '267', 'HLA-A*02', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('EV10', 'EILKEPVGHV', '5', '105', '308', '317', 'HLA-A*02', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('PL9', 'PLVKLWYQL', '5', '105', '421', '429', 'HLA-A*02', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('IV9', 'ILKEPVHGV', '5', '105', '309', '317', 'HLA-A*02', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('EL17', 'EKDSWTVNDIQKLVGKL', '5', '105', '248', '264', 'HLA-A*02', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('TK16', 'TDSQYALGIIQAQPDK', '5', '105', '497', '512', 'HLA-A*02', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('AK9', 'AIFQSSMTK', '5', '105', '158', '166', 'HLA-A*03', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('QR9', 'QIYPGIKVR', '5', '105', '269', '277', 'HLA-A*03', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('AK11', 'ALVEICTEMEK', '5', '105', '33', '43', 'HLA-A*03', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('NK9', 'NTPVFAIKK', '5', '105', '57', '65', 'HLA-A*03', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('GK9', 'GIPHPAGLK', '5', '105', '93', '101', 'HLA-A*03', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('MR9', 'MTKILEPFR', '5', '105', '164', '172', 'HLA-A*03', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('RK11', 'RMRGAHTNDVK', '5', '105', '356', '366', 'HLA-A*03', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('Pol-KA9', 'KVYLAWVPA', '5', '105', '530', '538', 'HLA-A*03', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('KK11', 'KVYLAWVPAHK', '5', '105', '530', '540', 'HLA-A*03', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('A32-PW1-(RT)', 'PIQKETWETW', '5', '105', '392', '401', 'HLA-A*32', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('1274', 'NTPVFAIKKK', '5', '105', '57', '66', 'HLA-A*68', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('AK9', 'AIFQSSMTK', '5', '105', '158', '166', 'HLA-A*68', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('1267', 'FTTPDKKHQK', '5', '105', '214', '223', 'HLA-A*68', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('GA10(RT)', 'GAETFYVDGA', '5', '105', '436', '445', 'HLA-A*68', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('PR18', 'PYNTPVFAIKKKDSTKWR', '5', '105', '55', '72', 'HLA-A*68', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('FK18', 'FWEVQLGIPHPAGLKKKK', '5', '105', '87', '104', 'HLA-A*68', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('DV9', 'DVKQLTEVV', '5', '105', '354', '372', 'HLA-A*68', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('IS14', 'IVGAETFYVDGAAS', '5', '105', '434', '447', 'HLA-A*68', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('IL8', 'IRYQYNVL', '5', '105', '142', '149', 'HLA-B*14', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('VI10', 'VTDSQYALGI', '5', '105', '496', '505', 'HLA-B*14', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('B15-LY12(RT)', 'LVGKLNWASQIY', '5', '105', '260', '271', 'HLA-B*15', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('IY10(RT)', 'ILKEPVHGVY', '5', '105', '309', '318', 'HLA-B*15', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('VI10(RT)', 'VTDSQYALGI', '5', '105', '496', '505', 'HLA-B*15', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('IL9', 'IPLTEEAEL', '5', '105', '293', '301', 'HLA-B*15', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('KY9', 'KQNPDIVIY', '5', '105', '173', '181', 'HLA-B*15', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('NY10', 'NETPGIRYQY', '5', '105', '137', '146', 'HLA-B*18', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('NY9', 'NPEIVIYQY', '5', '105', '175', '183', 'HLA-B*18', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('VL18', 'VSLTETTNQKTELQAIQL', '5', '105', '467', '484', 'HLA-B*18', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('NY9', 'NNETPGVRY', '5', '105', '136', '144', 'HLA-B*18', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('TL10', 'TELQAIQLAL', '5', '105', '477', '486', 'HLA-B*18', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('PM16', 'PISPIETVPVKLKPGM', '5', '105', '1', '16', 'HLA-B*39', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('LI11', 'LPQGWKGSPAI', '5', '105', '149', '159', 'HLA-B*39', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('QL17', 'QDSGSEVNIVTDSQYAL', '5', '105', '487', '503', 'HLA-B*39', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('EI15', 'EVNIVTDSQYALGII', '5', '105', '492', '506', 'HLA-B*39', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('AL16', 'ALGIIQAQPDKSESEL', '5', '105', '502', '517', 'HLA-B*39', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('ER9', 'EELRQHLLR', '5', '105', '203', '211', 'HLA-B*44', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('EW10', 'EELRQHLLRW', '5', '105', '203', '212', 'HLA-B*44', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('TW10', 'TWETWWTEYW', '5', '105', '397', '406', 'HLA-B*44', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('SY11', 'SEVNIVTDSQY', '5', '105', '491', '501', 'HLA-B*44', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('EI9', 'EKEGKISKI', '5', '105', '42', '50', 'HLA-B*51', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('TI8', 'TAFTIPSI', '5', '105', '128', '135', 'HLA-B*51', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('NM10', 'NPDIVIYQYM', '5', '105', '175', '184', 'HLA-B*51', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('EF9', 'EPIVGAETF', '5', '105', '432', '440', 'HLA-B*51', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('DL8', 'DAYFSVPL', '5', '105', '113', '120', 'HLA-B*51', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('TI8', 'TAFTIPSI', '5', '105', '128', '135', 'HLA-B*51', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('QI9', 'QGWKGSPAI', '5', '105', '151', '159', 'HLA-B*51', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('IL9', 'IPLTEEAEL', '5', '105', '293', '301', 'HLA-B*51', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('LK11', 'LRWGFCTPDKK', '5', '105', '210', '220', 'HLA-B*57', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('PIV', 'PIVLPEKDSW', '5', '105', '243', '252', 'HLA-B*57', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('IW9', 'IVLPEKDSW', '5', '105', '244', '252', 'HLA-B*57', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('KW10', 'KITTESIVIW', '5', '105', '374', '383', 'HLA-B*57', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('KW9', 'KLPIWKETW', '5', '105', '390', '398', 'HLA-B*57', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('QF10', 'QATWIPEWEF', '5', '105', '407', '416', 'HLA-B*57', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('FF9', 'FSVPLDEDF', '5', '105', '116', '124', 'HLA-B*57', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('QI9', 'QRPLVTIKI', '5', '104', '7', '15', 'HLA-A*01', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('KI10', 'KMIGGIGGFI', '5', '104', '45', '54', 'HLA-A*02', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('LI9', 'LVGPTPVNI', '5', '104', '76', '84', 'HLA-A*02', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('L10V', 'LLDTGADDTV', '5', '104', '23', '32', 'HLA-A*02', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('VI10', 'VLVGPTPVNI', '5', '104', '75', '84', 'HLA-A*02', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('VK10', 'VTIKIGGQLK', '5', '104', '11', '20', 'HLA-A*03', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('IV9(Protease)', 'ITLWQRPLV', '5', '104', '3', '11', 'HLA-A*68', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('DL9', 'DTVLEEMNL', '5', '104', '30', '38', 'HLA-A*68', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('GL9', 'GKKAIGTVL', '5', '104', '68', '76', 'HLA-B*15', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('B*1501 TF9', 'TQIGCTLNF', '5', '104', '91', '99', 'HLA-B*15', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('EW9', 'EEMNLPGRW', '5', '104', '34', '42', 'HLA-B*18', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('IG10', 'IEICGHKAIG', '5', '104', '64', '73', 'HLA-B*18', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('EW9', 'EEMNLPGRW', '5', '104', '34', '42', 'HLA-B*44', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('IG10', 'IEICGHKAIG', '5', '104', '64', '73', 'HLA-B*44', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('QG10', 'QRPLVTVKIG', '5', '104', '7', '16', 'HLA-B*51', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('KV8', 'KAIGTVLV', '5', '104', '70', '77', 'HLA-B*57', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('Rev LL9', 'LQLPPLERL', '5', '40', '73', '81', 'HLA-A*02010101', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('IY9', 'ISERILSTY', '5', '40', '55', '63', 'HLA-A*01', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('LL9', 'LQLPPLERL', '5', '40', '73', '81', 'HLA-A*02', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('IL9', 'ILVESPAVL', '5', '40', '102', '110', 'HLA-A*02', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('TY9', 'TVRLIKFLY', '5', '40', '15', '23', 'HLA-A*03', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('ER10', 'ERILSTYLGR', '5', '40', '57', '66', 'HLA-A*03', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('RR9', 'RILSTYLGR', '5', '40', '58', '66', 'HLA-A*03', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('1277', 'PTVLESGTKE', '5', '40', '107', '116', 'HLA-A*68', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('IL9', 'IHSISERIL', '5', '40', '52', '60', 'HLA-B*15', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('IL17', 'ILSTCLGRPAEPVPLQL', '5', '40', '59', '75', 'HLA-B*15', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('KY10', 'KTGRLIKLLY', '5', '40', '14', '23', 'HLA-B*57', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('LI9', 'LRAVRIIKI', '5', '40', '13', '21', 'HLA-B*57', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('1279', 'TACNNCYCKK', '5', '39', '20', '29', 'HLA-A*68', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('IR11', 'ITKGLGISYGR', '5', '39', '39', '49', 'HLA-A*68', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('B15-FY10(Tat)', 'FQTKGLGISY', '5', '39', '38', '47', 'HLA-B*15', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('VI10', 'VCFTTKGLGI', '5', '39', '36', '45', 'HLA-B*15', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('Tat MY9', 'MTKGLGISY', '5', '39', '39', '47', 'HLA-B*15', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('TY8', 'TKGLGISY', '5', '39', '40', '47', 'HLA-B*15', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('PW9', 'PVDPRLEPW', '5', '39', '3', '11', 'HLA-B*57', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('RK10', 'RIRTWKSLVK', '5', '44', '17', '26', 'HLA-A*03', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('HK9', 'HMYISKKAK', '5', '44', '28', '36', 'HLA-A*03', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('KK11', 'KTKPPLPSVKK', '5', '44', '158', '168', 'HLA-A*03', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('ER10', 'EVHIPLGEAR', '5', '44', '54', '63', 'HLA-A*68', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('LA18', 'LIHMHYFDCFADSAIRKA', '5', '44', '106', '123', 'HLA-A*68', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('WI8', 'WHLGHVSI', '5', '44', '79', '87', 'HLA-B*15', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('RI17', 'RHHYESRHPKVSSEVHI', '5', '44', '41', '57', 'HLA-B*15', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('SF13', 'SQ/KRASGQFY/F', '5', '44', '32', '40', 'HLA-B*15', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('VL18', 'VSIEWRLRRYSTQVDPGL', '5', '44', '85', '102', 'HLA-B*18', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('LY10', 'LADQLIHLHY', '5', '44', '102', '111', 'HLA-B*18', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('II10', 'IPLGDAKLII', '5', '44', '57', '66', 'HLA-B*51', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('B57-IF9', 'ISKKAKGWF', '5', '44', '31', '39', 'HLA-B*57', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('VW9', 'VTKLTEDRW', '5', '44', '166', '174', 'HLA-B*57', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('LW18', 'LQTGERDWHLGHGVSIEW', '5', '44', '72', '89', 'HLA-B*57', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('SL9', 'AIIRILQQL', '5', '45', '59', '67', 'HLA-A*02010101', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('AL9', 'AIIRILQQL', '5', '45', '59', '67', 'HLA-A*02', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('Vpr 62', 'RILQQLLFI', '5', '45', '62', '70', 'HLA-A*02', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('DR11', 'DTWAGVEAIIR', '5', '45', '52', '65', 'HLA-A*68', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('EV10', 'ETYGDTWTGV', '5', '45', '48', '57', 'HLA-A*68', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('EI9', 'EAVRHFPRI', '5', '45', '29', '37', 'HLA-B*51', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('AW9', 'AVRHFPRIW', '5', '45', '30', '38', 'HLA-B*57', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('SL17', 'STMVDMGHLRLLDVNDL', '5', '41', '66', '82', 'HLA-A*68', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('RL9', 'RRIRQGLEL', '3', '28', '858', '866', 'Mamu-B*00801', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('RL9', 'RRIRQGLEL', '3', '28', '858', '866', 'Mamu-B*00801', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('YL9', 'YRRWIQLGL', '3', '20', '263', '271', 'Mamu-B*00801', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('YL9', 'YRRWIQLGL', '3', '20', '263', '271', 'Mamu-B*08001', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('YL9', 'YRRWIQLGL', '3', '20', '263', '271', 'Mamu-B*00801', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('GW9', 'GPRKPIKCW', '3', '20', '386', '394', 'Mafa-A1*0630301', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('RL9a', 'RRSRPSGDL', '3', '29', '8', '16', 'Mamu-B*00801', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('RL9a', 'RRSRPSGDL', '3', '29', '8', '16', 'Mamu-B*00801', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('RL9a', 'RRSRPSGDL', '3', '29', '8', '16', 'Mamu-B*00801', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('RL9a', 'RRSRPSGDL', '3', '29', '8', '16', 'Mamu-B*00801', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('RL9a', 'RRSRPSGDL', '3', '29', '8', '16', 'Mamu-B*00801', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('RL9a', 'RRSRPSGDL', '3', '29', '8', '16', 'Mamu-B*00801', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('RL9b/c', 'RRRLTARGLL', '3', '29', '245', '254', 'Mamu-B*00801', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('RL10', 'ARRHRILDIYL', '3', '29', '136', '146', 'Mamu-B*00801', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('RM9', 'RPKVPLRTM', '3', '29', '103', '111', 'Mafa-A1*0630301', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('QP8', 'QVPKFHLP', '3', '21', '591', '598', 'Mafa-A4*0101', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('EA11', 'EAPQFPHGSSA', '3', '21', '50', '60', 'Mamu-A1*00101', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('Integrase LV9', 'LLWKGEGAV', '3', '21', '1007', '1015', 'HLA-A*02010101', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('Rev LL9', 'LQLPPLERL', '3', '26', '73', '81', 'HLA-A*02010101', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('RL9', 'RRAIRGEQL', '3', '22', '123', '131', 'Mamu-B*00801', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('IF9', 'IRILQRALF', '3', '24', '62', '70', 'Mamu-B*00801', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('GL10', 'GPPPPPPPGL', '3', '23', '102', '111', 'Mamu-A1*00101', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('RY8', 'RTLLSRVY', '1', '8', '788', '795', '', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('TL9', 'TVPWPNASL', '1', '8', '620', '628', '', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('CL8', 'CAPPGYAL', '1', '8', '233', '240', 'Mamu-A1*00101', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('TL9', 'TVPWPNASL', '1', '8', '620', '628', 'Mamu-A1*00101', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('ST10', 'SSPPSYFQQT', '1', '8', '726', '735', 'Mamu-A1*00101', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('RY9', 'RTIISLNKY', '1', '8', '296', '304', 'Mamu-A1*00201', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('KM9', 'KTVLPVTIM', '1', '8', '317', '325', 'Mamu-A1*00201', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('QY9', 'QTIVKHPRY', '1', '8', '359', '367', 'Mamu-A1*00201', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('GF10', 'GTSRNKRGVF', '1', '8', '519', '528', 'Mamu-A1*00201', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('SY9', 'SSWPWQIEY', '1', '8', '760', '768', 'Mamu-A1*00201', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('RY8', 'RTLLSRVY', '1', '8', '788', '795', 'Mamu-A1*00201', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('GI8', 'GDYKLVEI', '1', '8', '495', '502', 'Mamu-A1*01101', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('LF11', 'LRCNDTNYSGF', '1', '8', '241', '251', 'Mamu-B*01701', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('LY10', 'LRTELTYLQY', '1', '8', '816', '825', 'Mamu-B*01701', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('FW9', 'FHEAVQAVW', '1', '8', '830', '838', 'Mamu-B*01701', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('KL9', 'KRQQELLRL', '1', '8', '573', '581', 'Mamu-B*00801', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('KF9', 'KRGVFVLGF', '1', '8', '524', '532', 'Mamu-B*00801', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('LF9', 'LRQGYRPVF', '1', '8', '717', '725', 'Mamu-B*00801', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('RL9', 'RRIRQGLEL', '1', '8', '868', '876', 'Mamu-B*00801', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('RL9', 'RRIRQGLEL', '1', '8', '868', '876', 'Mamu-B*00801', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('RY9', 'RTELTYLQY', '1', '8', '817', '825', 'Mamu-A1*00201', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('CL9', 'CAPPGYALL', '1', '8', '233', '241', 'Mamu-A1*00101', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('CM9', 'CTPYDINQM', '1', '1', '181', '189', 'Mamu-A1*00101', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('SN15', 'SSVDEQIQWMYRQQN', '1', '1', '241', '255', '', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('MI15', 'MLNCVGDHQAAMQII', '1', '1', '189', '203', '', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('GN15', 'GKKKYMLKHVVWAAN', '1', '1', '25', '39', '', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('GY9', 'GSENLKSLY', '1', '1', '71', '79', '', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('LL11', 'LDRFGLAESLL', '1', '1', '41', '51', '', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('DK15', 'DRQAGFLGLGPWGKK', '1', '1', '429', '443', '', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('LW9', 'LSPRTLNAW', '1', '1', '149', '157', 'Mamu-A1*00101', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('QI9', 'QNPIPVGNI', '1', '1', '254', '262', 'Mamu-A1*00101', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('LF8', 'LAPVPIPF', '1', '1', '372', '379', 'Mamu-A1*00101', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('GY9', 'GSENLKSLY', '1', '1', '71', '79', 'Mamu-A1*00201', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('SI9', 'SEGCTPYDI', '1', '1', '178', '186', 'Mamu-A1*01101', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('YL9', 'YRRWIQLGL', '1', '1', '263', '271', 'Mamu-B*08001', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('YL9', 'YRRWIQLGL', '1', '1', '263', '271', 'Mamu-B*00801', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('YL9', 'YRRWIQLGL', '1', '1', '263', '271', 'Mamu-B*00801', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('GW9', 'GPRKPIKCW', '1', '1', '386', '394', 'Mafa-A1*0630301', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('GC11', 'GLDKGLSSLSC', '1', '9', '45', '55', '', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('RM9', 'RPKVPLRTM', '1', '9', '103', '111', 'Mafa-A1*06301', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('LY9', 'LLRARGETY', '1', '9', '20', '28', 'Mamu-A1*00201', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('TM10', 'TMSYKLAIDM', '1', '9', '110', '119', 'Mamu-A1*00201', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('YY9', 'YTSGPGIRY', '1', '9', '159', '167', 'Mamu-A1*00201', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('KL9', 'KTFGWLWKL', '1', '9', '169', '177', 'Mamu-A1*00201', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('YY9', 'YTYEAYVRY', '1', '9', '221', '229', 'Mamu-A1*00201', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('LM9', 'LTARGLLNM', '1', '9', '248', '256', 'Mamu-A1*00201', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('KI9', 'KEKGGLEGI', '1', '9', '124', '132', 'Mamu-A1*01101', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('IW9', 'IRYPKTFGW', '1', '9', '165', '173', 'Mamu-B*01701', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('MW9', 'MHPAQTSQW', '1', '9', '195', '203', 'Mamu-B*01701', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('QW9', 'QTSQWDDPW', '1', '9', '199', '207', 'Mamu-B*01701', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('RL9', 'RRSRPSGDL', '1', '9', '8', '16', 'Mamu-B*00801', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('RL10', 'RRHRILDIYL', '1', '9', '137', '146', 'Mamu-B*00801', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('RL9-2', 'RRLTARGLL', '1', '9', '246', '254', 'Mamu-B*00801', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('RL9b', 'RRLTARGLL', '1', '9', '246', '254', 'Mamu-B*00801', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('RL9c', 'RRRLTARGL', '1', '9', '245', '253', 'Mamu-B*00801', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('RL9b/c', 'RRRLTARGLL', '1', '9', '245', '254', 'Mamu-B*00801', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('RL9a', 'RRSRPSGDL', '1', '9', '8', '16', 'Mamu-B*00801', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('RL9a', 'RRSRPSGDL', '1', '9', '8', '16', 'Mamu-B*00801', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('RL9a', 'RRSRPSGDL', '1', '9', '8', '16', 'Mamu-B*00801', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('RL9a', 'RRSRPSGDL', '1', '9', '8', '16', 'Mamu-B*00801', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('RL9a', 'RRSRPSGDL', '1', '9', '8', '16', 'Mamu-B*00801', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('RL9a', 'RRSRPSGDL', '1', '9', '8', '16', 'Mamu-B*00801', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('RL10', 'ARRHRILDIYL', '1', '9', '136', '146', 'Mamu-B*00801', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('RM9', 'RPKVPLRTM', '1', '9', '103', '111', 'Mafa-A1*0630301', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('LK11', 'LWKGPGELLWK', '1', '2', '1001', '1011', '', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('LV10', 'LGPHYTPKIV', '1', '2', '147', '156', 'Mamu-A1*00101', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('QV9', 'QVPKFHLPV', '1', '2', '592', '600', 'Mamu-A1*00101', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('SV9', 'STPPLVRLV', '1', '2', '625', '633', 'Mamu-A1*00101', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('SV9-2', 'SGPKANIIV', '1', '2', '696', '704', 'Mamu-A1*00101', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('FF9', 'FSIPLDEEF', '1', '2', '324', '332', 'Mamu-A1*00201', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('LY9', 'LSQEQEGCY', '1', '2', '518', '526', 'Mamu-A1*00201', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('YL8', 'YHSNVKEL', '1', '2', '782', '789', 'Mamu-A1*00701', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('AL9', 'AERKQREAL', '1', '2', '92', '100', 'Mamu-A1*01101', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('AI11', 'AEAEYEENKII', '1', '2', '507', '517', 'Mamu-A1*01101', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('MF8', 'MRHVLEPF', '1', '2', '372', '379', 'Mamu-B*01701', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('FW9', 'FQWMGYELW', '1', '2', '435', '443', 'Mamu-B*01701', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('VW10', 'VWEQWWTDYW', '1', '2', '604', '613', 'Mamu-B*01701', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('QP8', 'QVPKFHLP', '1', '2', '592', '599', 'Mafa-A4*0101', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('EA11', 'EAPQFPHGSSA', '1', '2', '51', '61', 'Mamu-A1*00101', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('Integrase LV9', 'LLWKGEGAV', '1', '2', '1008', '1016', 'HLA-A*02010101', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('KL9', 'KRLRLIHLL', '1', '7', '12', '20', 'Mamu-B*00801', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('RL8', 'RRRWQQLL', '1', '7', '44', '51', 'Mamu-B*00801', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('SY11', 'SQLYRPLEACY', '1', '6', '41', '51', '', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('QA8', 'QLYRPLEA', '1', '6', '42', '49', 'Mafa-B*5101', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('SL8', 'STPESANL', '1', '6', '28', '35', 'Mamu-A1*00101', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('LL15', 'LQEGSHLEVQGYWHL', '1', '3', '61', '75', '', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('WI11', 'WTDVTPNYADI', '1', '3', '97', '107', '', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('WY8', 'WTDVTPNY', '1', '3', '97', '104', '', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('VI8', 'VTPDYADI', '1', '3', '100', '107', '', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('TS11', 'TPNYADILLHS', '1', '3', '101', '111', '', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('QA9', 'QVPSLQYLA', '1', '3', '144', '152', 'Mamu-A1*00101', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('IW9', 'ITWYSKNFW', '1', '3', '89', '97', 'Mamu-A1*00201', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('WY8', 'WTDVTPNY', '1', '3', '97', '104', 'Mamu-A1*00201', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('YY10', 'YADILLHSTY', '1', '3', '104', '113', 'Mamu-A1*00201', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('HW9', 'HFKVGWAWW', '1', '3', '44', '52', 'Mamu-B*01701', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('HW8', 'HLEVQGYW', '1', '3', '66', '73', 'Mamu-B*01701', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('CY9', 'CRFPRAHKY', '1', '3', '135', '143', 'Mamu-B*01701', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('RL9', 'RRAIRGEQL', '1', '3', '123', '131', 'Mamu-B*00801', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('RL8', 'RRDNRRGL', '1', '3', '172', '179', 'Mamu-B*00801', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('RL9', 'RRAIRGEQL', '1', '3', '123', '131', 'Mamu-B*00801', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('FG15', 'FRGGCIHSRIGQPGG', '1', '5', '73', '87', '', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('RM9', 'RILQRALFM', '1', '5', '63', '71', 'Mamu-A1*00201', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('RV9', 'REPWDEWVV', '1', '5', '13', '21', 'Mamu-A1*01101', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('IF9', 'IRILQRALF', '1', '5', '62', '70', 'Mamu-B*00801', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('LH15', 'LNRTVEEINREAVNH', '1', '4', '25', '39', '', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('TR11', 'TIGEAFEWLNR', '1', '4', '17', '27', '', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('II11', 'IPPGNSGEETI', '1', '4', '8', '18', 'Mamu-A1*00101', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('GL10', 'GPPPPPPPGL', '1', '4', '102', '111', 'Mamu-A1*00101', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('CL8', 'CAPPGYAL', '13', '82', '233', '240', 'Mamu-A1*00101', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('TL9', 'TVPWPNASL', '13', '82', '620', '628', 'Mamu-A1*00101', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('ST10', 'SSPPSYFQQT', '13', '82', '726', '735', 'Mamu-A1*00101', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('RY9', 'RTIISLNKY', '13', '82', '296', '304', 'Mamu-A1*00201', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('KM9', 'KTVLPVTIM', '13', '82', '317', '325', 'Mamu-A1*00201', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('QY9', 'QTIVKHPRY', '13', '82', '359', '367', 'Mamu-A1*00201', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('GF10', 'GTSRNKRGVF', '13', '82', '519', '528', 'Mamu-A1*00201', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('SY9', 'SSWPWQIEY', '13', '82', '760', '768', 'Mamu-A1*00201', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('RY8', 'RTLLSRVY', '13', '82', '788', '795', 'Mamu-A1*00201', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('GI8', 'GDYKLVEI', '13', '82', '495', '502', 'Mamu-A1*01101', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('LF11', 'LRCNDTNYSGF', '13', '82', '241', '251', 'Mamu-B*01701', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('LY10', 'LRTELTYLQY', '13', '82', '816', '825', 'Mamu-B*01701', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('FW9', 'FHEAVQAVW', '13', '82', '830', '838', 'Mamu-B*01701', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('KL9', 'KRQQELLRL', '13', '82', '573', '581', 'Mamu-B*00801', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('KF9', 'KRGVFVLGF', '13', '82', '524', '532', 'Mamu-B*00801', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('LF9', 'LRQGYRPVF', '13', '82', '717', '725', 'Mamu-B*00801', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('RL9', 'RRIRQGLEL', '13', '82', '868', '876', 'Mamu-B*00801', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('RL9', 'RRIRQGLEL', '13', '82', '868', '876', 'Mamu-B*00801', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('RY9', 'RTELTYLQY', '13', '82', '817', '825', 'Mamu-A1*00201', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('CL9', 'CAPPGYALL', '13', '82', '233', '241', 'Mamu-A1*00101', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('GN15', 'GKKKYMLKHVVWAAN', '13', '75', '25', '39', '', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('SN15', 'SSVDEQIQWMYRQQN', '13', '75', '241', '255', '', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('MI15', 'MLNCVGDHQAAMQII', '13', '75', '189', '203', '', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('CM9', 'CTPYDINQM', '13', '75', '181', '189', 'Mamu-A1*00101', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('LL11', 'LDRFGLAESLL', '13', '75', '41', '51', '', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('DK15', 'DRQAGFLGLGPWGKK', '13', '75', '429', '443', '', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('LW9', 'LSPRTLNAW', '13', '75', '149', '157', 'Mamu-A1*00101', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('QI9', 'QNPIPVGNI', '13', '75', '254', '262', 'Mamu-A1*00101', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('LF8', 'LAPVPIPF', '13', '75', '372', '379', 'Mamu-A1*00101', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('GY9', 'GSENLKSLY', '13', '75', '71', '79', 'Mamu-A1*00201', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('SI9', 'SEGCTPYDI', '13', '75', '178', '186', 'Mamu-A1*01101', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('YL9', 'YRRWIQLGL', '13', '75', '263', '271', 'Mamu-B*00801', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('GW9', 'GPRKPIKCW', '13', '75', '386', '394', 'Mafa-A1*0630301', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('GC11', 'GLDKGLSSLSC', '13', '83', '45', '55', '', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('LY9', 'LLRARGETY', '13', '83', '20', '28', 'Mamu-A1*00201', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('RL9', 'RRSRPSGDL', '13', '83', '8', '16', 'Mamu-B*00801', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('RL9a', 'RRSRPSGDL', '13', '83', '8', '16', 'Mamu-B*00801', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('LK11', 'LWKGPGELLWK', '13', '76', '1001', '1011', '', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('LV10', 'LGPHYTPKIV', '13', '76', '147', '156', 'Mamu-A1*00101', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('QV9', 'QVPKFHLPV', '13', '76', '592', '600', 'Mamu-A1*00101', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('SV9', 'STPPLVRLV', '13', '76', '625', '633', 'Mamu-A1*00101', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('SV9-2', 'SGPKANIIV', '13', '76', '696', '704', 'Mamu-A1*00101', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('FF9', 'FSIPLDEEF', '13', '76', '324', '332', 'Mamu-A1*00201', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('LY9', 'LSQEQEGCY', '13', '76', '518', '526', 'Mamu-A1*00201', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('YL8', 'YHSNVKEL', '13', '76', '782', '789', 'Mamu-A1*00701', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('AL9', 'AERKQREAL', '13', '76', '92', '100', 'Mamu-A1*01101', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('AI11', 'AEAEYEENKII', '13', '76', '507', '517', 'Mamu-A1*01101', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('MF8', 'MRHVLEPF', '13', '76', '372', '379', 'Mamu-B*01701', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('FW9', 'FQWMGYELW', '13', '76', '435', '443', 'Mamu-B*01701', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('VW10', 'VWEQWWTDYW', '13', '76', '604', '613', 'Mamu-B*01701', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('QP8', 'QVPKFHLP', '13', '76', '592', '599', 'Mafa-A4*0101', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('EA11', 'EAPQFPHGSSA', '13', '76', '51', '61', 'Mamu-A1*00101', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('Integrase LV9', 'LLWKGEGAV', '13', '76', '1008', '1016', 'HLA-A*02010101', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('KL9', 'KRLRLIHLL', '13', '81', '12', '20', 'Mamu-B*00801', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('RL8', 'RRRWQQLL', '13', '81', '44', '51', 'Mamu-B*00801', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('SY11', 'SQLYRPLEACY', '13', '80', '41', '51', '', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('QA8', 'QLYRPLEA', '13', '80', '42', '49', 'Mafa-B*5101', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('SL8', 'STPESANL', '13', '80', '28', '35', 'Mamu-A1*00101', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('WI11', 'WTDVTPNYADI', '13', '77', '97', '107', '', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('LL15', 'LQEGSHLEVQGYWHL', '13', '77', '61', '75', '', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('TS11', 'TPNYADILLHS', '13', '77', '101', '111', '', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('VI8', 'VTPDYADI', '13', '77', '100', '107', '', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('QA9', 'QVPSLQYLA', '13', '77', '144', '152', 'Mamu-A1*00101', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('IW9', 'ITWYSKNFW', '13', '77', '89', '97', 'Mamu-A1*00201', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('WY8', 'WTDVTPNY', '13', '77', '97', '104', 'Mamu-A1*00201', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('YY10', 'YADILLHSTY', '13', '77', '104', '113', 'Mamu-A1*00201', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('HW9', 'HFKVGWAWW', '13', '77', '44', '52', 'Mamu-B*01701', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('HW8', 'HLEVQGYW', '13', '77', '66', '73', 'Mamu-B*01701', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('CY9', 'CRFPRAHKY', '13', '77', '135', '143', 'Mamu-B*01701', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('RL9', 'RRAIRGEQL', '13', '77', '123', '131', 'Mamu-B*00801', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('RL8', 'RRDNRRGL', '13', '77', '172', '179', 'Mamu-B*00801', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('RL9', 'RRAIRGEQL', '13', '77', '123', '131', 'Mamu-B*00801', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('FG15', 'FRGGCIHSRIGQPGG', '13', '79', '73', '87', '', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('RM9', 'RILQRALFM', '13', '79', '63', '71', 'Mamu-A1*00201', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('RV9', 'REPWDEWVV', '13', '79', '13', '21', 'Mamu-A1*01101', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('IF9', 'IRILQRALF', '13', '79', '62', '70', 'Mamu-B*00801', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('TR11', 'TIGEAFEWLNR', '13', '78', '17', '27', '', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('LH15', 'LNRTVEEINREAVNH', '13', '78', '25', '39', '', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('II11', 'IPPGNSGEETI', '13', '78', '8', '18', 'Mamu-A1*00101', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('GL10', 'GPPPPPPPGL', '13', '78', '102', '111', 'Mamu-A1*00101', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('RY8', 'RTLLSRAY', '2', '37', '790', '797', '', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('TL9', 'TVPWPNASL', '2', '37', '622', '630', '', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('KL9', 'KRQQELLRL', '2', '37', '575', '583', 'Mamu-B*00801', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('KF9', 'KRGVFVLGF', '2', '37', '526', '534', 'Mamu-B*00801', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('LF9', 'LRQGYRPVF', '2', '37', '719', '727', 'Mamu-B*00801', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('RL9', 'RRIRQGLEL', '2', '37', '870', '878', 'Mamu-B*00801', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('RL9', 'RRIRQGLEL', '2', '37', '870', '878', 'Mamu-B*00801', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('RY9', 'RTELTYLQY', '2', '37', '819', '827', 'Mamu-A1*00201', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('CL9', 'CAPPGYALL', '2', '37', '235', '243', 'Mamu-A1*00101', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('GN15', 'GKKKYMLKHVVWAAN', '2', '10', '25', '39', '', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('LL11', 'LDRFGLAESLL', '2', '10', '41', '51', '', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('GY9', 'GSENLKSLY', '2', '10', '71', '79', '', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('CM9', 'CTPYDINQM', '2', '10', '181', '189', '', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('MI15', 'MLNCVGDHQAAMQII', '2', '10', '189', '203', '', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('SN15', 'SSVDEQIQWMYRQQN', '2', '10', '241', '255', '', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('DK15', 'DRQAGFLGLGPWGKK', '2', '10', '429', '443', '', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('YL9', 'YRRWIQLGL', '2', '10', '263', '271', 'Mamu-B*00801', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('YL9', 'YRRWIQLGL', '2', '10', '263', '271', 'Mamu-B*08001', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('YL9', 'YRRWIQLGL', '2', '10', '263', '271', 'Mamu-B*00801', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('GW9', 'GPRKPIKCW', '2', '10', '386', '394', 'Mafa-A1*0630301', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('GC11', 'GLGKGLSSRSC', '2', '36', '45', '55', '', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('LK11', 'LWKGPGELLWK', '2', '30', '997', '1007', '', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('QP8', 'QVPKFHLP', '2', '30', '588', '595', 'Mafa-A4*0101', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('EA11', 'EAPQFPHGSSA', '2', '30', '51', '61', 'Mamu-A1*00101', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('Integrase LV9', 'LLWKGEGAV', '2', '30', '1004', '1012', 'HLA-A*02010101', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('SL8', 'TTPESANL', '2', '34', '28', '35', '', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('SY11', 'SQLYRPLEACY', '2', '34', '41', '51', '', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('LL15', 'LQEGSHLEVQGYWHL', '2', '31', '61', '75', '', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('WY8', 'WTDVTPDY', '2', '31', '97', '104', '', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('WI11', 'WTDVTPDYADI', '2', '31', '97', '107', '', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('VI8', 'VTPDYADI', '2', '31', '100', '107', '', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('TS11', 'TPDYADILLHS', '2', '31', '101', '111', '', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('RL9', 'RRAIRGEQL', '2', '31', '123', '131', 'Mamu-B*00801', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('FG15', 'FRGGCNHSRIGQPGG', '2', '33', '73', '87', '', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('IF9', 'IRILQRALF', '2', '33', '62', '70', 'Mamu-B*00801', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('TR11', 'TIGEAFEWLNR', '2', '32', '17', '27', '', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('AE11', 'AFEWLNRTVEE', '2', '32', '21', '31', '', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('LH15', 'LNRTVEEINREAVNH', '2', '32', '25', '39', '', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('GL10', 'GPPPPPPPGL', '2', '32', '102', '111', 'Mamu-A1*00101', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('KL9', 'KRQQELLRL', '12', '74', '581', '589', 'Mamu-B*00801', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('KF9', 'KRGVFVLGF', '12', '74', '532', '540', 'Mamu-B*00801', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('LF9', 'LRQGYRPVF', '12', '74', '725', '733', 'Mamu-B*00801', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('CL9', 'CAPPGYALL', '12', '74', '238', '246', 'Mamu-A1*00101', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('YL9', 'YRRWIQLGL', '12', '67', '264', '272', 'Mamu-B*00801', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('Integrase LV9', 'LLWKGEGAV', '12', '68', '970', '978', 'HLA-A*02010101', 'CTL Epitope');
INSERT INTO sequenceanalysis.ref_aa_features (name,aa_sequence,ref_nt_id,ref_aa_id,aa_start,aa_stop,comment,category) VALUES ('GL10', 'GPPPPPPPGL', '12', '70', '102', '111', 'Mamu-A1*00101', 'CTL Epitope');

/* SequenceAnalysis-11.32-11.33.sql */

CREATE TABLE sequenceanalysis.aligners (
    RowId INT IDENTITY(1,1) NOT NULL,

    name varchar(100) not null,
    displayname varchar(100),
    description text,
    jsonconfig text,

    Created DATETIME,
    Modified DATETIME,

    CONSTRAINT PK_aligners PRIMARY KEY (rowId)
);

insert into sequenceanalysis.aligners (name,displayname,description,jsonconfig) values
('bowtie', 'Bowtie', 'Bowtie is a fast aligner often used for short reads.  Disadvantages are that it does not perform gapped alignment.  It will return a single hit for each read.', '[{"name": "bowtie.max_seed_mismatches","fieldLabel": "Max Seed Mismatches","value": 3},{"name": "bowtie.seed_length","fieldLabel": "Seed Length","value": 20}]');
insert into sequenceanalysis.aligners (name,displayname,description,jsonconfig) values
('lastz', 'Lastz', 'Lastz has performed well for both sequence-based genotyping and viral analysis.  ', '[{"name": "lastz.identity","fieldLabel": "Min Pct Identity","renderData": {"helpPopup": "The minimum percent identity required per alignment for that match to be included"},"value": 98},{"name": "lastz.continuity","fieldLabel": "Percent Continuity","renderData": {"helpPopup": "Continuity is the percentage of alignment columns that are not gaps. Alignment blocks outside the given range are discarded."},"value": 90}]');
insert into sequenceanalysis.aligners (name,displayname,description,jsonconfig) values
('bwa', 'BWA', 'BWA is a commonly used aligner, optimized for shorter reads.  It also supports paired-end reads.', '');
insert into sequenceanalysis.aligners (name,displayname,description,jsonconfig) values
('bwa-sw', 'BWA-SW', 'BWA-SW uses a different algorithm than BWA that is better suited for longer reads.  By design it will only return a single hit for each read.  It it currently recommended for viral analysis and other applications that align longer reads, but do not require retaining multiple hits.', '');
insert into sequenceanalysis.aligners (name,displayname,description,jsonconfig) values
('mosaik', 'Mosaik', 'Mosaik is suitable for longer reads and has the option to retain multiple hits per read.  The only downside is that it can be slower.  When this pipeline was first written, this aligner was preferred for sequence-based genotyping and similar applications which require retaining multiple hits.  It supports paired end reads.  The aligner is still good; however, Lastz also seems to perform well for SBT.', '[{"name":"mosaik.output_multiple","fieldLabel":"Retain All Hits","xtype":"checkbox","renderData":{"helpPopup":"If selected, all hits above thresholds will be reported.  If not, only a single hit will be retained."},"checked":true},{"name":"mosaik.max_mismatch_pct","fieldLabel":"Max Mismatch Pct","renderData":{"helpPopup":"The maximum percent of bases allowed to mismatch per alignment.  Note: Ns are counted as mismatches"},"value":0.02,"minValue":0,"maxValue":1},{"name":"mosaik.hash_size","fieldLabel":"Hash Size","renderData":{"helpPopup":"The hash size used in alignment (see Mosaik documentation).  A large value is preferred for sequences expected to be highly similar to the reference"},"minValue":0,"value":32},{"name":"mosaik.max_hash_positions","fieldLabel":"Max Hash Positions","renderData":{"helpPopup":"The maximum number of hash matches that are passed to local alignment."},"minValue":0,"value":200},{"name":"mosaik.align_threshold","fieldLabel":"Alignment Threshold","renderData":{"helpPopup":"The alignment score required for an alignment to continue to local alignment.  Because the latter is slow, a higher value can improve speed."},"value":55}]');

/* SequenceAnalysis-11.33-11.34.sql */

delete from sequenceanalysis.ref_aa_sequences where (select name from sequenceanalysis.ref_nt_sequences n where n.rowid = ref_aa_sequences.ref_nt_id) = 'SIVmac239cy0163';
delete from sequenceanalysis.ref_nt_sequences where name = 'SIVmac239cy0163';
delete from sequenceanalysis.virus_strains where virus_strain = 'SIVmac239cy0163';

alter table sequenceanalysis.sequence_readsets
  add machine_run_id varchar(200);

alter table sequenceanalysis.sequence_readsets
  add fileid2 integer;

alter table sequenceanalysis.sequence_readsets
  add raw_input_file2 integer;

alter table sequenceanalysis.sequence_readsets
  add qc_file2 integer;

alter table sequenceanalysis.sequence_analyses
  add inputfile2 integer;

delete from sequenceanalysis.aligners where name = 'bwa';
insert into sequenceanalysis.aligners (name,displayname,description,jsonconfig) values
('bwa', 'BWA', 'BWA is a commonly used aligner, optimized for shorter reads.  It also supports paired-end reads.', '[{"xtype":"hidden","name":"pairedEnd","value":"true"}]');

delete from sequenceanalysis.aligners where name = 'mosaik';
insert into sequenceanalysis.aligners (name,displayname,description,jsonconfig) values
('mosaik', 'Mosaik', 'Mosaik is suitable for longer reads and has the option to retain multiple hits per read.  The only downside is that it can be slower.  When this pipeline was first written, this aligner was preferred for sequence-based genotyping and similar applications which require retaining multiple hits.  It supports paired end reads.  The aligner is still good; however, Lastz also seems to perform well for SBT.', '[{"xtype":"hidden","name":"pairedEnd","value":"true"},{"name":"mosaik.output_multiple","fieldLabel":"Retain All Hits","xtype":"checkbox","renderData":{"helpPopup":"If selected, all hits above thresholds will be reported.  If not, only a single hit will be retained."},"checked":true},{"name":"mosaik.max_mismatch_pct","fieldLabel":"Max Mismatch Pct","renderData":{"helpPopup":"The maximum percent of bases allowed to mismatch per alignment.  Note: Ns are counted as mismatches"},"value":0.02,"minValue":0,"maxValue":1},{"name":"mosaik.hash_size","fieldLabel":"Hash Size","renderData":{"helpPopup":"The hash size used in alignment (see Mosaik documentation).  A large value is preferred for sequences expected to be highly similar to the reference"},"minValue":0,"value":32},{"name":"mosaik.max_hash_positions","fieldLabel":"Max Hash Positions","renderData":{"helpPopup":"The maximum number of hash matches that are passed to local alignment."},"minValue":0,"value":200},{"name":"mosaik.align_threshold","fieldLabel":"Alignment Threshold","renderData":{"helpPopup":"The alignment score required for an alignment to continue to local alignment.  Because the latter is slow, a higher value can improve speed."},"value":55}]');