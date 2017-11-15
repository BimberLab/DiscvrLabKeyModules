select animalId,
  Created as Date,
  case when currentColony = 'SNPRC' then
    case when sourceColony = 'SNPRC' then 'SNPRC' else 'S-NEPRC' end
  else     -- current colony = 'WNPRC'
      case when sourceColony = 'WNPRC' then 'WNPRC' else 'W-NEPRC' end
  end as sourceColony,
  sequenceNum as sequenceNumMin
from study.demographics
where status = 'alive'

union

select animalId,
  Created as Date,
  'Dead' as sourceColony,
  sequenceNum as sequenceNumMin
from study.demographics
where status <> 'alive'