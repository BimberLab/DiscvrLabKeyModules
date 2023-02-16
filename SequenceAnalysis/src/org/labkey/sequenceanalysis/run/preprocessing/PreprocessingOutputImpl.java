package org.labkey.sequenceanalysis.run.preprocessing;

import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.util.Pair;
import org.labkey.api.sequenceanalysis.pipeline.DefaultPipelineStepOutput;
import org.labkey.api.sequenceanalysis.pipeline.PreprocessingStep;

import java.io.File;

/**
 * User: bimber
 * Date: 6/21/2014
 * Time: 9:19 AM
 */
public class PreprocessingOutputImpl extends DefaultPipelineStepOutput implements PreprocessingStep.Output
{
    public static final String PROCESSED_FQ_ROLE = "Processed FASTQ";
    public static final String FASTQ_PROCESSING_OUTPUT_ROLE = "FASTQ Processing Output";

    private final Pair<File, File> _inputs;
    private Pair<File, File> _processed;

    public PreprocessingOutputImpl(File inputFile1, File inputFile2)
    {
        _inputs = Pair.of(inputFile1, inputFile2);
    }

    @Override
    public Pair<File, File> getProcessedFastqFiles()
    {
        return _processed;
    }

    public void setProcessedFastq(Pair<File, File> files) throws PipelineJobException
    {
        _processed = files;

        if (_processed.first.exists())
        {
            addOutput(_processed.first, PROCESSED_FQ_ROLE);
        }
        else
        {
            throw new PipelineJobException("No output created after preprocessing, expected: " + _processed.first.getPath());
        }

        //even if we had 2 inputs, the result could be a single file
        if (_processed.second != null)
        {
            if (_processed.second.exists())
            {
                addOutput(_processed.second, PROCESSED_FQ_ROLE);
            }
            else
            {
                throw new PipelineJobException("No output created after preprocessing, expected: " + _processed.second.getPath());
            }
        }
    }
}
