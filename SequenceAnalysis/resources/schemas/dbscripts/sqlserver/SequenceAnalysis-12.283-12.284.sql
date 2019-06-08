declare @Command nvarchar(max) = '';
select @Command = @Command + 'ALTER TABLE sequenceanalysis.sequence_analyses DROP CONSTRAINT ' + d.name
from sys.default_constraints d
where d.object_id = (SELECT constid FROM sysconstraints WHERE id=OBJECT_ID('sequenceanalysis.sequence_analyses')
    AND COL_NAME(id,colid)='inputfile'
    AND OBJECTPROPERTY(constid, 'IsDefaultCnst')=1
    );
execute (@Command);

declare @Command2 nvarchar(max) = '';
select @Command2 = @Command2 + 'ALTER TABLE sequenceanalysis.sequence_analyses DROP CONSTRAINT ' + d.name
from sys.default_constraints d
where d.object_id = (SELECT constid FROM sysconstraints WHERE id=OBJECT_ID('sequenceanalysis.sequence_analyses')
    AND COL_NAME(id,colid)='inputfile2'
    AND OBJECTPROPERTY(constid, 'IsDefaultCnst')=1
    );
execute (@Command2);

declare @Command3 nvarchar(max) = '';
select @Command3 = @Command3 + 'ALTER TABLE sequenceanalysis.sequence_analyses DROP CONSTRAINT ' + d.name
from sys.default_constraints d
where d.object_id = (SELECT constid FROM sysconstraints WHERE id=OBJECT_ID('sequenceanalysis.sequence_analyses')
    AND COL_NAME(id,colid)='outputfile'
    AND OBJECTPROPERTY(constid, 'IsDefaultCnst')=1
    );
execute (@Command3);

ALTER TABLE sequenceanalysis.sequence_analyses DROP COLUMN inputfile;
ALTER TABLE sequenceanalysis.sequence_analyses DROP COLUMN inputfile2;
ALTER TABLE sequenceanalysis.sequence_analyses DROP COLUMN outputfile;
