ALTER TABLE laboratory.samples ALTER COLUMN sequence NVARCHAR(MAX);
ALTER TABLE laboratory.dna_oligos ALTER COLUMN comments NVARCHAR(MAX);

declare @Command nvarchar(max) = '';
select @Command = @Command + 'ALTER TABLE laboratory.subjects DROP CONSTRAINT ' + d.name
from sys.default_constraints d
where d.object_id = (SELECT constid FROM sysconstraints WHERE id=OBJECT_ID('laboratory.subjects')
                                                              AND COL_NAME(id,colid)='comments'
                                                              AND OBJECTPROPERTY(constid, 'IsDefaultCnst')=1
);

execute(@Command);

ALTER TABLE laboratory.subjects ALTER COLUMN comments NVARCHAR(MAX);