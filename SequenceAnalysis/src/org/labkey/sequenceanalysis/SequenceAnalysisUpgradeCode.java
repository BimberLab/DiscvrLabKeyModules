package org.labkey.sequenceanalysis;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DeferredUpgrade;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.UpgradeCode;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.sequenceanalysis.RefNtSequenceModel;
import org.labkey.api.util.PageFlowUtil;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * User: bimber
 * Date: 1/31/13
 * Time: 8:29 PM
 */
public class SequenceAnalysisUpgradeCode implements UpgradeCode
{
    private static final Logger _log = LogManager.getLogger(SequenceAnalysisUpgradeCode.class);

    /** called at 12.277-12.278 */
    @SuppressWarnings({"UnusedDeclaration"})
    @DeferredUpgrade
    public void migrateSequenceField(final ModuleContext moduleContext)
    {
        TableInfo ti = SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_REF_NT_SEQUENCES);

        // original installs used SQL scripts to populate data into the ref NT table.  with the
        // new storage scheme this is not ideal, so simply delete these for new installs
        try
        {
            if (moduleContext.isNewInstall())
            {
                TableSelector ts = new TableSelector(ti, PageFlowUtil.set("rowid"));
                List<Integer> rowIds = ts.getArrayList(Integer.class);

                SequenceAnalysisManager.get().deleteRefNtSequenceWithoutUserSchema(rowIds);
                return;
            }

            TableSelector ts = new TableSelector(ti);
            List<RefNtSequenceModel> nts = ts.getArrayList(RefNtSequenceModel.class);
            _log.info(nts.size() + " total sequences to migrate");
            for (RefNtSequenceModel nt : nts)
            {
                String seq = nt.getLegacySequence();
                if (StringUtils.trimToNull(seq) != null)
                {
                    _log.info("writing sequence: " + nt.getName());
                    nt.createFileForSequence(moduleContext.getUpgradeUser(), seq, null);
                }
            }
        }
        catch (Exception e)
        {
            _log.error("Error upgrading sequenceanalysis module", e);
        }
    }

    /** called at 12.292-12.293 and 12.293-12.294*/
    @SuppressWarnings({"UnusedDeclaration"})
    @DeferredUpgrade
    public void migrateLibraryTracks(final ModuleContext moduleContext)
    {
        if (moduleContext.isNewInstall())
            return;

        try
        {
            TableInfo ti = SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_LIBRARY_TRACKS);
            TableSelector ts = new TableSelector(ti, PageFlowUtil.set("fileid", "library_id", "container"));
            List<Map> dataIds = ts.getArrayList(Map.class);
            _log.info(dataIds.size() + " total tracks to migrate");

            for (Map<String, Object> map : dataIds)
            {
                Integer libraryId = (Integer)map.get("library_id");
                Container container = ContainerManager.getForId(map.get("container").toString());
                if (container == null)
                {
                    continue;
                }

                File libraryDir = new File(SequenceAnalysisManager.get().getReferenceLibraryDir(container), libraryId.toString());
                File trackDir = new File(libraryDir, "tracks");
                if (!trackDir.exists())
                {
                    trackDir.mkdirs();
                }

                ExpData d = ExperimentService.get().getExpData((Integer)map.get("fileid"));
                if (d != null && d.getFile() != null)
                {
                    if (!d.getFile().getParentFile().getName().equals("tracks"))
                    {
                        File dest = new File(trackDir, d.getName());
                        _log.info("moving file: " + d.getFile().getPath() + " to " + dest.getPath());
                        FileUtils.moveFile(d.getFile(), dest);

                        d.setDataFileURI(dest.toURI());
                        d.save(moduleContext.getUpgradeUser());
                    }
                    else
                    {
                        _log.info("track file is already in proper location: " + d.getFile().getName());
                    }
                }
                else
                {
                    _log.warn("dataId not found for track with dataId: " + map.get("fileid"));
                }
            }

            //migrate chain files
            TableInfo chainTable = SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_CHAIN_FILES);
            TableSelector ts2 = new TableSelector(chainTable, PageFlowUtil.set("chainFile", "genomeId1", "container"));
            List<Map> chainFiles = ts2.getArrayList(Map.class);
            _log.info(chainFiles.size() + " total chain files to migrate");

            for (Map<String, Object> map : chainFiles)
            {
                Integer libraryId = (Integer)map.get("genomeId1");
                Container container = ContainerManager.getForId(map.get("container").toString());
                File libraryDir = new File(SequenceAnalysisManager.get().getReferenceLibraryDir(container), libraryId.toString());
                File chainDir = new File(libraryDir, "chainFiles");
                if (!chainDir.exists())
                {
                    chainDir.mkdirs();
                }

                ExpData d = ExperimentService.get().getExpData((Integer)map.get("chainFile"));
                if (d != null && d.getFile() != null)
                {
                    if (!d.getFile().getParentFile().getName().equals("chainFiles"))
                    {
                        File dest = new File(chainDir, d.getName());
                        _log.info("moving file: " + d.getFile().getPath() + " to " + dest.getPath());
                        FileUtils.moveFile(d.getFile(), dest);

                        d.setDataFileURI(dest.toURI());
                        d.save(moduleContext.getUpgradeUser());
                    }
                    else
                    {
                        _log.info("chain file is already in proper location: " + d.getFile().getName());
                    }
                }
                else
                {
                    _log.warn("dataId not found for chain file with dataId: " + map.get("chainFile"));
                }
            }
        }
        catch (Exception e)
        {
            _log.error("Error upgrading sequenceanalysis module", e);
        }
    }

    /** called at 12.306-12.307*/
    @SuppressWarnings({"UnusedDeclaration"})
    @DeferredUpgrade
    public void appendSequenceLength(final ModuleContext moduleContext)
    {
        if (moduleContext.isNewInstall())
            return;

        SequenceAnalysisManager.get().apppendSequenceLength(moduleContext.getUpgradeUser(), _log);
    }
}
