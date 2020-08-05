package org.labkey.blast;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.UpgradeCode;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.blast.model.BlastJob;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Created by bimber on 1/27/2016.
 */
public class BLASTUpgradeCode implements UpgradeCode
{
    private static final Logger _log = LogManager.getLogger(BLASTUpgradeCode.class);

    /**
     * called at 13.32-13.33
     */
    @SuppressWarnings({"UnusedDeclaration"})
    public void migrateDatabaseDirs(final ModuleContext moduleContext)
    {
        try
        {
            File originalDbDir = null;
            String BLAST_DB_DIR = "blastDbDir";
            Map<String, String> props = PropertyManager.getProperties(BLASTManager.CONFIG_PROPERTY_DOMAIN);
            if (props.containsKey(BLAST_DB_DIR))
            {
                originalDbDir = new File(props.get(BLAST_DB_DIR));
            }

            if (originalDbDir == null)
            {
                _log.info("no BLAST db dir was set, upgrade is not necessary");
                return;
            }
            else if (!originalDbDir.exists())
            {
                _log.info("BLAST db dir does not exist: " + originalDbDir.getPath());
                return;
            }

            TableInfo ti = BLASTSchema.getInstance().getSchema().getTable(BLASTSchema.TABLE_DATABASES);
            TableSelector ts = new TableSelector(ti, PageFlowUtil.set("objectid"));
            List<String> databaseIds = ts.getArrayList(String.class);
            for (String dbId : databaseIds)
            {
                _log.info("processing DB: " + dbId);

                String containerId = new TableSelector(ti, PageFlowUtil.set("container"), new SimpleFilter(FieldKey.fromString("objectid"), dbId), null).getObject(String.class);
                Container c = ContainerManager.getForId(containerId);
                File dbDir = BLASTManager.get().getDatabaseDir(c, true);
                File[] files = originalDbDir.listFiles();
                if (files == null)
                {
                    _log.info("no files found, continuing");
                    continue;
                }

                _log.info("total files: " + files.length);
                for (File child : files)
                {
                    if (child.getName().contains(dbId))
                    {
                        File dest = new File(dbDir, child.getName());
                        _log.info("moving file: " + child.getName() + ", to: " + dest.getPath());
                        FileUtils.moveFile(child, dest);

                        if (child.getName().startsWith("blast-"))
                        {
                            //update pipeline jobs table
                            TableInfo pipelineTable = PipelineService.get().getJobsTable(moduleContext.getUpgradeUser(), c);
                            List<Integer> rowIds = new TableSelector(pipelineTable, PageFlowUtil.set("rowid"), new SimpleFilter(FieldKey.fromString("FilePath"), child.getPath()), null).getArrayList(Integer.class);
                            if (!rowIds.isEmpty())
                            {
                                for (Integer rowId : rowIds)
                                {
                                    _log.info("updating pipeline job log: " + rowId);
                                    SQLFragment sql = new SQLFragment("UPDATE pipeline.statusfiles SET FilePath = ? WHERE RowId = ?", dest.getPath(), rowId);
                                    new SqlExecutor(DbScope.getLabKeyScope()).execute(sql);
                                }
                            }
                        }
                    }
                }
            }
        }
        catch (Exception e)
        {
            _log.error(e.getMessage(), e);
        }
    }
}
