ALTER TABLE sequenceanalysis.ref_nt_sequences ADD container entityid;
ALTER TABLE sequenceanalysis.ref_nt_sequences ADD sequenceFile int;
alter table sequenceanalysis.ref_nt_sequences alter column sequence drop not null;

UPDATE sequenceanalysis.ref_nt_sequences SET container = (select entityid from core.containers where name = 'Shared' and parent = (select entityid from core.containers where name is null));