ALTER TABLE sequenceanalysis.alignment_summary_junction ADD container ENTITYID;
GO
UPDATE sequenceanalysis.alignment_summary_junction
SET container = (SELECT s.container FROM sequenceanalysis.alignment_summary s WHERE s.rowid = alignment_id);