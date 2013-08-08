ALTER TABLE laboratory.samples ADD passage_number integer;

ALTER TABLE laboratory.sample_type ADD container entityid;
UPDATE laboratory.sample_type
SET container = (SELECT entityid FROM core.containers c WHERE c.name = 'Shared' and Parent = (select EntityId from core.Containers c2 WHERE c2.Parent is null));
