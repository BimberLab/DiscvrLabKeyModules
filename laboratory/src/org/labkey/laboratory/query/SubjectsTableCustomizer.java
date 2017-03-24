package org.labkey.laboratory.query;

import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.TableCustomizer;
import org.labkey.api.data.TableInfo;
import org.labkey.api.ldk.LDKService;

/**
 * User: bimber
 * Date: 1/12/13
 * Time: 1:16 PM
 */
public class SubjectsTableCustomizer implements TableCustomizer
{
    public void customize(TableInfo ti)
    {
        //apply defaults
        TableCustomizer tc = LDKService.get().getDefaultTableCustomizer();
        tc.customize(ti);

        if (ti instanceof AbstractTableInfo)
        {
            LaboratoryTableCustomizer lab = new LaboratoryTableCustomizer();
            lab.addAgeCols((AbstractTableInfo)ti, ti.getColumn("birth"), ti.getColumn("death"));
            lab.appendProjectsCol(ti.getUserSchema(), (AbstractTableInfo)ti, "subjectname");
        }
    }
}
