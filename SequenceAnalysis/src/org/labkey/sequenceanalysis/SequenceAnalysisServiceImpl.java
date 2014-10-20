package org.labkey.sequenceanalysis;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.ldk.LDKService;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.GenomeTrigger;
import org.labkey.api.sequenceanalysis.ReferenceLibraryHelper;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.SequenceFileHandler;
import org.labkey.sequenceanalysis.run.util.TabixRunner;
import org.labkey.sequenceanalysis.util.ReferenceLibraryHelperImpl;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * User: bimber
 * Date: 10/27/13
 * Time: 9:21 PM
 */
public class SequenceAnalysisServiceImpl extends SequenceAnalysisService
{
    private static SequenceAnalysisServiceImpl _instance = new SequenceAnalysisServiceImpl();

    private Set<GenomeTrigger> _genomeTriggers = new HashSet<>();
    private Set<SequenceFileHandler> _fileHandlers = new HashSet<>();

    private SequenceAnalysisServiceImpl()
    {

    }

    public static SequenceAnalysisServiceImpl get()
    {
        return _instance;
    }

    @Override
    public ReferenceLibraryHelper getLibraryHelper(File refFasta)
    {
        return new ReferenceLibraryHelperImpl(refFasta);
    }

    @Override
    public void registerGenomeTrigger(GenomeTrigger trigger)
    {
        _genomeTriggers.add(trigger);
    }

    public Set<GenomeTrigger> getGenomeTriggers()
    {
        return Collections.unmodifiableSet(_genomeTriggers);
    }

    @Override
    public void registerFileHandler(SequenceFileHandler handler)
    {
        _fileHandlers.add(handler);

        LDKService.get().registerQueryButton(handler.getButtonConfig(), SequenceAnalysisSchema.SCHEMA_NAME, SequenceAnalysisSchema.TABLE_OUTPUTFILES);
    }

    @Override
    public File createTabixIndex(File input, @Nullable Logger log) throws PipelineJobException
    {
        return new TabixRunner(log).execute(input);
    }

    public Set<SequenceFileHandler> getFileHandlers()
    {
        return _fileHandlers;
    }

//    private MessageDigest _md5 = null;
//
//    @Override
//    public SAMSequenceDictionary makeSequenceDictionary(File referenceFile)
//    {
//        if (_md5 == null)
//        {
//            try
//            {
//                _md5 = MessageDigest.getInstance("MD5");
//            }
//            catch (NoSuchAlgorithmException e)
//            {
//                throw new PicardException("MD5 algorithm not found", e);
//            }
//        }
//
//        final ReferenceSequenceFile refSeqFile = ReferenceSequenceFileFactory.getReferenceSequenceFile(referenceFile, false);
//        ReferenceSequence refSeq;
//        final List<SAMSequenceRecord> ret = new ArrayList<>();
//        final Set<String> sequenceNames = new HashSet<>();
//        while ((refSeq = refSeqFile.nextSequence()) != null)
//        {
//            if (sequenceNames.contains(refSeq.getName()))
//            {
//                throw new PicardException("Sequence name appears more than once in reference: " + refSeq.getName());
//            }
//
//            sequenceNames.add(refSeq.getName());
//            ret.add(makeSequenceRecord(refSeq));
//        }
//        return new SAMSequenceDictionary(ret);
//    }
//
//    /**
//     * Create one SAMSequenceRecord from a single fasta sequence
//     */
//    private SAMSequenceRecord makeSequenceRecord(final ReferenceSequence refSeq)
//    {
//        SAMSequenceRecord ret = new SAMSequenceRecord(refSeq.getName(), refSeq.length());
//
//        // Compute MD5 of upcased bases
//        final byte[] bases = refSeq.getBases();
//        for (int i = 0; i < bases.length; ++i) {
//            bases[i] = StringUtil.toUpperCase(bases[i]);
//        }
//
//        ret.setAttribute(SAMSequenceRecord.MD5_TAG, md5Hash(bases));
//
//        return ret;
//    }
//
//    private String md5Hash(final byte[] bytes)
//    {
//        _md5.reset();
//        _md5.update(bytes);
//        String s = new BigInteger(1, _md5.digest()).toString(16);
//        if (s.length() != 32) {
//            final String zeros = "00000000000000000000000000000000";
//            s = zeros.substring(0, 32 - s.length()) + s;
//        }
//
//        return s;
//    }
}
