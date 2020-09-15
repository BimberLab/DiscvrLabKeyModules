ALTER TABLE cluster.clusterJobs ADD hostname varchar(1000);
ALTER TABLE cluster.clusterJobs ADD logModified datetime;

ALTER TABLE cluster.clusterJobs DROP COLUMN hasStarted;