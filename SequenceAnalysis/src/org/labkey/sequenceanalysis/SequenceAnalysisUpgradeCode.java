package org.labkey.sequenceanalysis;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.UpgradeCode;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.sequenceanalysis.RefNtSequenceModel;

import java.util.List;

/**
 * User: bimber
 * Date: 1/31/13
 * Time: 8:29 PM
 */
public class SequenceAnalysisUpgradeCode implements UpgradeCode
{
    private static final Logger _log = Logger.getLogger(SequenceAnalysisUpgradeCode.class);

    /** called at 12.277-12.278 */
    @SuppressWarnings({"UnusedDeclaration"})
    public void migrateSequenceField(final ModuleContext moduleContext)
    {
        try
        {
            TableInfo ti = SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_REF_NT_SEQUENCES);
            TableSelector ts = new TableSelector(ti);
            List<RefNtSequenceModel> nts = ts.getArrayList(RefNtSequenceModel.class);
            _log.info(nts.size() + " total sequences to migrate");
            for (RefNtSequenceModel nt : nts)
            {
                String seq = nt.getLegacySequence();
                if (StringUtils.trimToNull(seq) != null)
                {
                    _log.info("writing sequence: " + nt.getName());
                    nt.createFileForSequence(moduleContext.getUpgradeUser(), seq);
                }
            }
        }
        catch (Exception e)
        {
            _log.error("Error upgrading sequenceanalysis module", e);
        }
    }
}
