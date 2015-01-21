ALTER TABLE sequenceanalysis.reference_libraries DROP COLUMN snps_file;

declare @Command nvarchar(max) = '';
select @Command = @Command + 'ALTER TABLE sequenceanalysis.sequence_analyses DROP CONSTRAINT ' + d.name
from sys.default_constraints d
where d.object_id = (SELECT constid FROM sysconstraints WHERE id=OBJECT_ID('sequenceanalysis.sequence_analyses')
    AND COL_NAME(id,colid)='makePublic'
    AND OBJECTPROPERTY(constid, 'IsDefaultCnst')=1
    );
execute(@Command);

ALTER TABLE sequenceanalysis.sequence_analyses DROP COLUMN makePublic;