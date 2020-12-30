package org.labkey.sequenceanalysis;

import htsjdk.samtools.util.FileExtensions;
import htsjdk.tribble.Tribble;
import htsjdk.tribble.index.Index;
import htsjdk.tribble.index.IndexFactory;
import htsjdk.variant.vcf.VCFCodec;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.laboratory.NavItem;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.resource.FileResource;
import org.labkey.api.resource.Resource;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.sequenceanalysis.GenomeTrigger;
import org.labkey.api.sequenceanalysis.PedigreeRecord;
import org.labkey.api.sequenceanalysis.ReferenceLibraryHelper;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.SequenceDataProvider;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.util.FileType;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Path;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.sequenceanalysis.pipeline.ProcessVariantsHandler;
import org.labkey.sequenceanalysis.pipeline.ReferenceGenomeImpl;
import org.labkey.sequenceanalysis.pipeline.ReferenceLibraryPipelineJob;
import org.labkey.sequenceanalysis.pipeline.SequenceTaskHelper;
import org.labkey.sequenceanalysis.run.util.BgzipRunner;
import org.labkey.sequenceanalysis.run.util.FastaIndexer;
import org.labkey.sequenceanalysis.run.util.GxfSorter;
import org.labkey.sequenceanalysis.run.util.IndexFeatureFileWrapper;
import org.labkey.sequenceanalysis.run.util.TabixRunner;
import org.labkey.sequenceanalysis.util.FastqMerger;
import org.labkey.sequenceanalysis.util.ReferenceLibraryHelperImpl;
import org.labkey.sequenceanalysis.util.SequenceUtil;

import java.io.File;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: bimber
 * Date: 10/27/13
 * Time: 9:21 PM
 */
public class SequenceAnalysisServiceImpl extends SequenceAnalysisService
{
    private static SequenceAnalysisServiceImpl _instance = new SequenceAnalysisServiceImpl();

    private final Logger _log = LogManager.getLogger(SequenceAnalysisServiceImpl.class);
    private Set<GenomeTrigger> _genomeTriggers = new HashSet<>();
    private Set<SequenceOutputHandler<SequenceOutputHandler.SequenceOutputProcessor>> _fileHandlers = new HashSet<>();
    private Set<SequenceOutputHandler<SequenceOutputHandler.SequenceReadsetProcessor>> _readsetHandlers = new HashSet<>();
    private Map<String, SequenceDataProvider> _dataProviders = new HashMap<>();

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
        return new ReferenceLibraryHelperImpl(refFasta, _log);
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
    public void registerFileHandler(SequenceOutputHandler<SequenceOutputHandler.SequenceOutputProcessor> handler)
    {
        _fileHandlers.add(handler);
    }

    @Override
    public void registerReadsetHandler(SequenceOutputHandler<SequenceOutputHandler.SequenceReadsetProcessor> handler)
    {
        _readsetHandlers.add(handler);
    }

//    @Override
//    public File createTabixIndex(File input, @Nullable Logger log) throws PipelineJobException
//    {
//        return new TabixRunner(log).execute(input);
//    }

    public Set<SequenceOutputHandler> getFileHandlers(Container c, SequenceOutputHandler.TYPE type)
    {
        Set<SequenceOutputHandler> ret = new HashSet<>();
        for (SequenceOutputHandler h : getFileHandlers(type))
        {
            if (c.getActiveModules().contains(h.getOwningModule()))
            {
                ret.add(h);
            }
        }
        return Collections.unmodifiableSet(ret);
    }

    public Set<SequenceOutputHandler> getFileHandlers(SequenceOutputHandler.TYPE type)
    {
        return Collections.unmodifiableSet(type == SequenceOutputHandler.TYPE.OutputFile ? _fileHandlers : _readsetHandlers);
    }

    @Override
    public void registerDataProvider(SequenceDataProvider p)
    {
        if (_dataProviders.containsKey(p.getKey()))
        {
            _log.error("A SequenceDataProvider with the name: " + p.getName() + " has already been registered");
        }

        _dataProviders.put(p.getName(), p);
    }

    public List<NavItem> getNavItems(Container c, User u, SequenceDataProvider.SequenceNavItemCategory category)
    {
        List<NavItem> ret = new ArrayList<>();
        for (SequenceDataProvider p : _dataProviders.values())
        {
            ret.addAll(p.getSequenceNavItems(c, u, category));
        }

        return ret;
    }

