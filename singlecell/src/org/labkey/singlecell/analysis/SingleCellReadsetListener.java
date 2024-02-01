package org.labkey.singlecell.analysis;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.security.User;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.singlecell.SingleCellModule;
import org.labkey.singlecell.SingleCellSchema;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


public class SingleCellReadsetListener implements SequenceAnalysisService.ReadsetListener
{
    private static final Logger _log = LogManager.getLogger(SingleCellReadsetListener.class);

    @Override
    public void onReadsetCreate(User u, Readset rs, @Nullable Readset replacedReadset, @Nullable PipelineJob job)
    {
        if (replacedReadset == null)
        {
            return;
        }

        // Find cDNAs using this value:
        Container c = ContainerManager.getForId(rs.getContainer());
        c = c.isWorkbookOrTab() ? c.getParent() : c;
        TableInfo cDNA = QueryService.get().getUserSchema(u, c, SingleCellSchema.NAME).getTable(SingleCellSchema.TABLE_CDNAS);

        for (String field : new String[]{"readsetId", "tcrReadsetId", "hashingReadsetId", "citeseqReadsetId"})
        {
            TableSelector ts = new TableSelector(cDNA, PageFlowUtil.set("rowId", "container"), new SimpleFilter(FieldKey.fromString(field), replacedReadset.getReadsetId()), null);
            if (ts.exists())
            {
                List<Map<String, Object>> toUpdateRows = new ArrayList<>();
                List<Map<String, Object>> oldKeys = new ArrayList<>();
                ts.forEachResults(cDNARow -> {
                    Map<String, Object> toUpdate = new CaseInsensitiveHashMap<>();
                    toUpdate.put("rowid", cDNARow.getInt(FieldKey.fromString("rowid")));
                    toUpdate.put("container", cDNARow.getString(FieldKey.fromString("container")));
                    toUpdate.put(field, rs.getReadsetId());

                    if (job != null)
                    {
                        job.getLogger().info("Also updating " + field + " on cDNA: " + cDNARow.getInt(FieldKey.fromString("rowid")) + " to " + rs.getReadsetId());
                    }

                    toUpdateRows.add(toUpdate);
                    oldKeys.add(Map.of("rowid", cDNARow.getInt(FieldKey.fromString("rowid"))));
                });

                try
                {
                    cDNA.getUpdateService().updateRows(u, c, toUpdateRows, oldKeys, null, null);
                }
                catch (SQLException | BatchValidationException | QueryUpdateServiceException | InvalidKeyException e)
                {
                    _log.error("Unable to update cDNA records", e);
                }
            }
        }
    }

    @Override
    public boolean isAvailable(Container c, User u)
    {
        return c.getActiveModules().contains(ModuleLoader.getInstance().getModule(SingleCellModule.class));
    }
}
