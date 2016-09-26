package org.labkey.sequenceanalysis;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.sequenceanalysis.RefNtSequenceModel;
import org.labkey.api.util.DefaultSystemMaintenanceTask;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.SystemMaintenance.MaintenanceTask;
import org.labkey.sequenceanalysis.model.AnalysisModelImpl;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by bimber on 9/15/2014.
 */
public class SequenceAnalysisMaintenanceTask implements MaintenanceTask
{
    private static Logger _log = Logger.getLogger(SequenceAnalysisMaintenanceTask.class);

    public SequenceAnalysisMaintenanceTask()
    {

    }

    @Override
    public String getDescription()
    {
        return "Delete SequenceAnalysis Artifacts";
    }

    @Override
    public String getName()
    {
        return "DeleteSequenceAnalysisArtifacts";
    }

    @Override
    public void run()
    {
        //delete sequence text files and library artifacts not associated with a DB record
        try
        {
            processContainer(ContainerManager.getRoot());
            verifySequenceDataPresent();
        }
        catch (Exception e)
        {
            _log.error(e.getMessage(), e);
        }
    }

    private void verifySequenceDataPresent()
    {
        TableInfo ti = SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_READ_DATA);
        TableSelector ts = new TableSelector(ti);
        List<ReadDataImpl> readDatas = ts.getArrayList(ReadDataImpl.class);
        for (ReadDataImpl rd : readDatas)
        {
            if (rd.getFileId1() != null)
            {
                ExpData d = ExperimentService.get().getExpData(rd.getFileId1());
                Container c = ContainerManager.getForId(rd.getContainer());
                if (d == null || d.getFile() == null)
                {
                    _log.error("Unable to find file associated with ReadData: " + rd.getRowid() + ", " + rd.getFileId1() + " for container: " + (c == null ? rd.getContainer() : c.getPath()));
                }
                else if (!d.getFile().exists())
                {
                    _log.error("Unable to find file associated with ReadData: " + rd.getRowid() + ", " + rd.getFileId1() + ", " + d.getFile().getPath() + " for container: " + (c == null ? rd.getContainer() : c.getPath()));
                }
            }

            if (rd.getFileId2() != null)
            {
                ExpData d = ExperimentService.get().getExpData(rd.getFileId2());
                Container c = ContainerManager.getForId(rd.getContainer());
                if (d == null || d.getFile() == null)
                {
                    _log.error("Unable to find file associated with ReadData: " + rd.getRowid() + ", " + rd.getFileId2() + " for container: " + (c == null ? rd.getContainer() : c.getPath()));
                }
                else if (!d.getFile().exists())
                {
                    _log.error("Unable to find file associated with ReadData: " + rd.getRowid() + ", " + rd.getFileId2() + ", " + d.getFile().getPath() + " for container: " + (c == null ? rd.getContainer() : c.getPath()));
                }
            }
        }

