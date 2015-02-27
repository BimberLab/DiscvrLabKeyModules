package org.labkey.sequenceanalysis.run.analysis;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.io.IOUtils;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.model.AnalysisModel;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.AbstractAnalysisStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.AnalysisStep;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.util.Compress;
import org.labkey.api.util.FileUtil;
import org.labkey.api.sequenceanalysis.run.AbstractCommandPipelineStep;
import org.labkey.sequenceanalysis.run.util.BlastNWrapper;
import org.labkey.sequenceanalysis.util.SequenceUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;

/**
 * User: bimber
 * Date: 7/3/2014
 * Time: 11:29 AM
 */
public class BlastUnmappedReadAnalysis extends AbstractCommandPipelineStep<BlastNWrapper> implements AnalysisStep
{
    public BlastUnmappedReadAnalysis(PipelineStepProvider provider, PipelineContext ctx)
    {
        super(provider, ctx, new BlastNWrapper(ctx.getLogger()));
    }

    public static class Provider extends AbstractAnalysisStepProvider<BlastUnmappedReadAnalysis>
    {
        public Provider()
        {
            super("BlastUnmappedReads", "BLAST Unmapped Reads", null, "This will BLAST any unmapped reads against NCBI's nr database, creating a summary report of hits.", Collections.<ToolParameterDescriptor>emptyList(), null, null);
        }

        @Override
        public BlastUnmappedReadAnalysis create(PipelineContext ctx)
        {
            return new BlastUnmappedReadAnalysis(this, ctx);
        }
    }

    @Override
    public void init(List<AnalysisModel> models) throws PipelineJobException
    {

    }

