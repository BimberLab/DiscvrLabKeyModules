ALTER TABLE cluster.clusterJobs ADD clusterAccount varchar(1000);
ALTER TABLE cluster.clusterJobs ADD duration double precision;
ALTER TABLE cluster.clusterJobs ADD cpuUsed int;
ALTER TABLE cluster.clusterJobs ADD gpuUsed int;
