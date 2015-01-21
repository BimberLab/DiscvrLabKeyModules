package org.labkey.sequenceanalysis.nav;

import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.laboratory.DataProvider;
import org.labkey.api.laboratory.LaboratoryService;
import org.labkey.api.laboratory.QueryCountNavItem;
import org.labkey.api.query.FieldKey;
import org.labkey.sequenceanalysis.SequenceAnalysisSchema;

/**
 * User: bimber
 * Date: 9/27/13
 * Time: 2:58 PM
 */
public class ReadsetCountNavItem extends QueryCountNavItem
{
    public ReadsetCountNavItem(DataProvider provider, LaboratoryService.NavItemCategory itemType, String category, String label)
    {
        super(provider, SequenceAnalysisSchema.SCHEMA_NAME, SequenceAnalysisSchema.TABLE_READSETS, itemType, category, label);
    }

    @Override
    protected SimpleFilter getFilter(Container c, TableInfo ti)
    {
        SimpleFilter filter = super.getFilter(c, ti);
        filter.addCondition(FieldKey.fromString("fileid"), null, CompareType.NONBLANK);
        return filter;
    }
}
