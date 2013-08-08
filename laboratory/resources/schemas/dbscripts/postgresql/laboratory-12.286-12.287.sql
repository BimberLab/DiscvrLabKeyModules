CREATE TABLE laboratory.result_status (
  status varchar(100),
  not_trusted boolean,

  CONSTRAINT PK_result_status PRIMARY KEY (status)
);

INSERT INTO laboratory.result_status (status, not_trusted) VALUES ('Definitive', false);
INSERT INTO laboratory.result_status (status, not_trusted) VALUES ('Outlier', true);
INSERT INTO laboratory.result_status (status, not_trusted) VALUES ('Replaced', true);