package org.labkey.sequenceanalysis.run.util;

import org.json.JSONObject;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.sequenceanalysis.pipeline.CommandLineParam;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;

import java.util.Arrays;

/**
 * Created by bimber on 4/2/2017.
 */
public class GenomicsDBImportHandler extends AbstractGenomicsDBImportHandler
{
    public static final String NAME = "GenomicsDB Import";

    public GenomicsDBImportHandler()
    {
        super(ModuleLoader.getInstance().getModule(SequenceAnalysisModule.class), NAME, "This will run GATK\'s GenomicsDBImport on a set of GVCF files.  Note: this cannot work against any VCF file - these are primarily VCFs created using GATK\'s HaplotypeCaller.", null, Arrays.asList(
                ToolParameterDescriptor.create("fileBaseName", "Filename", "This is the basename that will be used for the output gzipped VCF", "textfield", null, "CombinedGenotypes"),
                ToolParameterDescriptor.create("doCopyGVcfLocal", "Copy gVCFs To Working Directory", "If selected, the gVCFs will be copied to the working directory first, which can improve performance when working with a large set of files.", "checkbox", new JSONObject(){{
                    put("checked", false);
                }}, false),
                ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("--batch-size"), "batchSize", "Batch Size", "Batch size controls the number of samples for which readers are open at once and therefore provides a way to minimize memory consumption. However, it can take longer to complete. Use the consolidate flag if more than a hundred batches were used. This will improve feature read time. batchSize=0 means no batching (i.e. readers for all samples will be opened at once) Defaults to 0.", "ldk-integerfield", null, null),
                ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("--reader-threads"), "readerThreads", "Reader Threads", "How many simultaneous threads to use when opening VCFs in batches; higher values may improve performance when network latency is an issue", "ldk-integerfield", null, null),
                ToolParameterDescriptor.create("disableFileLocking", "Disable File Locking", "Certain filesystems do not support file locking, including NFS and Lustre.  If your data will be processed on a filesystem that does not support locking, check this.", "checkbox", new JSONObject(){{
                    put("checked", true);
                }}, true),
                ToolParameterDescriptor.create("sharedPosixOptimizations", "Use Shared Posix Optimizations", "This enabled optimizations for large shared filesystems, such as lustre.", "checkbox", new JSONObject(){{
                    put("checked", true);
                }}, true),
                ToolParameterDescriptor.create("nativeMemoryBuffer", "C++ Memory Buffer", "By default, the pipeline java processes are allocated nearly all of the requested RAM.  GenomicsDB requires memory for the C++ layer - this value (in GB) will be reserved for this.  We recommend about 15-25% of the total job RAM", "checkbox", new JSONObject(){{
                    put("minValue", 0);
                }}, 24),
                ToolParameterDescriptor.create("scatterGather", "Scatter/Gather Options", "If selected, this job will be divided to run job per chromosome.  The final step will take the VCF from each intermediate step and combined to make a final VCF file.", "sequenceanalysis-variantscattergatherpanel", new JSONObject(){{
                    put("defaultValue", "chunked");
                }}, false)
        ));
    }

    @Override
    public SequenceOutputProcessor getProcessor()
    {
        return new Processor(false);
    }
}
