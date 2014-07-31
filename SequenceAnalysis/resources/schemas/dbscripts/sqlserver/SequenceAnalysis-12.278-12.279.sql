EXEC core.executeJavaUpgradeCode 'migrateSequenceField';

ALTER TABLE sequenceanalysis.ref_nt_sequences DROP COLUMN sequence;

declare @Command nvarchar(max) = '';
select @Command = @Command + 'ALTER TABLE sequenceanalysis.ref_nt_sequences DROP CONSTRAINT ' + d.name
from sys.default_constraints d
where d.object_id = (SELECT constid FROM sysconstraints WHERE id=OBJECT_ID('sequenceanalysis.ref_nt_sequences')
    AND COL_NAME(id,colid)='status'
    AND OBJECTPROPERTY(constid, 'IsDefaultCnst')=1
    );
execute(@Command);

ALTER TABLE sequenceanalysis.ref_nt_sequences DROP COLUMN status;

ALTER TABLE sequenceanalysis.nt_snps DROP CONSTRAINT fk_nt_snps_alignment;
DROP TABLE sequenceanalysis.sequence_reads;
DROP TABLE sequenceanalysis.sequence_alignments;
DROP TABLE sequenceanalysis.aa_snps;
DROP TABLE sequenceanalysis.nt_snps;