        //also check analyses
        TableInfo analysesTable = SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_ANALYSES);
        TableSelector tsAnalyses = new TableSelector(analysesTable);
        List<AnalysisModelImpl> analyses = tsAnalyses.getArrayList(AnalysisModelImpl.class);
        for (AnalysisModelImpl m : analyses)
        {
            if (m.getAlignmentFile() != null)
            {
                ExpData d = m.getAlignmentData();
                Container c = ContainerManager.getForId(m.getContainer());
                if (d == null || d.getFile() == null)
                {
                    _log.error("Unable to find file associated with analysis: " + m.getAnalysisId() + ", " + m.getAlignmentFile() + " for container: " + (c == null ? m.getContainer() : c.getPath()));
                }
                else if (!d.getFile().exists())
                {
                    _log.error("Unable to find file associated with analysis: " + m.getAnalysisId() + ", " + m.getAlignmentFile() + ", " + d.getFile().getPath() + " for container: " + (c == null ? m.getContainer() : c.getPath()));
                }
            }
        }
    }

    private void processContainer(Container c) throws IOException
    {
        PipeRoot root = PipelineService.get().getPipelineRootSetting(c);
        if (root != null)
        {
            //first sequences
            File sequenceDir = new File(root.getRootPath(), ".sequences");
            TableInfo tableRefNtSequences = SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_REF_NT_SEQUENCES);
            TableSelector ntTs = new TableSelector(tableRefNtSequences, new SimpleFilter(FieldKey.fromString("container"), c.getId()), null);
            final Set<String> expectedSequences = new HashSet<>();
            for (RefNtSequenceModel m : ntTs.getArrayList(RefNtSequenceModel.class))
            {
                if (m.getSequenceFile() == null || m.getSequenceFile() == 0)
                {
                    _log.error("sequence record lacks a sequence file Id: " + m.getRowid());
                    continue;
                }

                ExpData d = ExperimentService.get().getExpData(m.getSequenceFile());
                if (d == null || d.getFile() == null)
                {
                    _log.error("file was null for sequence: " + m.getRowid());
                    continue;
                }

                if (!d.getFile().exists())
                {
                    _log.error("expected sequence file does not exist for sequence: " + m.getRowid() + " " + m.getName() + ", expected: " + d.getFile().getPath());
                    continue;
                }

                if (d.getFile().getAbsolutePath().toLowerCase().startsWith(sequenceDir.getAbsolutePath().toLowerCase()))
                {
                    expectedSequences.add(d.getFile().getName());
                }
            }

            if (sequenceDir.exists())
            {
                for (File child : sequenceDir.listFiles())
                {
                    if (!expectedSequences.contains(child.getName()))
                    {
                        deleteFile(child);
                    }
                }
            }

            //then libraries
            File libraryDir = SequenceAnalysisManager.get().getReferenceLibraryDir(c);
            if (libraryDir != null && libraryDir.exists())
            {
                TableInfo ti = SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_REF_LIBRARIES);
                TableSelector ts = new TableSelector(ti, Collections.singleton("rowid"), new SimpleFilter(FieldKey.fromString("container"), c.getId()), null);
                Set<String> expectedLibraries = new HashSet<>();
                for (Integer rowId : ts.getArrayList(Integer.class))
                {
                    expectedLibraries.add(rowId.toString());
                }

                for (File child : libraryDir.listFiles())
                {
                    if ("log".equals(FileUtil.getExtension(child)))
                    {
                        continue;  //always ignore log files
                    }

                    if (!expectedLibraries.contains(child.getName()))
                    {
                        deleteFile(child);
                    }
                    else
                    {
                        //inspect within library
                        List<String> expectedChildren = new ArrayList<>();
                        Integer fastaId = new TableSelector(SequenceAnalysisSchema.getInstance().getSchema().getTable(SequenceAnalysisSchema.TABLE_REF_LIBRARIES), PageFlowUtil.set("fasta_file")).getObject(Integer.parseInt(child.getName()), Integer.class);
                        if (fastaId == null)
                        {
                            _log.error("Unable to find FASTA ExpData in DB matching jbrowse directory: " + child.getPath());
                            continue;
                        }

                        ExpData fastaData = ExperimentService.get().getExpData(fastaId);
                        File fasta = fastaData.getFile();
                        if (!fasta.exists())
                        {
                            _log.error("expected fasta file does not exist: " + fasta.getPath());
                        }

                        expectedChildren.add(fasta.getName());
                        expectedChildren.add(fasta.getName() + ".fai");
                        expectedChildren.add(FileUtil.getBaseName(fasta.getName()) + ".idKey.txt");
                        expectedChildren.add(FileUtil.getBaseName(fasta.getName()) + ".dict");
                        expectedChildren.add("alignerIndexes");
                        expectedChildren.add("tracks");
                        expectedChildren.add("chainFiles");

                        for (String fileName : child.list())
                        {
                            if (!expectedChildren.contains(fileName))
                            {
                                deleteFile(new File(child, fileName));
                            }
                        }

                        Integer libraryId = Integer.parseInt(child.getName());

                        //check/verify tracks
                        File trackDir = new File(child, "tracks");
                        if (trackDir.exists())
                        {
                            Set<String> expectedTracks = new HashSet<>();
                            TableInfo tracksTable = SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_LIBRARY_TRACKS);
                            TableSelector tracksTs = new TableSelector(tracksTable, Collections.singleton("fileid"), new SimpleFilter(FieldKey.fromString("library_id"), libraryId), null);
                            for (Integer dataId : tracksTs.getArrayList(Integer.class))
                            {
                                ExpData trackData = ExperimentService.get().getExpData(dataId);
                                if (trackData != null && trackData.getFile() != null)
                                {
                                    expectedTracks.add(trackData.getFile().getName());
                                    if (!trackData.getFile().exists())
                                    {
                                        _log.error("expected track file does not exist: " + trackData.getFile().getPath());
                                    }

                                    expectedTracks.addAll(getAssociatedFiles(trackData.getFile(), true));
                                }
                                else
                                {
                                    _log.warn("unable to find ExpData for track with dataId: " + dataId);
                                }
                            }

                            for (File f : trackDir.listFiles())
                            {
                                if (!expectedTracks.contains(f.getName()))
                                {
                                    deleteFile(f);
                                }
                            }
                        }

                        //check/verify chainFiles
                        File chainDir = new File(child, "chainFiles");
                        if (chainDir.exists())
                        {
                            Set<String> expectedChains = new HashSet<>();
                            TableInfo chainTable = SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_CHAIN_FILES);
                            TableSelector chainTs = new TableSelector(chainTable, Collections.singleton("chainFile"), new SimpleFilter(FieldKey.fromString("genomeId1"), libraryId), null);
                            for (Integer dataId : chainTs.getArrayList(Integer.class))
                            {
                                ExpData chainData = ExperimentService.get().getExpData(dataId);
                                if (chainData != null && chainData.getFile() != null)
                                {
                                    expectedChains.add(chainData.getFile().getName());
                                    if (!chainData.getFile().exists())
                                    {
                                        _log.error("expected chain file does not exist: " + chainData.getFile().getPath());
                                    }
                                }
                            }

                            for (File f : chainDir.listFiles())
                            {
                                if (!expectedChains.contains(f.getName()))
                                {
                                    deleteFile(f);
                                }
                            }
                        }
                    }
                }
            }

            //finally outputfiles
            File sequenceOutputsDir = new File(root.getRootPath(), "sequenceOutputs");
            if (sequenceOutputsDir.exists())
            {
                TableInfo ti = SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_OUTPUTFILES);
                TableSelector ts = new TableSelector(ti, Collections.singleton("dataid"), new SimpleFilter(FieldKey.fromString("container"), c.getId()), null);
                Set<String> expectedFileNames = new HashSet<>();
                for (Integer dataId : ts.getArrayList(Integer.class))
                {
                    ExpData d = ExperimentService.get().getExpData(dataId);
                    if (d != null)
                    {
                        expectedFileNames.add(d.getFile().getName());
                        expectedFileNames.addAll(getAssociatedFiles(d.getFile(), true));

                        if (!d.getFile().exists())
                        {
                            _log.error("expected output file does not exist: " + d.getFile().getPath());
                        }
                    }
                }

                for (File child : sequenceOutputsDir.listFiles())
                {
                    if (!expectedFileNames.contains(child.getName()))
                    {
                        deleteFile(child);
                    }
                }
            }
        }

        for (Container child : c.getChildren())
        {
            processContainer(child);
        }
    }

    private void deleteFile(File f) throws IOException
    {
        _log.info("deleting sequence file: " + f.getPath());
        if (f.isDirectory())
        {
            FileUtils.deleteDirectory(f);
        }
        else
        {
            f.delete();
        }
    }

    private static FileType _bamFileType = new FileType("bam");
    private static FileType _vcfFileType = new FileType("vcf", FileType.gzSupportLevel.SUPPORT_GZ);
    private static FileType _bedFileType = new FileType("bed");

    /**
     * This is intended to return any files associated with an input, which is primarily designed to pick up index files
     */
    public static List<String> getAssociatedFiles(File f, boolean includeGz)
    {
        List<String> ret = new ArrayList<>();

        //TODO: this is sort of a hack.  certain file types can get gzipped or indexed, so add those variants:
        if (_bamFileType.isType(f))
        {
            ret.add(f.getName() + ".bai");
        }
        else if (_vcfFileType.isType(f))
        {
            ret.add(f.getName() + ".tbi");
            ret.add(f.getName() + ".idx");
            ret.add(f.getName() + ".bgz");

            if (includeGz)
            {
                ret.add(f.getName() + ".gz");
                ret.add(f.getName() + ".gz.tbi");
                ret.add(f.getName() + ".gz.idx");
            }
        }
        else if (_bedFileType.isType(f))
        {
            ret.add(f.getName() + ".idx");
        }

        return ret;
    }
}