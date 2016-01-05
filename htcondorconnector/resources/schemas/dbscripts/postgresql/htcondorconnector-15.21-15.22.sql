ALTER TABLE htcondorconnector.condorJobs DROP COLUMN nodeId;
ALTER TABLE htcondorconnector.condorJobs DROP COLUMN isActive;
ALTER TABLE htcondorconnector.condorJobs DROP COLUMN hadError;

ALTER TABLE htcondorconnector.condorJobs ADD hasStarted boolean;
ALTER TABLE htcondorconnector.condorJobs ADD status VARCHAR(100);
ALTER TABLE htcondorconnector.condorJobs ADD location VARCHAR(1000);
ALTER TABLE htcondorconnector.condorJobs ADD activeTaskId VARCHAR(1000);
