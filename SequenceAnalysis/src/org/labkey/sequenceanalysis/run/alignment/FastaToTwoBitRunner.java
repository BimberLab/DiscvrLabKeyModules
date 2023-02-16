package org.labkey.sequenceanalysis.run.alignment;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.pipeline.AlignerIndexUtil;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.run.AbstractCommandWrapper;
import org.labkey.api.util.FileUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by bimber on 3/11/2015.
 */
public class FastaToTwoBitRunner extends AbstractCommandWrapper
{
    private static final String TWO_BIT = "2bit";

    public FastaToTwoBitRunner(@Nullable Logger logger)
    {
        super(logger);
    }

    public File getCachedIndex(ReferenceGenome referenceGenome)
    {
        File baseDir = new File(referenceGenome.getSourceFastaFile().getParentFile(), (referenceGenome.isTemporaryGenome() ? "" : AlignerIndexUtil.INDEX_DIR + "/") + TWO_BIT);
        return new File(baseDir, FileUtil.getBaseName(referenceGenome.getSourceFastaFile()) + ".2bit");
    }

    public File createIndex(ReferenceGenome referenceGenome, File outputDir, PipelineContext ctx) throws PipelineJobException
    {
        ctx.getLogger().info("Creating 2bit index");

        boolean hasCachedIndex = AlignerIndexUtil.hasCachedIndex(ctx, TWO_BIT, referenceGenome);
        if (hasCachedIndex)
        {
            return getCachedIndex(referenceGenome);
        }

        File indexDir = new File(outputDir, TWO_BIT);
        File indexFile = new File(indexDir, getExpectedName(referenceGenome.getWorkingFastaFile()));
        if (!indexDir.exists())
        {
            indexDir.mkdirs();
        }
        convertFile(referenceGenome.getWorkingFastaFile(), indexFile);

        //recache if not already
        AlignerIndexUtil.saveCachedIndex(hasCachedIndex, ctx, indexDir, TWO_BIT, referenceGenome);

        return indexFile;
    }

    public File convertFile(File fasta, File output) throws PipelineJobException
    {
        List<String> args = new ArrayList<>();
        args.add(getExe().getPath());
        args.add(fasta.getPath());
        args.add(output.getPath());

        execute(args);

        if (!output.exists())
        {
            throw new PipelineJobException("expected 2bit file not created: " + output.getPath());
        }
        return output;
    }

    public String getExpectedName(File refFasta)
    {
        return FileUtil.getBaseName(refFasta) + ".2bit";
    }

    protected File getExe()
    {
        return SequencePipelineService.get().getExeForPackage("BLATPATH", "faToTwoBit");
    }
}