    public ReadDataImpl getReadData(int rowId, User u)
    {
        TableInfo ti = SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_READ_DATA);
        SimpleFilter filter = new SimpleFilter(FieldKey.fromString("rowid"), rowId);

        ReadDataImpl model = new TableSelector(ti, filter, null).getObject(ReadDataImpl.class);
        if (model == null)
        {
            return null;
        }

        Container c = ContainerManager.getForId(model.getContainer());
        if (!c.hasPermission(u, ReadPermission.class))
        {
            throw new UnauthorizedException("Cannot read data in container: " + c.getPath());
        }

        return model;
    }

    public SequenceReadsetImpl getReadset(int readsetId, User u)
    {
        TableInfo ti = SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_READSETS);
        SimpleFilter filter = new SimpleFilter(FieldKey.fromString("rowid"), readsetId);

        SequenceReadsetImpl model = new TableSelector(ti, filter, null).getObject(SequenceReadsetImpl.class);
        if (model == null)
        {
            return null;
        }

        Container c = ContainerManager.getForId(model.getContainer());
        if (!c.hasPermission(u, ReadPermission.class))
        {
            throw new UnauthorizedException("Cannot read data in container: " + c.getPath());
        }

        return model;
    }

    @Override
    public ReferenceGenomeImpl getReferenceGenome(int genomeId, User u) throws PipelineJobException
    {
        TableInfo ti = SequenceAnalysisSchema.getInstance().getSchema().getTable(SequenceAnalysisSchema.TABLE_REF_LIBRARIES);
        SimpleFilter filter = new SimpleFilter(FieldKey.fromString("rowid"), genomeId);

        Map<String, Object> map = new TableSelector(ti, PageFlowUtil.set("rowid", "fasta_file", "container", "name"), filter, null).getMap();
        if (map == null)
        {
            return null;
        }

        Container c = ContainerManager.getForId((String)map.get("container"));
        if (!c.hasPermission(u, ReadPermission.class))
        {
            throw new UnauthorizedException("Cannot read data in container: " + c.getPath());
        }

        ExpData d = ExperimentService.get().getExpData((Integer)map.get("fasta_file"));
        if (d.getFile() == null)
        {
            throw new PipelineJobException("No FASTA file found for genome: " + genomeId);
        }

        return new ReferenceGenomeImpl(d.getFile(), d, genomeId, (String)map.get("name"));
    }

    @Override
    public File ensureVcfIndex(File vcf, Logger log) throws IOException
    {
        return ensureVcfIndex(vcf, log, false);
    }

    @Override
    public File ensureVcfIndex(File vcf, Logger log, boolean forceRecreate) throws IOException
    {
        if (vcf == null || !vcf.exists())
        {
            throw new IOException("VCF does not exist: " + (vcf == null ? null : vcf.getPath()));
        }

        try
        {
            FileType gz = new FileType(".gz");
            File expected = new File(vcf.getPath() + Tribble.STANDARD_INDEX_EXTENSION);
            File tbi = new File(vcf.getPath() + ".tbi");

            if (!forceRecreate && expected.exists())
            {
                return expected;
            }
            else if  (!forceRecreate && tbi.exists())
            {
                return tbi;
            }
            else
            {
                log.info("creating vcf index: " + vcf.getPath());
                //note: there is a bug in htsjdk's index creation with gz inputs
                if (gz.isType(vcf) && !SystemUtils.IS_OS_WINDOWS)
                {
                    TabixRunner r = new TabixRunner(log);
                    r.execute(vcf);

                    return tbi;
                }
                else
                {
                    Index idx = IndexFactory.createDynamicIndex(vcf, new VCFCodec());
                    idx.writeBasedOnFeatureFile(vcf);

                    return expected;
                }
            }
        }
        catch (PipelineJobException e)
        {
            throw new IOException(e);
        }
    }

    public File bgzipFile(File input, Logger log) throws PipelineJobException
    {
        return new BgzipRunner(log).execute(input);
    }

    @Override
    public void ensureFastaIndex(File fasta, Logger log) throws PipelineJobException
    {
        FastaIndexer indexer = new FastaIndexer(log);
        File index = indexer.getExpectedIndexName(fasta);
        if (!index.exists())
        {
            log.info("creating FASTA index: " + fasta.getName());
            indexer.execute(fasta);
        }
    }

    @Override
    public String getUnzippedBaseName(String filename)
    {
        return SequenceTaskHelper.getUnzippedBaseName(filename);
    }

    @Override
    public Integer getExpRunIdForJob(PipelineJob job, boolean throwUnlessFound) throws PipelineJobException
    {
        return SequenceTaskHelper.getExpRunIdForJob(job, throwUnlessFound);
    }

    @Override
    public List<PedigreeRecord> generatePedigree(Collection<String> sampleNames, Container c, User u)
    {
        final List<PedigreeRecord> pedigreeRecords = new ArrayList<>();

        TableInfo subjectTable = QueryService.get().getUserSchema(u, (c.isWorkbook() ? c.getParent() : c), "laboratory").getTable("subjects");
        TableSelector ts = new TableSelector(subjectTable, PageFlowUtil.set("subjectname", "mother", "father", "gender"), new SimpleFilter(FieldKey.fromString("subjectname"), sampleNames, CompareType.IN), null);
        ts.forEach(new Selector.ForEachBlock<ResultSet>()
        {
            @Override
            public void exec(ResultSet rs) throws SQLException
            {
                PedigreeRecord pedigree = new PedigreeRecord();
                pedigree.setSubjectName(rs.getString("subjectname"));
                pedigree.setFather(rs.getString("father"));
                pedigree.setMother(rs.getString("mother"));
                pedigree.setGender(rs.getString("gender"));
                if (!StringUtils.isEmpty(pedigree.getSubjectName()))
                    pedigreeRecords.add(pedigree);
            }
        });

        //insert record for any missing parents:
        Set<String> distinctSubjects = new HashSet<>();
        for (PedigreeRecord p : pedigreeRecords)
        {
            distinctSubjects.add(p.getSubjectName());
        }

        List<PedigreeRecord> newRecords = new ArrayList<>();
        for (PedigreeRecord p : pedigreeRecords)
        {
            appendParents(distinctSubjects, p, newRecords);
        }
        pedigreeRecords.addAll(newRecords);

        //if ID only has one parent, add a dummy ID
        List<PedigreeRecord> toAdd = new ArrayList<>();
        for (PedigreeRecord pd : pedigreeRecords)
        {
            if (StringUtils.isEmpty(pd.getFather()) && !StringUtils.isEmpty(pd.getMother()))
            {
                pd.setFather("xf" + pd.getSubjectName());
                pd.setPlaceholderFather(true);
                PedigreeRecord pr = new PedigreeRecord();
                pr.setSubjectName(pd.getFather());
                pr.setGender("m");
                toAdd.add(pr);
            }
            else if (!StringUtils.isEmpty(pd.getFather()) && StringUtils.isEmpty(pd.getMother()))
            {
                pd.setMother("xm" + pd.getSubjectName());
                pd.setPlaceholderMother(true);
                PedigreeRecord pr = new PedigreeRecord();
                pr.setSubjectName(pd.getMother());
                pr.setGender("f");
                toAdd.add(pr);
            }
        }

        if (!toAdd.isEmpty())
        {
            //job.getLogger().info("adding " + toAdd.size() + " subjects to handle IDs with only one parent known");
            pedigreeRecords.addAll(toAdd);
        }
        
        pedigreeRecords.sort((o1, o2) ->
        {
            boolean o1ParentOfO2 = o1.getSubjectName().equals(o2.getFather()) || o1.getSubjectName().equals(o2.getMother());
            boolean o2ParentOfO1 = o2.getSubjectName().equals(o1.getFather()) || o2.getSubjectName().equals(o1.getMother());

            if (o1ParentOfO2 && o2ParentOfO1)
                throw new IllegalArgumentException("Pedigree records are both parents of one another: " + o1.getSubjectName() + "/" + o2.getSubjectName());
            else if (o1ParentOfO2)
                return -1;
            else if (o2ParentOfO1)
                return 1;
            else if ((o1.getTotalParents(true) == 0 && o2.getTotalParents(true) != 0))
                return -1;
            else if ((o1.getTotalParents(true) != 0 && o2.getTotalParents(true) == 0))
                return 1;

            return o1.getSubjectName().compareTo(o2.getSubjectName());
        });
        
        return pedigreeRecords;
    }
    
    private void appendParents(Set<String> distinctSubjects, PedigreeRecord p, List<PedigreeRecord> newRecords)
    {
        if (!StringUtils.isEmpty(p.getFather()) && !distinctSubjects.contains(p.getFather()))
        {
            PedigreeRecord pr = new PedigreeRecord();
            pr.setSubjectName(p.getFather());
            pr.setGender("m");

            newRecords.add(pr);
            distinctSubjects.add(p.getFather());
            appendParents(distinctSubjects, pr, newRecords);
        }

        if (!StringUtils.isEmpty(p.getMother()) && !distinctSubjects.contains(p.getMother()))
        {
            PedigreeRecord pr = new PedigreeRecord();
            pr.setSubjectName(p.getMother());
            pr.setGender("f");

            newRecords.add(pr);
            distinctSubjects.add(p.getMother());
            appendParents(distinctSubjects, pr, newRecords);
        }
    }

    @Override
    public String getVCFLineCount(File vcf, Logger log, boolean passOnly) throws PipelineJobException
    {
        return ProcessVariantsHandler.getVCFLineCount(vcf, log, passOnly);
    }

    @Override
    public String getScriptPath(String moduleName, String path) throws PipelineJobException
    {
        Module module = ModuleLoader.getInstance().getModule(moduleName);
        Resource script = module.getModuleResource(Path.parse(path));
        if (script == null || !script.exists())
            throw new PipelineJobException("Unable to find file: " + path + " in module: " + moduleName);

        File f = ((FileResource) script).getFile();
        if (!f.exists())
            throw new PipelineJobException("Unable to find file: " + f.getPath());

        return f.getPath();
    }

    @Override
    public String createReferenceLibrary(List<Integer> sequenceIds, Container c, User u, String name, String assemblyId, String description, boolean skipCacheIndexes, boolean skipTriggers) throws IOException
    {
        return createReferenceLibrary(sequenceIds, c, u, name, assemblyId, description, skipCacheIndexes, skipTriggers, null);
    }

    @Override
    public String createReferenceLibrary(List<Integer> sequenceIds, Container c, User u, String name, String assemblyId, String description, boolean skipCacheIndexes, boolean skipTriggers, Set<GenomeTrigger> extraTriggers) throws IOException
    {
        ReferenceLibraryPipelineJob job = SequenceAnalysisManager.get().createReferenceLibrary(sequenceIds, c, u, name, assemblyId, description, skipCacheIndexes, skipTriggers, null, extraTriggers);

        return job.getJobGUID();
    }

    @Override
    public File combineVcfs(List<File> files, File outputGz, ReferenceGenome genome, Logger log, boolean multiThreaded, @Nullable Integer compressionLevel) throws PipelineJobException
    {
        return SequenceUtil.combineVcfs(files, genome, outputGz, log, multiThreaded, compressionLevel);
    }

    @Override
    public void sortGxf(Logger log, File input, @Nullable File output) throws PipelineJobException
    {
        new GxfSorter(log).sortGxf(input, output);
    }

    @Override
    public void ensureFeatureFileIndex(File input, Logger log) throws PipelineJobException
    {
        if (SequenceUtil.FILETYPE.bed.getFileType().isType(input))
        {
            File expected = new File(input.getPath() + FileExtensions.TRIBBLE_INDEX);
            if (expected.exists())
            {
                log.debug("index exists: " + expected.getPath());
            }
            else
            {
                new IndexFeatureFileWrapper(log).ensureFeatureFileIndex(input);
            }
        }
        else if (SequenceUtil.FILETYPE.vcf.getFileType().isType(input))
        {
            try
            {
                ensureVcfIndex(input, log);
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }
        else
        {
            throw new IllegalArgumentException("Unexpected input file type, cannot index: " + input.getName());
        }
    }

    @Override
    public void mergeFastqFiles(File output, List<File> inputs, Logger log) throws PipelineJobException
    {
        FastqMerger merger = new FastqMerger(log);
        merger.mergeFiles(output, inputs);
    }
}
