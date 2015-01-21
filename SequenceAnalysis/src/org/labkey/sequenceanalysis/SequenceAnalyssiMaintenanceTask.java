package org.labkey.sequenceanalysis;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.SystemMaintenance;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by bimber on 9/15/2014.
 */
public class SequenceAnalyssiMaintenanceTask implements SystemMaintenance.MaintenanceTask
{
    private static Logger _log = Logger.getLogger(SequenceAnalyssiMaintenanceTask.class);

    public SequenceAnalyssiMaintenanceTask()
    {

    }

    @Override
    public String getDescription()
    {
        return "Delete SequenceAnalysis Artifacts";
    }

    @Override
    public String getName()
    {
        return "DeleteSequenceAnalysisArtifacts";
    }

    @Override
    public boolean canDisable()
    {
        return true;
    }

    @Override
    public boolean hideFromAdminPage()
    {
        return false;
    }

    @Override
    public void run()
    {
        //delete sequence text files and library artifacts not associated with a DB record
        try
        {
            processContainer(ContainerManager.getRoot());
        }
        catch (Exception e)
        {
            _log.error(e.getMessage(), e);
        }
    }

    private void processContainer(Container c) throws IOException
    {
        PipeRoot root = PipelineService.get().getPipelineRootSetting(c);
        if (root != null)
        {
            //first sequences
            File sequenceDir = new File(root.getRootPath(), ".sequences");
            if (sequenceDir.exists())
            {
                TableInfo tableRefNtSequences = SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_REF_NT_SEQUENCES);
                TableSelector ts = new TableSelector(tableRefNtSequences, Collections.singleton("rowid"), new SimpleFilter(FieldKey.fromString("container"), c.getId()), null);
                final Set<String> expectedSequences = new HashSet<>();
                for (Integer rowId : ts.getArrayList(Integer.class))
                {
                    expectedSequences.add(rowId + ".txt.gz");
                }

                for (File child : sequenceDir.listFiles())
                {
                    if (!expectedSequences.contains(child.getName()))
                    {
                        deleteFile(child);
                    }
                }
            }

            //then libraries
            File libraryDir = SequenceAnalysisManager.get().getReferenceLibraryDir(c);
            if (libraryDir != null && libraryDir.exists())
            {
                TableInfo ti = SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_REF_NT_SEQUENCES);
                TableSelector ts = new TableSelector(ti, Collections.singleton("rowid"), new SimpleFilter(FieldKey.fromString("container"), c.getId()), null);
                Set<String> expectedLibraries = new HashSet<>();
                for (Integer rowId : ts.getArrayList(Integer.class))
                {
                    expectedLibraries.add(rowId.toString());
                }

                for (File child : libraryDir.listFiles())
                {
                    if ("log".equals(FileUtil.getExtension(child)))
                    {
                        continue;  //always ignore log files
                    }

                    if (!expectedLibraries.contains(child.getName()))
                    {
                        deleteFile(child);
                    }
                }
            }

            //finally outputfiles
            File sequenceOutputsDir = new File(root.getRootPath(), "sequenceOutputs");
            if (sequenceOutputsDir.exists())
            {
                TableInfo ti = SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_OUTPUTFILES);
                TableSelector ts = new TableSelector(ti, Collections.singleton("dataid"), new SimpleFilter(FieldKey.fromString("container"), c.getId()), null);
                Set<String> expectedFileNames = new HashSet<>();
                for (Integer dataId : ts.getArrayList(Integer.class))
                {
                    ExpData d = ExperimentService.get().getExpData(dataId);
                    if (d != null)
                    {
                        expectedFileNames.add(d.getFile().getName());

                        //TODO: this seems like a hack.  certaion file types can get gzipped or indexed, so add those variants:
                        expectedFileNames.add(d.getFile().getName() + ".bai");
                        expectedFileNames.add(d.getFile().getName() + ".tbi");
                        expectedFileNames.add(d.getFile().getName() + ".bgz");

                        expectedFileNames.add(d.getFile().getName() + ".gz");
                        expectedFileNames.add(d.getFile().getName() + ".gz.tbi");
                    }
                }

                for (File child : sequenceOutputsDir.listFiles())
                {
                    if (!expectedFileNames.contains(child.getName()))
                    {
                        deleteFile(child);
                    }
                }
            }
        }

        for (Container child : c.getChildren())
        {
            processContainer(child);
        }
    }

    private void deleteFile(File f) throws IOException
    {
        _log.info("deleting sequence file: " + f.getPath());
        if (f.isDirectory())
        {
            FileUtils.deleteDirectory(f);
        }
        else
        {
            f.delete();
        }
    }
}