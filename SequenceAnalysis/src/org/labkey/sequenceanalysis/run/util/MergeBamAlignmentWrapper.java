package org.labkey.sequenceanalysis.run.util;

import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMReadGroupRecord;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.ValidationStringency;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.pipeline.SortSamWrapper;
import org.labkey.api.sequenceanalysis.run.CreateSequenceDictionaryWrapper;
import org.labkey.api.sequenceanalysis.run.PicardWrapper;
import org.labkey.api.util.FileUtil;
import org.labkey.sequenceanalysis.util.SequenceUtil;

import java.io.File;
import java.io.IOException;
import java.util.Date;
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
            Date start = new Date();
            getLogger().info("Running MergeBamAlignment: " + alignedBam.getPath());
            setStringency(ValidationStringency.SILENT);

            File querySortedAlignedBam = alignedBam;
            if (SequenceUtil.getBamSortOrder(alignedBam) != SAMFileHeader.SortOrder.queryname)
            {
                // NOTE: we need to sort w/ picard since it performs a slightly different
                // string comparison than samtools and MergeBamAlignment will crash otherwise
                getLogger().info("Queryname sorting input BAM: " + alignedBam.getPath());
                querySortedAlignedBam = new File(getOutputDir(alignedBam), FileUtil.getBaseName(alignedBam) + ".querysort.bam");
                SortSamWrapper sortSamWrapper = new SortSamWrapper(getLogger());
                sortSamWrapper.execute(alignedBam, querySortedAlignedBam, SAMFileHeader.SortOrder.queryname);
            }

            List<String> params = getBaseArgs();
            params.add("ALIGNED_BAM=" + querySortedAlignedBam.getPath());
            params.add("MAX_INSERTIONS_OR_DELETIONS=-1");
            inferMaxRecordsInRam(params);

            //can take a long time to calculate
            //getLogger().info("total alignments in starting BAM: ");
            //SequenceUtil.logAlignmentCount(alignedBam, getLogger());

            File unmappedReadsBam;
            SamReaderFactory fact = SamReaderFactory.makeDefault();
            SAMReadGroupRecord rg = null;
            try (SamReader reader = fact.open(alignedBam))
            {
                SAMFileHeader header = reader.getFileHeader();
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
                    rg = header.getReadGroups().get(0);
                }

                FastqToSamWrapper fq = new FastqToSamWrapper(getLogger());
                fq.setOutputDir(alignedBam.getParentFile());
                fq.setStringency(ValidationStringency.SILENT);
                unmappedReadsBam = fq.execute(inputFastq1, inputFastq2, SAMFileHeader.SortOrder.queryname, rg);
                if (!unmappedReadsBam.exists())
                {
                    throw new PipelineJobException("BAM file not created, expected: " + unmappedReadsBam.getPath());
                }
            }

            SortSamWrapper sorter = new SortSamWrapper(getLogger());
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
            params.add("SORT_ORDER=" + SAMFileHeader.SortOrder.coordinate.name());

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

            if (!alignedBam.equals(querySortedAlignedBam))
            {
                getLogger().debug("deleting temp query sorted BAM: " + querySortedAlignedBam.getName());
                querySortedAlignedBam.delete();
            }

            //getLogger().info("\ttotal alignments in unmapped reads BAM: ");
            //SequenceUtil.logAlignmentCount(unmappedReadsBam, getLogger());
            unmappedReadsBam.delete();

            if (outputFile == null)
            {
                getLogger().info("renaming BAM to: " + alignedBam.getName());
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

            //getLogger().info("\ttotal alignments in final BAM: ");
            //SequenceUtil.logAlignmentCount(mergedFile, getLogger());

            getLogger().info("\tMergeBamAlignment duration: " + DurationFormatUtils.formatDurationWords((new Date()).getTime() - start.getTime(), true, true));

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

    protected String getToolName()
    {
        return "MergeBamAlignment";
    }
}
