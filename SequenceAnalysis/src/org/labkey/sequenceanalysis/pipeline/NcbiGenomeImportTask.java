package org.labkey.sequenceanalysis.pipeline;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;
import org.apache.http.client.utils.URIBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.AbstractTaskFactory;
import org.labkey.api.pipeline.AbstractTaskFactorySettings;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.util.Compress;
import org.labkey.api.util.FileType;
import org.labkey.sequenceanalysis.SequenceAnalysisManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by bimber on 1/5/2015.
 */
public class NcbiGenomeImportTask extends PipelineJob.Task<NcbiGenomeImportTask.Factory>
{
    protected NcbiGenomeImportTask(Factory factory, PipelineJob job)
    {
        super(factory, job);
    }

    public static class Factory extends AbstractTaskFactory<AbstractTaskFactorySettings, Factory>
    {
        public Factory()
        {
            super(NcbiGenomeImportTask.class);
            //setLocation("webserver");
        }

        public List<FileType> getInputTypes()
        {
            return Collections.emptyList();
        }

        public String getStatusName()
        {
            return PipelineJob.TaskStatus.running.toString();
        }

        public List<String> getProtocolActionNames()
        {
            return Arrays.asList("dbSNP Import");
        }

        public PipelineJob.Task createTask(PipelineJob job)
        {
            return new NcbiGenomeImportTask(this, job);
        }

        public boolean isJobComplete(PipelineJob job)
        {
            return false;
        }
    }

    @NotNull
    public RecordedActionSet run() throws PipelineJobException
    {
        List<RecordedAction> actions = new ArrayList<>();

        actions.add(processSequences());

        return new RecordedActionSet(actions);
    }

    private RecordedAction processSequences() throws PipelineJobException
    {        
        RecordedAction action = new RecordedAction("Loading NCBI genome");
        action.setStartTime(new Date());

        URL genomeDir = getFtpURL(null);
        getJob().getLogger().info("reading directory: " + genomeDir.toString());

        FTPClient ftpClient = new FTPClient();
        try
        {
            ftpClient.connect("ftp.ncbi.nlm.nih.gov");

            if (!FTPReply.isPositiveCompletion(ftpClient.getReplyCode()))
            {
                ftpClient.disconnect();
                throw new PipelineJobException("FTP server refused connection.");
            }

            ftpClient.login("anonymous", "");

            String target = "/genomes/" + getPipelineJob().getRemoteDirName() + "/Assembled_chromosomes/seq";
            getJob().getLogger().debug("changing to folder: " + target);
            boolean success = ftpClient.changeWorkingDirectory(target);
            if (!success)
            {
                getJob().getLogger().warn("unable to change to folder: " + target);
            }
            ftpClient.setDefaultTimeout(200);
            ftpClient.setConnectTimeout(200);
            ftpClient.setDataTimeout(200);

            List<Integer> sequenceIds = new ArrayList<>();
            FTPFile[] files = ftpClient.listFiles();
            if (files.length == 0)
            {
                getJob().getLogger().warn("no files found under: " + target);
                getJob().getLogger().warn(ftpClient.getReplyString());
            }

            List<String> fileNames = new ArrayList<>();
            for (FTPFile child : files)
            {
                fileNames.add(child.getName());
                if (child.getName().endsWith(".fa.gz"))
                {
                    if ((StringUtils.trimToNull(getPipelineJob().getGenomePrefix()) == null || child.getName().startsWith(getPipelineJob().getGenomePrefix())) && child.getName().contains("_chr"))
                    {
                        getJob().getLogger().info("processing file: " + child.getName());

                        int sequenceId = createSequenceForFile(ftpClient, child);
                        sequenceIds.add(sequenceId);
                    }
                    else
                    {
                        getJob().getLogger().info("skipping file: " + child.getName());
                    }
                }
            }

            ftpClient.logout();

            if (sequenceIds.isEmpty())
            {
                getJob().getLogger().info("no sequences found.  this might mean there are no .fa.gz files.  files found:");
                for (String fn : fileNames)
                {
                    getJob().getLogger().info(fn);
                }
            }
            else
            {
                SequenceAnalysisManager.get().createReferenceLibrary(sequenceIds, getJob().getContainer(), getJob().getUser(), getPipelineJob().getGenomeName(), "Created automatically from an NCBI download");
            }
        }
        catch (Exception e)
        {
            throw new PipelineJobException(e);
        }
        finally
        {
            if (ftpClient.isConnected())
            {
                try
                {
                    ftpClient.disconnect();
                }
                catch (IOException e)
                {
                    //ignore
                }
            }
        }
        
        action.setEndTime(new Date());

        return action;
    }

    private int createSequenceForFile(FTPClient client, FTPFile seqFile) throws Exception
    {
        getJob().getLogger().info("copying file locally: " + seqFile.getName());
        File decompressed = File.createTempFile(seqFile.getName(), ".fasta");
        File compressed = new File(decompressed.getPath() + ".gz");
        compressed.deleteOnExit();
        decompressed.deleteOnExit();

        try (OutputStream outputStream = new FileOutputStream(compressed))
        {
            client.setFileType(FTP.BINARY_FILE_TYPE);

            try (InputStream is = client.retrieveFileStream(seqFile.getName()))
            {
                IOUtils.copy(is, outputStream);
                if (seqFile.getSize() != compressed.length())
                {
                    getJob().getLogger().error("file size did not match original file from FTP site");
                }

                Compress.decompressGzip(compressed, decompressed);
                getJob().getLogger().info("copy complete");
            }
            client.completePendingCommand();
        }
        compressed.delete();

        String name = inferSeqName(seqFile.getName()).replaceAll(".fa.gz", "");
        name = name.replaceAll("chr", ""); //most resources reference human using only the numbers

        getJob().getLogger().info("creating reference sequence for: " + name);
        Map<String, String> params = new HashMap<>();
        params.put("mol_type", "gDNA");
        params.put("category", "Genome");
        params.put("species", getPipelineJob().getSpecies());
        params.put("name", name);

        List<Integer> seqIds = SequenceAnalysisManager.get().importRefSequencesFromFasta(getJob().getContainer(), getJob().getUser(), decompressed, true, params, getJob().getLogger());
        decompressed.delete();
        if (seqIds.size() == 1)
        {
            return seqIds.get(0);
        }
        else if (seqIds.size() > 1)
        {
            throw new PipelineJobException("file contained more than 1 sequence: " + seqFile.getName());
        }
        else
        {
            throw new PipelineJobException("file did not contain sequences: " + seqFile.getName());
        }
    }

    private String inferSeqName(String fileName)
    {
        String[] tokens = fileName.split("_");

        return tokens[tokens.length - 1];
    }

    private URL getFtpURL(@Nullable String suffix) throws PipelineJobException
    {
        try
        {
            URIBuilder builder = new URIBuilder(NcbiGenomeImportPipelineProvider.URL_BASE + "/" + getPipelineJob().getRemoteDirName() + "/");
            if (suffix != null)
            {
                builder.setPath(builder.getPath() + suffix);
            }
            
            return new URL(builder.toString());
        }
        catch (MalformedURLException | URISyntaxException e)
        {
            throw new PipelineJobException(e);
        }
    }

    private NcbiGenomeImportPipelineJob getPipelineJob()
    {
        return (NcbiGenomeImportPipelineJob)getJob();
    }
}
