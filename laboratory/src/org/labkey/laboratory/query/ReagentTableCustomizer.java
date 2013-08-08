package org.labkey.laboratory.query;

import org.labkey.api.data.TableCustomizer;
import org.labkey.api.data.TableInfo;
import org.labkey.api.ldk.LDKService;

/**
 * Created with IntelliJ IDEA.
 * User: bimber
 * Date: 4/19/13
 * Time: 1:26 PM
 */
public class ReagentTableCustomizer implements TableCustomizer
{
    public void customize(TableInfo ti)
    {
        LaboratoryTableCustomizer lc = new LaboratoryTableCustomizer();
        lc.customize(ti);

        TableCustomizer tc = LDKService.get().getDefaultTableCustomizer();
        tc.customize(ti);
    }
}
