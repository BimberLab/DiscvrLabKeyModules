DELETE FROM laboratory.result_status WHERE status = 'No Data';
INSERT INTO laboratory.result_status (status) VALUES ('No Data');