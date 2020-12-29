-- find constraint name
declare @name nvarchar(32),
  @sql nvarchar(1000)

select @name = O.name
from sys.default_constraints O
where parent_object_id = object_id('sequenceanalysis.sequence_readsets')
  AND type = 'D'
  and O.name like '%comme%'

-- delete if found
if not @name is null
begin
select @sql = 'ALTER TABLE sequenceanalysis.sequence_readsets DROP CONSTRAINT [' + @name + ']'
         execute sp_executesql @sql
end

ALTER TABLE sequenceanalysis.sequence_readsets ALTER COLUMN comments NVARCHAR(MAX);