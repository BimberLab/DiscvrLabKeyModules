DELETE FROM sequenceanalysis.readData
WHERE (SELECT c.entityId from core.containers c WHERE c.entityid = readData.container) IS NULL;