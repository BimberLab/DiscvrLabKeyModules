package org.labkey.sequenceanalysis.pipeline;

import org.labkey.api.data.CompareType;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.gwt.client.util.StringUtils;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.pipeline.WorkDirectoryTask;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.FileType;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.sequenceanalysis.SequenceAnalysisSchema;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: bimber
 * Date: 12/15/12
 * Time: 8:34 PM
 *
 * This task is designed to create the reference FASTA, which requires the DB.  this task will run
 * on the webserver
 */
public class ReferenceLibraryTask extends WorkDirectoryTask<ReferenceLibraryTask.Factory>
{
    private static final String ACTIONNAME = "Creating Reference Library FASTA";
    public static final String REFERENCE_DB_FASTA = "Reference Library FASTA";
    public static final String REFERENCE_DB_FASTA_OUTPUT = "Reference Library Output";

    private SequenceTaskHelper _taskHelper;

    protected ReferenceLibraryTask(Factory factory, PipelineJob job)
    {
        super(factory, job);
    }

    public static class Factory extends AbstractSequenceTaskFactory<Factory>
    {
        public Factory()
        {
            super(ReferenceLibraryTask.class);
            setJoin(true);
        }

        @Override
        public boolean isParticipant(PipelineJob job)
        {
            SequenceTaskHelper taskHelper = new SequenceTaskHelper(job);
            return taskHelper.getSettings().isDoAlignment();
        }

        public List<FileType> getInputTypes()
        {
            return Collections.emptyList();
        }

        public String getStatusName()
        {
            return "CREATING REFERENCE LIBRARY FASTA";
        }

        public List<String> getProtocolActionNames()
        {
            return Arrays.asList(ACTIONNAME);
        }

        public PipelineJob.Task createTask(PipelineJob job)
        {
            ReferenceLibraryTask task = new ReferenceLibraryTask(this, job);

            return task;
        }

        public boolean isJobComplete(PipelineJob job)
        {
            return false;
        }
    }

    private SequenceTaskHelper getHelper()
    {
        return _taskHelper;
    }

    public RecordedActionSet run() throws PipelineJobException
    {
        PipelineJob job = getJob();
        _taskHelper = new SequenceTaskHelper(job);
        getHelper().setWorkDir(_taskHelper.getSupport().getAnalysisDirectory());

        RecordedAction action = new RecordedAction(ACTIONNAME);
        getHelper().addInput(action, "Job Parameters", getHelper().getSupport().getParametersFile());
        getJob().getLogger().info("Creating Reference Library FASTA");

        //find the output FASTA
        File sharedDirectory = new File(getHelper().getSupport().getAnalysisDirectory(), SequenceTaskHelper.SHARED_SUBFOLDER_NAME);
        sharedDirectory.mkdirs();
        File refFasta = new File(sharedDirectory,  _taskHelper.getSettings().getRefDbFilename());
        createReferenceFasta(refFasta);
        if(!refFasta.exists())
        {
            throw new PipelineJobException("Reference file does not exist: " + refFasta.getPath());
        }
        //getHelper().addInput(action, "Input FASTA File", refFasta);
        getHelper().addOutput(action, "Reference Library Folder", sharedDirectory);
        getHelper().addOutput(action, REFERENCE_DB_FASTA, refFasta);

        getHelper().cleanup(_wd);

        return new RecordedActionSet(action);
    }

    private void createReferenceFasta(File output) throws PipelineJobException
    {
        FileWriter writer = null;
        try
        {
            if (getHelper().getSettings().hasCustomReference())
            {
                String name = StringUtils.trimToNull(getHelper().getSettings().getCustomReferenceSequenceName());
                String seq = StringUtils.trimToNull(getHelper().getSettings().getCustomReferenceSequence());
                if (name == null || seq == null)
                    throw new PipelineJobException("Must provide both a name and sequence for custom reference sequences");

                if (!output.exists())
                    output.createNewFile();
                writer = new FileWriter(output);

                writer.write(">" + name + System.getProperty("line.separator"));
                writer.write(seq);
            }
            else
            {
                getJob().getLogger().info("Downloading DNA DB:");
                getJob().getLogger().info("\tUsing filters:");

                SimpleFilter filter = getHelper().getSettings().getReferenceFilter();
                for (SimpleFilter.FilterClause fc : filter.getClauses())
                {
                    Object[] vals = fc.getParamVals();
                    StringBuilder sb = new StringBuilder();
                    String comparison = ((CompareType.CompareClause) fc).getComparison().getDisplayValue();
                    String delim = "";
                    int i = 0;
                    for (FieldKey fk : fc.getFieldKeys())
                    {
                        sb.append(delim).append(fk.getLabel()).append(" ").append(comparison).append(" ").append(vals[i]);

                        i++;
                        delim = " and ";
                    }
                    getJob().getLogger().info("\t\t" + sb.toString());
                }

                TableInfo ti = SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_REF_NT_SEQUENCES);
                TableSelector ts = new TableSelector(ti, PageFlowUtil.set("name", "sequence", "rowid"), filter, null);
                Map<String, Object>[] rows = ts.getArray(Map.class);

                getJob().getLogger().info("\tTotal reference sequences: " + rows.length);
                if (rows.length == 0)
                {
                    throw new PipelineJobException("There were no reference sequences returned, unable to perform alignment");
                }

                if (!output.exists())
                    output.createNewFile();
                writer = new FileWriter(output);

                for (Map<String, Object> row : rows)
                {
                    String name = (String)row.get("name");
                    name = name.replaceAll(":| ", "_"); //replace problem chars
                    writer.write(">" + String.valueOf(row.get("rowid")) + "|" + name + System.getProperty("line.separator"));
                    writer.write((String)row.get("sequence") + System.getProperty("line.separator"));
                }
            }
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
        finally
        {
            if (writer != null)
            {
                try
                {
                    writer.close();
                }
                catch (IOException e)
                {
                    //ignore
                }
            }
        }
    }
}
