CREATE TABLE laboratory.result_status (
  status varchar(100),
  not_trusted bit,

  CONSTRAINT PK_result_status PRIMARY KEY (status)
);

GO

INSERT INTO laboratory.result_status (status, not_trusted) VALUES ('Definitive', 0);
INSERT INTO laboratory.result_status (status, not_trusted) VALUES ('Outlier', 1);
INSERT INTO laboratory.result_status (status, not_trusted) VALUES ('Replaced', 1);