package org.labkey.sequenceanalysis.analysis;

import au.com.bytecode.opencsv.CSVWriter;
import htsjdk.samtools.liftover.LiftOver;
import htsjdk.samtools.util.Interval;
import htsjdk.tribble.AbstractFeatureReader;
import htsjdk.tribble.CloseableTribbleIterator;
import htsjdk.tribble.FeatureReader;
import htsjdk.tribble.annotation.Strand;
import htsjdk.tribble.bed.BEDCodec;
import htsjdk.tribble.bed.BEDFeature;
import htsjdk.tribble.bed.FullBEDFeature;
import htsjdk.variant.variantcontext.Allele;
import htsjdk.variant.variantcontext.Genotype;
import htsjdk.variant.variantcontext.GenotypeBuilder;
import htsjdk.variant.variantcontext.GenotypesContext;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.variantcontext.VariantContextBuilder;
import htsjdk.variant.variantcontext.writer.Options;
import htsjdk.variant.variantcontext.writer.VariantContextWriter;
import htsjdk.variant.variantcontext.writer.VariantContextWriterBuilder;
import htsjdk.variant.vcf.VCFCodec;
import htsjdk.variant.vcf.VCFHeader;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.DataType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.pipeline.file.FileAnalysisJobSupport;
import org.labkey.api.security.User;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;
import org.labkey.sequenceanalysis.SequenceAnalysisSchema;
import org.labkey.sequenceanalysis.util.SequenceUtil;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Created by bimber on 8/26/2014.
 */
public class LiftoverHandler implements SequenceOutputHandler
{
    private FileType _bedFileType = new FileType("bed", false);
    //private FileType _gffFileType = new FileType("gff", false);
    private FileType _vcfFileType = new FileType(Arrays.asList("vcf", "bcf"), "vcf", false, FileType.gzSupportLevel.SUPPORT_GZ);

    public LiftoverHandler()
    {

    }

    @Override
    public String getName()
    {
        return "Lift To Alternate Genome";
    }

    @Override
    public String getDescription()
    {
        return null;
    }

    @Override
    public String getButtonJSHandler()
    {
        return "SequenceAnalysis.window.LiftoverWindow.buttonHandler";
    }

    @Override
    public ActionURL getButtonSuccessUrl(Container c, User u, List<Integer> outputFileIds)
    {
        return null;
    }

    @Override
    public Module getOwningModule()
    {
        return ModuleLoader.getInstance().getModule(SequenceAnalysisModule.class);
    }

