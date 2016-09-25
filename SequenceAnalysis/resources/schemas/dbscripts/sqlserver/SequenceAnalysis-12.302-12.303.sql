ALTER TABLE sequenceanalysis.alignment_summary_junction ADD createdby USERID;
ALTER TABLE sequenceanalysis.alignment_summary_junction ADD created DATETIME;
ALTER TABLE sequenceanalysis.alignment_summary_junction ADD modifiedby USERID;
ALTER TABLE sequenceanalysis.alignment_summary_junction ADD modified DATETIME;
GO

update sequenceanalysis.alignment_summary_junction
set
created = (select created from sequenceanalysis.alignment_summary where alignment_summary.rowid = alignment_summary_junction.alignment_id),
modified = (select modified from sequenceanalysis.alignment_summary where alignment_summary.rowid = alignment_summary_junction.alignment_id),
createdby = (select createdby from sequenceanalysis.alignment_summary where alignment_summary.rowid = alignment_summary_junction.alignment_id),
modifiedby = (select modifiedby from sequenceanalysis.alignment_summary where alignment_summary.rowid = alignment_summary_junction.alignment_id)
;