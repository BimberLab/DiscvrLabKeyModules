package org.labkey.jbrowse.model;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.DataType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.writer.PrintWriters;
import org.labkey.jbrowse.JBrowseManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.zip.GZIPOutputStream;

abstract public class DbBackedJsonFile extends JsonFile
{
    protected int _genomeId;
    protected String _suffix;
    protected ExpData _data;

    public DbBackedJsonFile(int genomeId, String label, String suffix, boolean createDatabaseRecordsIfNeeded, User user)
    {
        _genomeId = genomeId;
        _suffix = suffix;
        _label = label;
        String genomeContainer = new TableSelector(DbSchema.get(JBrowseManager.SEQUENCE_ANALYSIS, DbSchemaType.Module).getTable("reference_libraries"), PageFlowUtil.set("container"), new SimpleFilter(FieldKey.fromString("rowid"), genomeId), null).getObject(String.class);
        setContainer(genomeContainer);
        setCategory("Reference Annotations");
        setObjectId(genomeId + "." + suffix);

        _data = shouldExist() ? getOrCreateExpData(createDatabaseRecordsIfNeeded, user) : null;
    }

    @Override
    public ExpData getExpData()
    {
        return _data;
    }

    private ExpData getOrCreateExpData(boolean createDatabaseRecordsIfNeeded, User user)
    {
        ExpData data = ExperimentService.get().getExpDataByURL(getGtfOut(), getContainerObj());
        if (data == null)
        {
            if (!createDatabaseRecordsIfNeeded)
            {
                throw new IllegalStateException("Expected track to have been previously created: " + getGtfOut().getPath());
            }

            data = ExperimentService.get().createData(getContainerObj(), new DataType("JBrowseGFF"));
            data.setDataFileURI(getGtfOut().toURI());
            data.save(user);
        }

        return data;
    }

    @Override
    protected String getSourceFileName()
    {
        return getObjectId() + _suffix + ".gff.gz";
    }

    @Override
    public File getLocationOfProcessedTrack(boolean createDir)
    {
        File trackDir = getBaseDir();
        if (createDir && !trackDir.exists())
        {
            trackDir.mkdirs();
        }

        return new File(trackDir, getSourceFileName());
    }

    @Override
    public boolean needsProcessing()
    {
        return true;
    }

    @Override
    public String getLabel()
    {
        return _suffix;
    }

    protected File getGtfOut()
    {
        return getLocationOfProcessedTrack(false);
    }

    public boolean shouldExist()
    {
        return getSelector().exists();
    }

    abstract protected TableSelector getSelector();

    @Override
    public File prepareResource(Logger log, boolean throwIfNotPrepared, boolean forceReprocess) throws PipelineJobException
    {
        createGtf(log, throwIfNotPrepared, forceReprocess);
        return super.prepareResource(log, throwIfNotPrepared, forceReprocess);
    }

    public void createGtf(Logger log, boolean throwOnNotFound, boolean forceRecreate) throws PipelineJobException
    {
        try
        {
            File outFile = getGtfOut();
            if (throwOnNotFound && !outFile.exists())
            {
                throw new IllegalStateException("Expected track to have been previously created: " + outFile.getPath());
            }

            File parent = outFile.getParentFile();
            if (parent.exists())
            {
                if (forceRecreate)
                {
                    FileUtils.deleteDirectory(parent);
                    parent.mkdirs();
                }
            }
            else
            {
                parent.mkdirs();
            }

            if (forceRecreate || !outFile.exists())
            {
                try (final PrintWriter writer = PrintWriters.getPrintWriter(new GZIPOutputStream(new FileOutputStream(outFile))))
                {
                    printGtf(writer, log);
                }
            }
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
    }

    abstract protected void printGtf(PrintWriter writer, Logger log);
}
