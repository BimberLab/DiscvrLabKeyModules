package org.labkey.sequenceanalysis.run.variant;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.run.AbstractCommandWrapper;
import org.labkey.api.util.FileUtil;
import org.labkey.sequenceanalysis.run.util.BgzipRunner;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by bimber on 8/24/2016.
 */
public class SnpEffWrapper extends AbstractCommandWrapper
{
    public SnpEffWrapper(Logger log)
    {
        super(log);
    }

    public void runSnpEff(Integer genomeId, Integer geneId, File snpEffBaseDir, File input, File output, @Nullable File intervalsFile) throws PipelineJobException
    {
        getLogger().info("Annotating VCF with snpEff");
        String basename = getGenomeBasename(genomeId, geneId);

        List<String> params = new ArrayList<>();
        params.add(SequencePipelineService.get().getJava8FilePath());
        params.addAll(SequencePipelineService.get().getJavaOpts());
        params.add("-jar");
        params.add(getSnpEffJar().getPath());
        params.add("ann");
        params.add(basename);
        params.add("-noStats");
        params.add("-nodownload");
        params.add("-dataDir");
        params.add(snpEffBaseDir.getPath());
        params.add("-c");
        params.add(getSnpEffConfigFile().getPath());
        params.add("-configOption");
        params.add(basename + ".genome=" + basename);
        params.add("-t");
        params.add(input.getPath());

        if (intervalsFile != null)
        {
            params.add("-interval");
            params.add(intervalsFile.getPath());
        }

        File unzippedVcf = new File(getOutputDir(output), "snpEff.vcf");
        execute(params, unzippedVcf);

        if (!unzippedVcf.exists())
        {
            throw new PipelineJobException("output not found: " + unzippedVcf.getName());
        }

        unzippedVcf = new BgzipRunner(getLogger()).execute(unzippedVcf);
        try
        {
            if (!unzippedVcf.equals(output))
            {
                if (output.exists())
                {
                    getLogger().debug("deleting pre-existing output file: " + output.getPath());
                    output.delete();
                }
                FileUtils.moveFile(unzippedVcf, output);
            }
            SequenceAnalysisService.get().ensureVcfIndex(output, getLogger());
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
    }

    public String getGenomeBasename(Integer genomeId, Integer geneFileId)
    {
        return genomeId + "_" + geneFileId;
    }

    public File getExpectedIndexDir(File snpEffBaseDir, Integer genomeId, Integer geneFileId)
    {
        String basename = getGenomeBasename(genomeId, geneFileId);
        return new File(snpEffBaseDir, basename);
    }

    public void buildIndex(File snpEffBaseDir, ReferenceGenome genome, File genes, Integer geneFileId) throws PipelineJobException
    {
        getLogger().info("Building SnpEff index for: "+ genome.getGenomeId() + " / " + geneFileId);

        File genomeDir = getExpectedIndexDir(snpEffBaseDir, genome.getGenomeId(), geneFileId);
        if (genomeDir.exists() && genomeDir.list().length > 0)
        {
            getLogger().info("directory already exists, will not re-build");
            return;
        }

        genomeDir.mkdirs();

        List<String> params = new ArrayList<>();
        params.add(SequencePipelineService.get().getJavaFilepath());
        params.addAll(SequencePipelineService.get().getJavaOpts());
        params.add("-jar");
        params.add(getSnpEffJar().getPath());
        params.add("build");
        params.add("-c");
        params.add(getSnpEffConfigFile().getPath());

        String ext = FileUtil.getExtension(genes);
        if ("gtf".equalsIgnoreCase(ext))
        {
            params.add("-gtf22");
        }
        else if ("gff".equalsIgnoreCase(ext))
        {
            params.add("-gff3");
        }
        else
        {
            throw new PipelineJobException("unable to process extension: " + ext);
        }

        String basename = getGenomeBasename(genome.getGenomeId(), geneFileId);
        params.add("-v");
        params.add("-nodownload");
        params.add(basename);

        try
        {
            Files.createSymbolicLink(new File(genomeDir, "sequences.fa").toPath(), genome.getSourceFastaFile().toPath());
            Files.createSymbolicLink(new File(genomeDir, "genes." + ext).toPath(), genes.toPath());
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        params.add("-dataDir");
        params.add(snpEffBaseDir.getPath());
        params.add("-configOption");
        params.add(basename + ".genome=" + basename);
        //params.add(basename);

        execute(params);
    }


    private File getJarDir()
    {
        String path = PipelineJobService.get().getConfigProperties().getSoftwarePackagePath("SNPEFFPATH");
        if (path != null)
        {
            return new File(path, "snpEff");
        }

        path = PipelineJobService.get().getConfigProperties().getSoftwarePackagePath(SequencePipelineService.SEQUENCE_TOOLS_PARAM);
        if (path == null)
        {
            path = PipelineJobService.get().getAppProperties().getToolsDirectory();
        }

        return path == null ? new File("snpEff") : new File(path, "snpEff");
    }

    private File getSnpEffJar()
    {
        return new File(getJarDir(), "snpEff.jar");
    }

    private File getSnpEffConfigFile()
    {
        return new File(getJarDir(), "snpEff.config");
    }
}