    @Override
    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        return new LinkedHashSet<>(Arrays.asList(ClientDependency.fromPath("sequenceanalysis/window/LiftoverWindow.js")));
    }

    @Override
    public boolean doRunRemote()
    {
        return false;
    }

    @Override
    public boolean doRunLocal()
    {
        return true;
    }

    @Override
    public boolean canProcess(SequenceOutputFile f)
    {
        return f.getFile() != null && (
                _bedFileType.isType(f.getFile()) ||
                //_gffFileType.isType(f.getFile()) ||
                _vcfFileType.isType(f.getFile()));
    }

    @Override
    public List<String> validateParameters(JSONObject params)
    {
        return null;
    }

    @Override
    public OutputProcessor getProcessor()
    {
        return new Processor();
    }

    public class Processor implements OutputProcessor
    {
        @Override
        public void processFilesOnWebserver(PipelineJob job, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {
            for (SequenceOutputFile f : inputFiles)
            {
                job.getLogger().info("processing output: " + f.getFile().getName());

                RecordedAction action = new RecordedAction(getName());
                action.setStartTime(new Date());

                boolean isGzip = f.getFile().getPath().toLowerCase().endsWith(".gz");
                int dots = isGzip ? 2 : 1;
                String extension = isGzip ? FileUtil.getExtension(f.getFile().getName().replace(".gz$", "")) : FileUtil.getExtension(f.getFile());
                String baseName = FileUtil.getBaseName(f.getFile(), dots);

                Integer chainRowId = params.getInt("chainRowId");
                Map<String, Object> chainRow = new TableSelector(SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_CHAIN_FILES), PageFlowUtil.set("chainFile", "genomeId1", "genomeId2")).getObject(chainRowId, Map.class);
                ExpData chainData = ExperimentService.get().getExpData((Integer) chainRow.get("chainFile"));
                if (chainData == null || !chainData.getFile().exists())
                {
                    throw new PipelineJobException("Unable to find chain file: " + chainRowId);
                }

                int sourceGenomeId = (Integer) chainRow.get("genomeId1");
                int targetGenomeId = (Integer) chainRow.get("genomeId2");
                double pct = params.containsKey("pct") ? params.getDouble("pct") : 0.95;
                job.getLogger().info("using minimum percent match: " + pct);

                action.addInput(f.getFile(), "Input File");
                action.addInput(chainData.getFile(), "Chain File");

                File outDir = ((FileAnalysisJobSupport) job).getAnalysisDirectory();
                File lifted = new File(outDir, baseName + ".lifted-" + targetGenomeId + "." + extension);
                File unmappedOutput = new File(outDir, baseName + ".unmapped-" + targetGenomeId + "." + extension);

                try
                {
                    if (_bedFileType.isType(f.getFile()))
                    {
                        liftOverBed(chainData.getFile(), f.getFile(), lifted, unmappedOutput, job, pct);
                    }
                    //                else if (_gffFileType.isType(f.getFile()))
                    //                {
                    //                    //liftOverGFF(chainData.getFile(), f.getFile(), lifted, unmappedOutput, job);
                    //                }
                    else if (_vcfFileType.isType(f.getFile()))
                    {
                        liftOverVcf(chainData.getFile(), f.getFile(), lifted, unmappedOutput, job, f.getLibrary_id(), pct);
                    }
                    else
                    {
                        throw new UnsupportedOperationException("Unsupported file type: " + f.getFile().getName());
                    }
                }
                catch (IOException e)
                {
                    throw new PipelineJobException(e);
                }

                action.addOutput(lifted, "Lifted Features", lifted.exists());
                if (lifted.exists())
                {
                    SequenceOutputFile so1 = new SequenceOutputFile();
                    so1.setName(f.getName() + " (lifted)");
                    so1.setDescription("Contains features from " + f.getName() + " after liftover");
                    ExpData liftedData = ExperimentService.get().createData(job.getContainer(), new DataType("Liftover Output"));
                    liftedData.setDataFileURI(lifted.toURI());
                    liftedData.setName(lifted.getName());
                    liftedData.save(job.getUser());

                    so1.setDataId(liftedData.getRowId());
                    so1.setLibrary_id(targetGenomeId);
                    so1.setReadset(f.getReadset());
                    so1.setAnalysis_id(f.getAnalysis_id());
                    so1.setCategory(f.getCategory());
                    so1.setContainer(job.getContainerId());
                    so1.setCreated(new Date());
                    so1.setCreatedby(job.getUser().getUserId());
                    so1.setModified(new Date());
                    so1.setModifiedby(job.getUser().getUserId());

                    outputsToCreate.add(so1);
                }

                if (!unmappedOutput.exists())
                {
                    job.getLogger().info("no unmapped intervals");
                }
                else if (SequenceUtil.getLineCount(unmappedOutput) == 0)
                {
                    job.getLogger().info("no unmapped intervals");
                    unmappedOutput.delete();
                }
                else
                {
                    action.addOutput(unmappedOutput, "Unmapped features", false);

                    SequenceOutputFile so2 = new SequenceOutputFile();
                    so2.setName(f.getName() + " (lifted/unmapped)");
                    so2.setDescription("Contains the unmapped features after attempted liftover of " + f.getName());

                    ExpData unmappedData = ExperimentService.get().createData(job.getContainer(), new DataType("Liftover Output"));
                    unmappedData.setName(unmappedOutput.getName());
                    unmappedData.setDataFileURI(unmappedOutput.toURI());
                    unmappedData.save(job.getUser());

                    so2.setDataId(unmappedData.getRowId());
                    so2.setLibrary_id(f.getLibrary_id());
                    so2.setReadset(f.getReadset());
                    so2.setAnalysis_id(f.getAnalysis_id());
                    so2.setCategory(f.getCategory());
                    so2.setContainer(job.getContainerId());
                    so2.setCreated(new Date());
                    so2.setCreatedby(job.getUser().getUserId());
                    so2.setModified(new Date());
                    so2.setModifiedby(job.getUser().getUserId());

                    outputsToCreate.add(so2);
                }

                action.setEndTime(new Date());
                actions.add(action);
            }
        }

        @Override
        public void processFilesRemote(SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {

        }
    }

    public void liftOverVcf(File chain, File input, File output, File unmappedOutput, PipelineJob job, Integer originalGenomeId, double pct) throws IOException
    {
        LiftOver lo = new LiftOver(chain);
        VariantContextWriterBuilder builder1 = new VariantContextWriterBuilder();
        builder1.unsetOption(Options.INDEX_ON_THE_FLY);
        builder1.setOutputFile(output);
        builder1.setOption(Options.ALLOW_MISSING_FIELDS_IN_HEADER);

        VariantContextWriterBuilder builder2 = new VariantContextWriterBuilder();
        builder2.unsetOption(Options.INDEX_ON_THE_FLY);
        builder2.setOutputFile(unmappedOutput);
        builder2.setOption(Options.ALLOW_MISSING_FIELDS_IN_HEADER);

        int successfulIntervals = 0;
        int failedIntervals = 0;
        int count = 0;

        try (VariantContextWriter writer = builder1.build();VariantContextWriter unmappedWriter = builder2.build())
        {
            try (FeatureReader<VariantContext> reader = AbstractFeatureReader.getFeatureReader(input.getAbsolutePath(),  new VCFCodec(), false))
            {
                VCFHeader header = (VCFHeader)reader.getHeader();

                writer.writeHeader(header);
                unmappedWriter.writeHeader(header);

                //borrowed heavily from org/broadinstitute/gatk/tools/walkers/variantutils/LiftoverVariants.java
                try (CloseableTribbleIterator<VariantContext> i = reader.iterator())
                {
                    while (i.hasNext())
                    {
                        VariantContext vc = i.next();

                        Interval fromInterval = new Interval(vc.getChr(), vc.getStart(), vc.getEnd(), false, String.format("%s:%d", vc.getChr(), vc.getStart()));
                        int length = vc.getEnd() - vc.getStart();
                        Interval toInterval = lo.liftOver(fromInterval, pct);
                        VariantContext originalVC = vc;

                        if ( toInterval != null )
                        {
                            // check whether the strand flips, and if so reverse complement everything
                            if ( fromInterval.isPositiveStrand() != toInterval.isPositiveStrand() && vc.isPointEvent() )
                            {
                                vc = reverseComplement(vc);
                            }

                            vc = new VariantContextBuilder(vc).loc(toInterval.getSequence(), toInterval.getStart(), toInterval.getStart() + length).make();
                            vc = new VariantContextBuilder(vc)
                                    .attribute("OriginalGenome", originalGenomeId.toString())
                                    .attribute("OriginalChr", fromInterval.getSequence())
                                    .attribute("OriginalStart", fromInterval.getStart()).make();

                            writer.add(vc);
                            successfulIntervals++;
                        }
                        else
                        {
                            failedIntervals++;
                            unmappedWriter.add(originalVC);
                        }

                        count++;
                        if (count % 20000 == 0)
                        {
                            job.getLogger().info("processed " + count + " variants.  successful: " + successfulIntervals + ", unsuccessful: " + failedIntervals);
                        }
                    }
                }
            }
        }

        //TODO: sort resulting file
        job.getLogger().info("liftover complete.  successful variants: " + successfulIntervals + ", unsuccessful: " + failedIntervals);
    }

    private byte[] simpleReverseComplement(byte[] bases)
    {
        byte[] rcbases = new byte[bases.length];

        for (int i = 0; i < bases.length; i++) {
            rcbases[i] = simpleComplement(bases[bases.length - 1 - i]);
        }

        return rcbases;
    }

    private byte simpleComplement(byte base)
    {
        switch (base) {
            case 'A':
            case 'a':
                return 'T';
            case 'C':
            case 'c':
                return 'G';
            case 'G':
            case 'g':
                return 'C';
            case 'T':
            case 't':
                return 'A';
            default:
                return base;
        }
    }

    private VariantContext reverseComplement(VariantContext vc)
    {
        // create a mapping from original allele to reverse complemented allele
        HashMap<Allele, Allele> alleleMap = new HashMap<>(vc.getAlleles().size());
        for ( final Allele originalAllele : vc.getAlleles() )
        {
            Allele newAllele;
            if ( originalAllele.isNoCall() )
                newAllele = originalAllele;
            else
                newAllele = Allele.create(simpleReverseComplement(originalAllele.getBases()), originalAllele.isReference());
            alleleMap.put(originalAllele, newAllele);
        }

        // create new Genotype objects
        GenotypesContext newGenotypes = GenotypesContext.create(vc.getNSamples());
        for ( final Genotype genotype : vc.getGenotypes() )
        {
            List<Allele> newAlleles = new ArrayList<>();
            for ( final Allele allele : genotype.getAlleles() )
            {
                Allele newAllele = alleleMap.get(allele);
                if ( newAllele == null )
                    newAllele = Allele.NO_CALL;
                newAlleles.add(newAllele);
            }
            newGenotypes.add(new GenotypeBuilder(genotype).alleles(newAlleles).make());
        }

        return new VariantContextBuilder(vc).alleles(alleleMap.values()).genotypes(newGenotypes).make();
    }

    public void liftOverBed(File chain, File input, File output, File unmappedOutput, PipelineJob job, double pct) throws IOException
    {
        LiftOver lo = new LiftOver(chain);
        try (CSVWriter writer = new CSVWriter(new FileWriter(output), '\t', CSVWriter.NO_QUOTE_CHARACTER);CSVWriter unmappedWriter = new CSVWriter(new FileWriter(unmappedOutput), '\t', CSVWriter.NO_QUOTE_CHARACTER))
        {
            try (FeatureReader<BEDFeature> reader = AbstractFeatureReader.getFeatureReader(input.getAbsolutePath(), new BEDCodec()))
            {
                try (CloseableTribbleIterator<BEDFeature> i = reader.iterator())
                {
                    while (i.hasNext())
                    {
                        FullBEDFeature f = (FullBEDFeature)i.next();

                        Interval iv = new Interval(f.getChr(), f.getStart(), f.getEnd(), f.getStrand() == Strand.POSITIVE, StringUtils.isEmpty(f.getName()) ? null : f.getName());
                        Interval lifted = lo.liftOver(iv, pct);

                        if (lifted != null)
                        {
                            writer.writeNext(new String[]{f.getChr(), String.valueOf(lifted.getStart() - 1), String.valueOf(f.getEnd()), f.getName(), String.valueOf(f.getScore()), (lifted.isNegativeStrand() ? "-" : lifted.isPositiveStrand() ? "+" : null)});
                        }
                        else
                        {
                            unmappedWriter.writeNext(new String[]{f.getChr(), String.valueOf(f.getStart() - 1), String.valueOf(f.getEnd()), f.getName(), String.valueOf(f.getScore()), (f.getStrand() == Strand.NEGATIVE ? "-" : f.getStrand() == Strand.POSITIVE ? "+" : null)});
                        }
                    }
                }
            }
        }

        //TODO: append to header:
//        ##INFO=<ID=OriginalChr,Number=1,Type=String,Description="OriginalChr">
//        ##INFO=<ID=OriginalGenome,Number=1,Type=String,Description="OriginalGenome">
//        ##INFO=<ID=OriginalStart,Number=1,Type=String,Description="OriginalStart">

        //TODO: sort resulting file
    }
}
