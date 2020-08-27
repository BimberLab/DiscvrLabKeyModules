ALTER TABLE cluster.clusterJobs ADD hostname varchar(1000);
ALTER TABLE cluster.clusterJobs ADD logModified timestamp;

ALTER TABLE cluster.clusterJobs DROP COLUMN hasStarted;