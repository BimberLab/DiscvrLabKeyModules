package org.labkey.sequenceanalysis.run;

import org.apache.log4j.Logger;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.sequenceanalysis.pipeline.SequencePipelineSettings;

import java.io.File;
import java.util.Collection;

/**
 * User: bimber
 * Date: 10/27/13
 * Time: 9:19 PM
 */
public interface AlignerWrapper
{
    public String getName();

    public String getDescription();

    public String getXtype();

    public Collection<ClientDependency> getClientDependencies();

    public void createIndex(File fasta) throws PipelineJobException;

    public File doAlignment(File inputFastq, String basename, SequencePipelineSettings settings) throws PipelineJobException;
}