    @Override
    public Output performAnalysisPerSampleRemote(Readset rs, File inputBam, ReferenceGenome referenceGenome, File outputDir) throws PipelineJobException
    {
        AnalysisOutputImpl output = new AnalysisOutputImpl();

        //if necessary, download taxdb:
        File taxDbDir = new File(getPipelineCtx().getWorkingDirectory(), "taxdb");
        if (!(new File(taxDbDir, "taxdb.btd")).exists())
        {
            try
            {
                downloadTaxDb(taxDbDir);
                output.addIntermediateFile(taxDbDir);

            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }


        File fasta = new File(outputDir, FileUtil.getBaseName(inputBam) + "_unmapped.fasta");
        output.addIntermediateFile(fasta);
        output.addInput(fasta, "Unmapped Reads FASTA");

        getPipelineCtx().getLogger().info("writing unmapped reads to FASTA");
        List<File> fastas = UnmappedReadExportAnalysis.writeUnmappedReadsAsFasta(inputBam, fasta, getPipelineCtx().getLogger(), 1000L);
        if (fastas.size() > 1)
        {
            getPipelineCtx().getLogger().info("The unmapped reads have been split into " + fastas.size() + " smaller files to BLAST.  This may still take some time to run.");
        }

        Map<String, Integer> hitCount = new TreeMap<>();
        Map<String, Integer> taxaCount = new TreeMap<>();

        for (File f : fastas)
        {
            getPipelineCtx().getLogger().info("processing file: " + f.getName());
            output.addIntermediateFile(f, "Unmapped Reads FASTA");
            long lineCount = SequenceUtil.getLineCount(f);
            if (lineCount == 0)
            {
                f.delete();

                getPipelineCtx().getLogger().info("There are no unmapped reads, skipping");
            }
            else
            {
                File blastResults = new File(outputDir, FileUtil.getBaseName(f) + ".bls");

                List<String> args = new ArrayList<>();
                args.add("-outfmt");
                args.add("6 qseqid qlen slen length sseqid sgi sacc evalue bitscore pident mismatch staxids sscinames scomnames sblastnames stitle");
                args.add("-perc_identity");
                args.add("90");

                args.add("-evalue");
                args.add("1e-10");
                args.add("-best_hit_score_edge");
                args.add("0.05");
                args.add("-best_hit_overhang");
                args.add("0.25");
                args.add("-max_target_seqs");
                args.add("1");

                getWrapper().doRemoteBlast(f, blastResults, args, taxDbDir);
                if (blastResults.exists())
                {
                    try (BufferedReader lr = new BufferedReader(new FileReader(blastResults)))
                    {
                        String line;
                        while ((line = lr.readLine()) != null)
                        {
                            //qseqid qlen slen length sseqid sgi sacc evalue bitscore pident mismatch staxids sscinames scomnames sblastnames stitle
                            String[] tokens = line.split("\t");
                            appendColumn(tokens, 12, hitCount);
                            appendColumn(tokens, 11, taxaCount);
                        }
                    }
                    catch (IOException e)
                    {
                        throw new PipelineJobException(e);
                    }

                    Compress.compressGzip(blastResults);
                    output.addInput(new File(blastResults.getPath() + ".gz"), "BLAST Results");
                }
            }
        }

        getPipelineCtx().getLogger().info("hits: ");
        for (String key : hitCount.keySet())
        {
            getPipelineCtx().getLogger().info(key + ": " + hitCount.get(key));
        }

        getPipelineCtx().getLogger().info("taxa: ");
        for (String key : taxaCount.keySet())
        {
            getPipelineCtx().getLogger().info(key + ": " + taxaCount.get(key));
        }

        return output;
    }

    private void appendColumn(String[] tokens, int colIdx, Map<String, Integer> map)
    {
        int total = map.containsKey(tokens[colIdx]) ? map.get(tokens[colIdx]) : 0;
        total++;

        map.put(tokens[colIdx], total);
    }

    @Override
    public Output performAnalysisPerSampleLocal(AnalysisModel model, File inputBam, File referenceFasta) throws PipelineJobException
    {
        return null;
    }

    private void downloadTaxDb(File outDir) throws IOException
    {
        getPipelineCtx().getJob().getLogger().info("downloading NCBI taxdb");
        if (!outDir.exists())
        {
            outDir.mkdirs();
        }

        FTPClient ftpClient = new FTPClient();
        try
        {
            ftpClient.connect("ftp.ncbi.nlm.nih.gov");

            if (!FTPReply.isPositiveCompletion(ftpClient.getReplyCode()))
            {
                ftpClient.disconnect();
                throw new IOException("FTP server refused connection.");
            }
            getPipelineCtx().getJob().getLogger().debug("connected");

            ftpClient.login("anonymous", "anonymous");
            ftpClient.enterLocalPassiveMode();
            getPipelineCtx().getJob().getLogger().debug("authenticated as anonymous: " + ftpClient.getReplyString());

            ftpClient.changeWorkingDirectory("/blast/db");
            FTPFile f = ftpClient.mlistFile("taxdb.tar.gz");
            getPipelineCtx().getJob().getLogger().debug("file size: " + f.getSize());

            File compressed = new File(outDir, "taxdb.tar.gz");
            try (OutputStream outputStream = new FileOutputStream(compressed))
            {
                ftpClient.setFileType(FTP.BINARY_FILE_TYPE);

                ftpClient.retrieveFile("taxdb.tar.gz", outputStream);
                if (f.getSize() != compressed.length())
                {
                    getPipelineCtx().getLogger().warn("file size did not match original file from FTP site");
                }

                getPipelineCtx().getLogger().info("copy complete");
            }

            ftpClient.logout();

            //extract TAR
            getPipelineCtx().getLogger().debug("extracting TAR");
            try (TarArchiveInputStream tais = new TarArchiveInputStream(new GZIPInputStream(new FileInputStream(compressed))))
            {
                TarArchiveEntry te = tais.getNextTarEntry();
                while (te != null)
                {
                    try (OutputStream out = new FileOutputStream(new File(outDir, te.getName())))
                    {
                        IOUtils.copy(tais, out);
                    }

                    te = tais.getNextTarEntry();
                }
            }
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
    }
}
