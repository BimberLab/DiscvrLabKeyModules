ALTER TABLE htcondorconnector.condorJobs DROP COLUMN nodeId;
ALTER TABLE htcondorconnector.condorJobs DROP COLUMN isActive;
ALTER TABLE htcondorconnector.condorJobs DROP COLUMN hadError;

ALTER TABLE htcondorconnector.condorJobs ADD hasStarted bit;
ALTER TABLE htcondorconnector.condorJobs ADD status NVARCHAR(100);
ALTER TABLE htcondorconnector.condorJobs ADD location NVARCHAR(1000);
ALTER TABLE htcondorconnector.condorJobs ADD activeTaskId NVARCHAR(1000);
