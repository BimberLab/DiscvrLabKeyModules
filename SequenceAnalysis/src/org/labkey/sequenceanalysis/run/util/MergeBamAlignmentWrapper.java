package org.labkey.sequenceanalysis.run.util;

import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.ValidationStringency;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.util.FileUtil;
import org.labkey.sequenceanalysis.util.SequenceUtil;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

/**
 * User: bimber
 * Date: 10/29/12
 * Time: 7:55 PM
 */
public class MergeBamAlignmentWrapper extends PicardWrapper
{
    public MergeBamAlignmentWrapper(@Nullable Logger logger)
    {
        super(logger);
    }

    /**
     * If the output file is null, the original will be deleted and replaced by the fixed BAM
     */
    public File executeCommand(File refFasta, File alignedBam, File inputFastq1, @Nullable File inputFastq2, @Nullable File outputFile) throws PipelineJobException
    {
        try
        {
            getLogger().info("Running MergeBamAlignment: " + alignedBam.getPath());
            setStringency(ValidationStringency.SILENT);

            List<String> params = new LinkedList<>();
            params.add("java");
            params.add("-jar");
            params.add(getJar().getPath());

            params.add("ALIGNED_BAM=" + alignedBam.getPath());

            getLogger().info("\ttotal alignments in starting BAM: ");
            SequenceUtil.logAlignmentCount(alignedBam, getLogger());

            File unmappedReadsBam;
            SamReaderFactory fact = SamReaderFactory.makeDefault();
            try (SamReader reader = fact.open(alignedBam))
            {
                SAMFileHeader header = reader.getFileHeader();
                String rgId = null;
                if (header.getReadGroups().size() == 0)
                {
                    getLogger().warn("No read groups found in input BAM");
                }
                else if (header.getReadGroups().size() > 1)
                {
                    getLogger().warn("more than one read group found in BAM, ignoring groups");
                }
                else
                {
                    rgId = header.getReadGroups().get(0).getId();
                }

                FastqToSamWrapper fq = new FastqToSamWrapper(getLogger());
                fq.setOutputDir(alignedBam.getParentFile());
                fq.setStringency(ValidationStringency.SILENT);
                File unmappedReadsSam = fq.execute(inputFastq1, inputFastq2, SAMFileHeader.SortOrder.queryname, rgId == null ? "null" : rgId);
                unmappedReadsBam = new File(unmappedReadsSam.getParentFile(), FileUtil.getBaseName(unmappedReadsSam) + ".unmapped.bam");

                SamFormatConverterWrapper converterWrapper = new SamFormatConverterWrapper(getLogger());
                converterWrapper.setStringency(ValidationStringency.SILENT);
                converterWrapper.execute(unmappedReadsSam, unmappedReadsBam, true);
                if (!unmappedReadsBam.exists())
                {
                    throw new PipelineJobException("BAM file not created, expected: " + unmappedReadsBam.getPath());
                }
            }

            SortSamWrapper sorter = new SortSamWrapper(getLogger());
            sorter.setStringency(ValidationStringency.SILENT);
            unmappedReadsBam = sorter.execute(unmappedReadsBam, null, SAMFileHeader.SortOrder.queryname);

            params.add("UNMAPPED_BAM=" + unmappedReadsBam.getPath());

            //TODO: bisulfiteSequence

            params.add("REFERENCE_SEQUENCE=" + refFasta.getPath());
            boolean dictCreated = ensureDictionary(refFasta);

            //this argument is ignored by the tool, but is stil required
            params.add("PAIRED_RUN=false");

            params.add("ALIGNED_READS_ONLY=false");
            params.add("CLIP_ADAPTERS=false");
            params.add("CLIP_OVERLAPPING_READS=false");
            params.add("INCLUDE_SECONDARY_ALIGNMENTS=true");
            params.add("ALIGNER_PROPER_PAIR_FLAGS=false");
            params.add("VALIDATION_STRINGENCY=" + getStringency().name());

            File mergedFile = new File(getOutputDir(alignedBam), FileUtil.getBaseName(alignedBam) + ".merged.bam");
            params.add("OUTPUT=" + mergedFile.getPath());

            execute(params);

            if (dictCreated)
            {
                getExpectedDictionaryFile(refFasta).delete();
            }

            if (!mergedFile.exists())
            {
                throw new PipelineJobException("Output file could not be found: " + mergedFile.getPath());
            }

            getLogger().info("\ttotal alignments in unmapped reads BAM: ");
            SequenceUtil.logAlignmentCount(unmappedReadsBam, getLogger());
            unmappedReadsBam.delete();

            if (outputFile == null)
            {
                getLogger().info("renaming BAM");
                alignedBam.delete();
                try
                {
                    FileUtils.moveFile(mergedFile, alignedBam);
                    mergedFile = alignedBam;
                }
                catch (IOException e)
                {
                    throw new PipelineJobException(e);
                }
            }

            getLogger().info("\ttotal alignments in final BAM: ");
            SequenceUtil.logAlignmentCount(mergedFile, getLogger());

            return mergedFile;
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
    }

    private File getExpectedDictionaryFile(File referenceFasta)
    {
        return new File(getOutputDir(referenceFasta), FileUtil.getBaseName(referenceFasta) + ".dict");
    }

    private boolean ensureDictionary(File referenceFasta) throws PipelineJobException
    {
        File dictFile = getExpectedDictionaryFile(referenceFasta);
        if (!dictFile.exists())
        {
            getLogger().info("\tensure dictionary exists");
            new CreateSequenceDictionaryWrapper(getLogger()).execute(referenceFasta, false);

            return true;
        }

        return false;
    }

    protected File getJar()
    {
        return getPicardJar("MergeBamAlignment.jar");
    }
}
