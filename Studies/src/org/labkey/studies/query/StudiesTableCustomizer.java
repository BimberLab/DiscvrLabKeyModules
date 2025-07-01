package org.labkey.studies.query;

import org.apache.commons.collections4.MultiValuedMap;
import org.apache.logging.log4j.Logger;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveKeyedHashSetValuedMap;
import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.TableCustomizer;
import org.labkey.api.data.TableInfo;
import org.labkey.api.ldk.LDKService;
import org.labkey.api.study.DatasetTable;
import org.labkey.api.util.logging.LogHelper;

public class StudiesTableCustomizer implements TableCustomizer
{
    private static final Logger _log = LogHelper.getLogger(StudiesTableCustomizer.class, "Messages from StudiesTableCustomizer");

    @Override
    public void customize(TableInfo tableInfo)
    {
        MultiValuedMap<String, String> props = new CaseInsensitiveKeyedHashSetValuedMap<>();
        if (tableInfo.getPkColumnNames().size() > 1)
        {
            final CaseInsensitiveHashMap<String> keys = new CaseInsensitiveHashMap<>();
            tableInfo.getPkColumnNames().forEach(x -> keys.put(x, x));

            if (keys.containsKey("objectId"))
            {
                props.put("primaryKeyField", keys.get("objectId"));
            }
            else if (tableInfo instanceof DatasetTable ds)
            {
                if (ds.getDataset().isDemographicData())
                {
                    String subjectCol = ds.getDataset().getStudy().getSubjectColumnName();
                    if (keys.containsKey(subjectCol))
                    {
                        props.put("primaryKeyField", keys.get(subjectCol));
                    }
                    else
                    {
                        _log.error("Demographics dataset does not list subject col (" + subjectCol + ") as a PK. Table: " + tableInfo.getName());
                    }
                }
            }
        }

        LDKService.get().getDefaultTableCustomizer(props).customize(tableInfo);
        if (tableInfo instanceof AbstractTableInfo ati)
        {
            doCustomize(ati);
        }
        else
        {
            _log.error("Expected table to be instance of AbstractTableInfo. Table: " + tableInfo.getName());
        }
    }

    private void doCustomize(AbstractTableInfo ati)
    {
        // TODO:
        // Overlapping studies/cohorts
        // TimepointLabel
    }
}
