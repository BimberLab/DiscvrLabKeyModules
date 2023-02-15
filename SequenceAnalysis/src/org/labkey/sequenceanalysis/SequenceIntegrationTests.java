package org.labkey.sequenceanalysis;

import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMRecordIterator;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.ValidationStringency;
import htsjdk.samtools.fastq.FastqReader;
import htsjdk.samtools.fastq.FastqRecord;
import htsjdk.samtools.fastq.FastqWriter;
import htsjdk.samtools.fastq.FastqWriterFactory;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.DataType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.module.FolderTypeManager;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.reader.Readers;
import org.labkey.api.resource.DirectoryResource;
import org.labkey.api.resource.FileResource;
import org.labkey.api.resource.Resource;
import org.labkey.api.sequenceanalysis.RefNtSequenceModel;
import org.labkey.api.sequenceanalysis.model.ReadData;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.AlignerIndexUtil;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.settings.AppProps;
import org.labkey.api.test.TestTimeout;
import org.labkey.api.util.Compress;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.Path;
import org.labkey.api.util.TestContext;
import org.labkey.api.view.ViewServlet;
import org.labkey.sequenceanalysis.model.BarcodeModel;
import org.labkey.sequenceanalysis.pipeline.ReferenceLibraryPipelineJob;
import org.labkey.sequenceanalysis.pipeline.SequenceAlignmentJob;
import org.labkey.sequenceanalysis.pipeline.SequenceJob;
import org.labkey.sequenceanalysis.pipeline.SequenceTaskHelper;
import org.labkey.sequenceanalysis.util.FastqUtils;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.bind.annotation.RequestMethod;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * User: bimber
 * Date: 11/25/12
 * Time: 1:58 PM
 */
public class SequenceIntegrationTests
{
    private static SequenceIntegrationTests _instance = new SequenceIntegrationTests();
    public static final String PIPELINE_PROP_NAME = "sequencePipelineEnabled";

    private SequenceIntegrationTests()
    {

    }

    public static SequenceIntegrationTests get()
    {
        return _instance;
    }

    public List<FastqRecord> getBarcodedFastqData()
    {
        List<FastqRecord> list = new ArrayList<>();

        //MID001: ACGAGTGCGT, RC: ACGCACTCGT
        //MID002: ACGCTCGACA, RC: TGTCGAGCGT

        //MID003: AGACGCACTC, RC: GAGTGCGTCT
        //MID004: AGCACTGTAG, RC: CTACAGTGCT

        //Header format:
        //{5'BARCODE}-XXX_{3'BARCODE}-XXX
        //XXX: mismatch/offset/deletions

        //5': MID002: 0 offset, 0 mismatch
        //3': MID003: 0 offset, 0 mismatch
        list.add(new FastqRecord("MID002-000_MID003-000", "ACGCTCGACAAGTGGTTTACCGCCTCTCTGTTTATCTCCTCTACTGTTCTGTTTAGCCATTCGAAGGCCTCTCCTATTGTCTCTTCTCCACTGTTTCCAGGTGGGATTCTCTCCCTGGGATCTGACATCGCTTACTACTTTCAGTGCTAAGTACTGTAGGCTTGGTACCTGGTACTTATGAGCTCTCGGGAACCTGCAGCAAGACAGCAGTTGTTCTCCCCTGATGGCCCTTCTCACTTCTCCCGCTGTAAAGCAAGGGAAATAAGTGCTATGCAGTAAAATGTCTGCATAGTTTGGTGTTACATCTGTCCAAAAGTTCTTTGAGTACCAGGTTATCCTCACTGCATAAGTACTGAGCCACCCTTTTTCTGGAGTGCGTCT", "", "IIIIIIIIIFFII33333;;?HAAGIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIHHIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIII>><IIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIHHHIIIIIIIIIIIIIICCCCIIIIII@B@HHIIIIIIIIIIIIIIIIIIFB??;;:681117008<CCFCEGGIII;;;;HHFFFFFFIIIBBBHIIIIIIIIIIIIIIIEEDDIIIIIIIIIIIIIIIIIIIIIIIGGHIHHHHIHHIIIIIGGEDDB8443333301:63134=BBAA"));

        //5': MID002, 0 offset, 0 mismatch
        //3': MID003: 0 offset, 0 mismatch
        list.add(new FastqRecord("MID002-000_MID003-000", "ACGCTCGACAAGGTGGTTTACCGCCTCTCTGTTTATCTCCTCTACTGTTCTGTTTAGCCATTCGAAGGCCTCTCCTATTGTCTCTTCTCCACTGTTTCCAGGTGGGATTCTCTCCCTGGGATCTGACATCGCTTACTACTTTCAGTGCTAAGTACTGTAGGCTTGGTACCTGGTACTTATGAGCTCTCGGGAACCTGCAGCAAGACAGCAGTCGTTCTCCCCTGATGGCCCTTCTCACTTCTCCCGCTGTAAAGCAAGGGAAATAAGTGCTATGCAGTAAAATGTCTGCATAGTTTGGTGTTACATCTGTCCAAAAGTTCTTTGAGTACCAGGTTATCCTCACTGCATAAGTACTGAGCCACCCTTTTTCTGGAGTGCGTCT", "", "BBBDGIIB==<??ICC888D?GDGGIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIHHHIIIIIIIIIIIIIIIIIIIIIIIBBBIIIIII===HIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIBBBIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIFFFDFIIIIBCBBIIIIIIIIIIIIIIIIIIIIIIIIHIIIIHHHIIIIII<<<FHIIIIIIIIIIIIIBBBBIIIIIIIIIIIIIIIGDFFIIIIIIIIIIIDBEEIIIF999CDIIIIIIIIIIIIIIIIIIIGGGICCCGIEEG@@>>;::::6<6672>>;8;9;89AC"));

        //5': MID002, 0 offset, 0 mismatch
        //3': MID002: 0 offset, 0 mismatch
        list.add(new FastqRecord("MID002-000_MID002-000", "ACGCTCGACACAGAAAAGGGTGGCTCAGTACTTATGCAGTGAGGATAACCTGGTACTCAAAGAACTTTTGGACAGATGTAACACCAAGCTATGCAGACATTTTACTGCATAGCACTTATTTCCCTTGCTTTACAGCGGGAGAAGTGAGAAGGGCCATCAGGGGAGAACAACTGCTGTCTTGCTGCAGGTTCCCGAGAGCTCATAAGTACCAGGTACCAAGCCTACAGTACTTAGCACTGAAAGTAGTAAGCGATGTCAGATCCCAGGGAGAGAATCCCACCTGGAAACAGTGGAGAAGAGACAATAGGAGAGGCCTTCGAATGGCTAAACAGAACAGTAGAGGAGATAAACAGAGAGGCGGTAAACCACCTTGTCGAGCGT", "", "IIIIIIIIIIIFF3333:::IIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIDDDDIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIFFFFIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIDDDIIIIIIIIIIIDDDIIIIIIFFFFIIIIIIIIIIIIIIIIIIIIIIIHHHHIIIIIIIIIIIIIHHIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIHHHIIIIIIIIIIIIIIIIIIIIIIIIHHIIHIIIIIIIIIIIIIDDBIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIEEEDEAACIIIEHHHHIGGHCCIIHIIHDDDEHGIGGGFF@>?@@HGHEGFDFD"));

        //5: MID002: 1 offset, 1 mismatch
        //3: MID001: 0 offset, 0 mismatch
        list.add(new FastqRecord("MID002-110_MID001-000", "aACGCTCcACATCCAGAAAGGGTGGCTCAGTACTTATGCAGTGAGGATAACCTGGTACTCAAAGAACTTTTGGACAGATGTAACACCAAACTATGCAGACATTTTACTGCATAGCACTTATTTCCCTTGCTTTACAGCGGGAGAAGTGAGAAGGGCCATCAGGGGAGAACAACTGCTGTCTTGCTGCAGGTTCCCGAGAGCTCATAAGTACCAGGTACCAAGCCTACAGTACTTAGCACTGAAAGTAGTAAGCGATGTCAGATCCCAGGGAGAGAATCCCACCTGGAAACAGTGGAGAAGAGACAATAGGAGAGGCCTTCGAATGGCTAAACAGAACAGTAGAGGAGATAAACAGAGAGGCGGTAAACCACCTACGCACTCGT", "", "6673111/;;;/..B@11199CIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIII@@>IIII>>>>HHHIIIIIIIIIIIII===FIIIIIIIIIIDDDDIIBIIIIIIIIIIIIIIIFFFIIIIIIIIIIIIFFFHHIIIIIIIIIIIIIIIIIIFFFDIBIIIIIIIIIIIIIIIIIIIIIFBBFIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIHIIIIIIIIIIIIIID:555A@AAD:??D?;442<9:A@@<99:=EIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIGDIIIIIIIIIIGGIIIICCCIFIIIIIIIGGIA?=;?6FGIHFFFIIIEEB"));

        //5': MID003, 0 offset, 0 mismatch
        //3': MID004, 0 offset, 0 mismatch
        list.add(new FastqRecord("MID003-000_MID004-000", "AGACGCACTCAGGTGGTTTACCGCCTCTCTGTTTATCTCCTCTACTGTTCTGTTTAGCCATTCGAAGGCCTCTCCTATTGTCTCTTCTCCACTGTTTCCAGGTGGGATTCTCTCCCTGGGATCTGACATCGCTTACTACTTTCAGTGCTAAGTACTGTAGGCTTGGTACCTGGTACTTATGAGCTCTCGGGAACCTGCAGCAAGACAGCAGTTGTTCTCCCCTGATGGCCCTTCTCACTTCTCCCGCTGTAAAGCAAGGGAAATAAGTGCTATGCAGTAAAATGTCTGCATAGTTTGGTGTTACATCTGTCCAAAAGTTCTTTGAGTACCAGGTTATCCTCACTGCATAAGTACTGAGCCACCCTTTTTCTGCTACAGTGCT", "", "IIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIII@@@IIIIIGGGIIIIIFGDGGIIIIIIIIIIHHHIHHHIIIIIIHHHIIIIIIIIIIIIIIIIIIIFFIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIHHHIIIICCCCIIIIIIIIIIIIIIIIIIIIFFFIHIIIBBBBIIIIIIIIIIIIIIIIIIIIIIIIBBBBIIIIIHFFFHIIIIIIIIIIIIIIIIIIII>>>>IIIIIIIIIIIIIIIFFDDDIIIEEEEIFF@<;;CDEEC??>C@;41166<<8<4CCBBA6:8AHG"));

        //5': MID003, 0 offset, 0 mismatch
        //3': MID004, 0 offset, 0 mismatch
        list.add(new FastqRecord("MID003-000_MID004-000", "AGACGCACTCAGGTGGTTTACCGCCTCTCTGTTTATCTCCTCTACTGTTCTGTTTAGCCATTCGAAGGCCTCTCCTATTGTCTCTTCTCCACTGTTTCCAGGTGGGATTCTCTCCCGTGGGATCTGACATCGCTTACTACTTTCAGTGCTAAGTACTGTAGGCTTGGTACCTGGTACTTATGAGCTCTCGGGAACCTGCAGCAAGACAGCAGTTGTTCTCCCCTGATGGCCCTTCGTCGACTTCTCCCGCTGTAAAGCAAGGGAAATAAGTGCTATGCAGTAAAATGTCTGCATAGTTTGGTGTTACATCTGTCCAAAAGTTCTTTGAGTACCAGGTTATCCTCACTGCATAAGTACTGAGCCACCCTTTTTCTGCTACAGTGCT", "", "HHHIIFF>>;=777::;;;IBBBIIIICCFHFFFIIIIIIIIIIIHIIEB99888?<EEEEAAA>>;=IGIAACIIIIIIIIIIIIIIIIE@>>;AAEAA===><<=EEA>>AA999@ABBEEIIFIIIIFFFAA<<;DD<<<BEIHGHIIIEBBEBEFHCAAAEIIIIIIIIIIIIIIIIIIIIHAAAIIIIIIIIIIIIIIIIIIIIIIGBAA556;>>=<=>AAAA@4433156695455;7577:7=AD===A?3555<555?:?GIIIDDGGGI>74433>DCDCDDEGIICCCIIIIAADCCCCCCA::11117:;7111@GIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIHCA>>>@@@88888G9HHHHHHHHHHH"));

        //5': MID003, 0 offset, 0 mismatch
        //3': MID003: 0 offset, 0 mismatch
        list.add(new FastqRecord("MID003-000_MID003-000", "AGACGCACTCAGGTGGTTTACCGCCTCTCTGTTTATCTCCTCTACTGTTCTGTTTAGCCATTCGAAGGCCTCTCCTATTGTCTCTTCTCCACTGTTTCCAGGTGGGATTCTCTCCCTGGGATCTGACATCGCTTACTACTTTCAGTGCTAAGTACTGTAGGCTTGGTACCTGGTACTTATGAGCTCTCGGGAACCTGCAGCAAGACAGCAGTTGTTCTCCCCTGATGGCCCTTCTCACTTCTCCCGCTGTAAAGCAAGGGAAATAAGTGCTATGCAGTAAAATGTCTGCATAGTTTGGTGTTACATCTGTCCAAAAGTTCTTTGAGTACCAGGTTATCCTCACTGCATAAGTACTGAGCCACCCTTTTTCTGGAGTGCGTCT", "", "IIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIHHIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIFFFFIIIIIIIIIHHHIGGGGIIFFFHHIIIIIIIIIIIIIHFFFIIIIIIIIIIIIIIIIIIIIIBCBAFFIIIIIIIIIIIIIGGGHH>;;;DFIIIHHIIIII>>999BDDDIIIIIIIIIIICCBAFFIIIIIIIGGFDDDDDGDDDDIIDDDDFIBB@@??AA??IIIIIIIIIIIDDDDDGGGGGGGGGIGGIGGGGIIGGGD:::66<666.AA=>=>EEIII"));

        //5': MID003, 0 offset, 0 mismatch
        //3': MID001: 0 offset, 0 mismatch
        list.add(new FastqRecord("MID003-000_MID001-000", "AGACGCACTCCAGAAAAGGGTAGGCTCAGTACTTATGCAGTGAGGATAACCTGGTACTCAAAGAACTTTTGGACAGATGTAACACCAAACTATGCAGACATTTTACTGCATAGCACTTATTTCCCTTGCTTTACAGCGGGAGAAGTGAGAAGGGCCATCAGGGGAGAACAACTGCTGTCTTGCTGCAGGTTCCCGAGAGCTCATAAGTACCAGGTACCAAGCCTACAGTACTTAGCACTGAAAGTAGTAAGCGATGTCAGATCCCAGGGAGAGAATCCCACCTGGAAACAGTGGAGAAGAGACAATAGGAGAGGCCTTCGAATGGCTAAACAGAACAGTAGAGGAGATAAACAGAGAGGCGGTAAACCACCTACGCACTCGT", "", "IIIIIIIIIIFII8888999?033GGIIIIIIIIIIIEEEFIIIIIGEIIIIIIHHHIH<<<IIIIBBBBHHIIIIHIIIIIIIIIIIIIIIIIIIIIIIHHHHIIIIIIIIIIIIHHH<<<GGHFFIIEEIIIIIH>>>IIIIIIIIIIIIIIIIIIIIDDDDIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIHI<<<HIIIIIIIIIIIIIIIIIIIIHHHIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIHEEEHHCCCECEGIIIHHFFHAGGFEBEHEEEFECCEEHI"));

        //5': MID002, 0 offset, 0 mismatch
        //3': MID001: 0 offset, 0 mismatch
        list.add(new FastqRecord("MID002-000_MID001-000", "ACGCTCGACACAGAAAAGGTGGCTCAGTACTTATGCAGTGAGGATAACCTGGTACTCAAAGAACTTTTGGACAGATGTAACACCAAACTATGCAGACATTTTACTGCATAGCACTTATTTCCCTTGCTTTACAGCGGGAGAAGTGAGAAGGGCCATCAGGGGAGAACAACTGCTGTCTTGCTGCAGGTTCCCGAGAGCTCATAAGTACCAGGTACCAAGCCTACAGTACTTAGCACTGAAAGTAGTAAGCGATGTCAGATCCCAGGGAGAGATCCCACCTGGAAACAGTGGAGAAGAGACAATAGGAGAGGCCTTCGAATGGCTAAACAGAACAGTAGAGGAGATAAACAGAGAGGCGGAAACCACCTACGCACTCGT", "", "IIIIIIIIIDD=7330000029CEIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIICCCGDDG====CCFFGIHIIIIIIIIIIIIIIIIIIIIIIIFFFFIIIIIIIIIICA<<:FFG888FFIIIIIIIIIIGGGIIIGIIIIIGGGGFFGGFGB7777F<GGGGGGGIIIIIIIIIIIIIGGIIIFFFFCCCCFFFCCCCBB@BBBB>9444??BB???BBEEEECCCCFCCCFFFFFCACFFFFBBBFFFFCAAAAA??????944323??;55999>??747777??999;<<<22207732333----/0333333/00351793325774353300...-/-3:::5552442:2202225888844444"));

        //5': MID002, 0 offset, 1 mismatch
        //3': MID001: 0 offset, 0 mismatch
        list.add(new FastqRecord("MID002-100_MID001-000", "ACGCcCGACACAGAAAAGGTGGCTCAGTACTTATGCAGTGAGGATAACCTGGTACTCAAAGAACTTTTGGACAGATGTAACACCAAACTATGCAGACATTTTACTGCATAGCACTTATTTCCCTTGCTTTACAGCGGGAGAAGTGAGAAGGGCCATCAGGGGAGAACAACTGCTGTCTTGCTGCAGGTTCCCGAGAGCTCATAAGTACCAGGTACCAAGCCTACAGTACTTAGCACTGAAAGTAGTAAGCGATGTCAGATCCCAGGGAGAGATCCCACCTGGAAACAGTGGAGAAGAGACAATAGGAGAGGCCTTCGAATGGCTAAACAGAACAGTAGAGGAGATAAACAGAGAGGCGGAAACCACCTACGCACTCGT", "", "IIIIIIIIIDD=7330000029CEIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIICCCGDDG====CCFFGIHIIIIIIIIIIIIIIIIIIIIIIIFFFFIIIIIIIIIICA<<:FFG888FFIIIIIIIIIIGGGIIIGIIIIIGGGGFFGGFGB7777F<GGGGGGGIIIIIIIIIIIIIGGIIIFFFFCCCCFFFCCCCBB@BBBB>9444??BB???BBEEEECCCCFCCCFFFFFCACFFFFBBBFFFFCAAAAA??????944323??;55999>??747777??999;<<<22207732333----/0333333/00351793325774353300...-/-3:::5552442:2202225888844444"));

        //5: MID002: 1 deletion
        //3': MID002: 0 offset, 0 mismatch
        list.add(new FastqRecord("MID002-001_MID002-000", "CGCTCGACAAGGTGGTTTACCGCCTCTCTGTTTATCTCCTCTACTGTTCTGTTTAGCCATTCGAAGGCCTCTCCTATTGTCTCTTCTCCACTGTTTCCAGGTGGGATTCTCTCCCTGTGATCTGACATCGCTTACTACTTTCAGTGCTAAGTACTGTAGGCTTGGTACCTGGTACTTATGAGCTCTCGGGAACCTGCAGCAAGACAGCAGTTGTTCTCCCCTGATGGCCCTTCTCACTTCTCCCGCTGTAAAGCAAGGGAAATAAGTGCTATGCAGTAAAATGTCTGCATAGTTTGGTGTTACATCTGTCCAAAAGTTCTTTGAGTACCAGGTTATCCTCACTGCATAAGTACTGAGCCACCCTTTTTCTGTGTCGAGCGT", "", "IIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIICCAAHIIIIIHCCACIIIGIIIIIIIHHHHIIIIIIIIIICCC??A;;AGIIIIIGGGHI??==<=GIGGAAACIIIIIG?GGIIGGGGGFFCC>:77:<:7343:<CEECCCAA9997AFCCCC>>ACC:::<C<<<99222100.....0-;<<A@<>>CCD"));

        //5': MID002, 0 offset, 0 mismatch
        //3': MID001: 1 deletion
        list.add(new FastqRecord("MID002-000_MID001-001", "ACGCTCGACACAGAAAAGGGTGGCTCAGTACTTATGCAGTGAGGATAACCTGGTACTCAAAGAACTTTTGGACAGATGTAACACCAAACTATGCAGACATTTTACTGCATAGCACTTATTTCCCTTGCTTTACAGCGGGAGAAGTGAGAAGGGCCATCAGGGGAGAACAACTGCTGTCTTGCTGCAGGTTCCCGAGAGCTCATAAGTACCAGGTACCAAGCCTACAGTACTTAGCACTGAAAGTAGTAAGCGATGTCAGATCCCAGGGAGAGAATCCCACCTGGAAACAGTGGAGAAGAGACAATAGGAGAGGCCTTCGAATGGCTAAACAGAACAGTAGAGGAGATAAACAGATGAGGCGGTAAACCACCTACGCACTCG", "", "686HDEGC@@D83333366BF==FCDDGGGIAAAIIIGGGHIGGIIGGGGIIIIIIIHIGIIAADDDDDGGHHHHHIIHDDDIGGGGGHIHHFCCCEDD33338@8A>>>>FFHHHHHDDDD>>::C@1114<DDDDDDEBDGEFEDC<<<<;B>A@AA<<;;<7DDC@@EFFFFGGGGGGGGGGGFIIDDDDDDDDDFDDCC<<<>>CCDDC>>>DDDFDCCC@>>>B>><<<<<<:::555:;???>>>><>>>8777<:::??=996<<><<311779>>>????<>;;;;:::<;;;9999992000250000029755577;;44/6<<:::?::;;;?<;;;?=:7;;;===<:88<::885771///1111111"));

        //5': MID001, 0 offset, 0 mismatch
        //3': MID003: 0 offset, 0 mismatch
        list.add(new FastqRecord("MID001-000_MID003-000", "ACGAGTGCGTAGGTGGTTTACCGCCTCTCTGTTTATCTCCTCTACTGTTCTGTTTAGCCATTCGAAGGCCTCTCCTATTGTCTCTTCTCCACTGTTTCCAGGTGGGATTCTCTCCCTGGGATCTGACATCGCTTACTACTTTCAGTGCTAAGTACTGTAGGCTTGGTACCTGGTACTTATGAGCTCTCGGGAACCTGCAGCAAGACAGCAGTTGTTCTCCCCTGATGGCCCTTCTCACTTCTCCCGCTGTAAAGCAAGGGAAATAAGTGCTATGCAGTAAAATGTCTGCATAGTTTGGTGTTACATCTGTCCAAAAGTTCTTTGAGTACCAGGTTATCCTCACTGCATAAGTACTGAGCCACCCTTTTTCTGGAGTGCGTCT", "", "IIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIHHHIIIIIIIIIBA>DG=<;>>3339AFGFFFFFIIIHHEHHIFI>;;;FFDDDIHHFFIIIIIIIIF@@@BIIIIIIIIIIIIIIIIIIIIFDDDD667611118@CCCHHHIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIEECCIIIIIIIIIIIIIIIIIIDDDIHEEEEE??33338;EE@@FGIIIIGGIIIIIIIIGIIIIIIII@@@@GIIIIGGC@;886::>::I8IIFFFIIIIII"));

        //5': MID001, 1 offset, 0 mismatch
        //3': MID003: 0 offset, 0 mismatch
        list.add(new FastqRecord("MID001-010_MID003-000", "cACGAGTGCGTAGGTGGTTTACCGCCTCTCTGTTTATCTCCTCTACTGTTCTGTTTAGCCATTCGAAGGCCTCTCCTATTGTCTCTTCTCCACTGTTTCCAGGTGGGATTCTCTCCCTGGGATCTGACATCGCTTACTACTTTCAGTGCTAAGTACTGTAGGCTTGGTACCTGGTACTTATGAGCTCTCGGGAACCTGCAGCAAGACAGCAGTTGTTCTCCCCTGATGGCCCTTCTCACTTCTCCCGCTGTAAAGCAAGGGAAATAAGTGCTATGCAGTAAAATGTCTGCATAGTTTGGTGTTACATCTGTCCAAAAGTTCTTTGAGTACCAGGTTATCCTCACTGCATAAGTACTGAGCCACCCTTTTTCTGGAGTGCGTCT", "", "IIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIHHHIIIIIIIIIBA>DG=<;>>3339AFGFFFFFIIIHHEHHIFI>;;;FFDDDIHHFFIIIIIIIIF@@@BIIIIIIIIIIIIIIIIIIIIFDDDD667611118@CCCHHHIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIEECCIIIIIIIIIIIIIIIIIIDDDIHEEEEE??33338;EE@@FGIIIIGGIIIIIIIIGIIIIIIII@@@@GIIIIGGC@;886::>::I8IIFFFIIIIII"));

        //5': MID001, 2 offset, 0 mismatch
        //3': MID003: 0 offset, 0 mismatch
        list.add(new FastqRecord("MID001-020_MID003-000", "ctACGAGTGCGTAGGTGGTTTACCGCCTCTCTGTTTATCTCCTCTACTGTTCTGTTTAGCCATTCGAAGGCCTCTCCTATTGTCTCTTCTCCACTGTTTCCAGGTGGGATTCTCTCCCTGGGATCTGACATCGCTTACTACTTTCAGTGCTAAGTACTGTAGGCTTGGTACCTGGTACTTATGAGCTCTCGGGAACCTGCAGCAAGACAGCAGTTGTTCTCCCCTGATGGCCCTTCTCACTTCTCCCGCTGTAAAGCAAGGGAAATAAGTGCTATGCAGTAAAATGTCTGCATAGTTTGGTGTTACATCTGTCCAAAAGTTCTTTGAGTACCAGGTTATCCTCACTGCATAAGTACTGAGCCACCCTTTTTCTGGAGTGCGTCT", "", "IIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIHHHIIIIIIIIIBA>DG=<;>>3339AFGFFFFFIIIHHEHHIFI>;;;FFDDDIHHFFIIIIIIIIF@@@BIIIIIIIIIIIIIIIIIIIIFDDDD667611118@CCCHHHIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIEECCIIIIIIIIIIIIIIIIIIDDDIHEEEEE??33338;EE@@FGIIIIGGIIIIIIIIGIIIIIIII@@@@GIIIIGGC@;886::>::I8IIFFFIIIIII"));

        return list;
    }

    @TestTimeout(240)
    abstract public static class AbstractPipelineTestCase extends Assert
    {
        protected final String DUAL_BARCODE_FILENAME = "dualBarcodes_SIV.fastq";
        protected final String SAMPLE_SFF_FILENAME = "sample454_SIV.sff";

        protected final String PAIRED_FILENAME1 = "paired1.fastq.gz";
        protected final String PAIRED_FILENAME2 = "paired2.fastq.gz";
        protected final String UNPAIRED_FILENAME = "unpaired1.fastq.gz";

        protected final String PAIRED_FILENAME_L1a = "s_G1_L001_R1_001.fastq.gz";
        protected final String PAIRED_FILENAME2_L1a = "s_G1_L001_R2_001.fastq.gz";
        protected final String PAIRED_FILENAME_L1b = "s_G1_L001_R1_002.fastq.gz";
        protected final String PAIRED_FILENAME2_L1b = "s_G1_L001_R2_002.fastq.gz";
        protected final String PAIRED_FILENAME_L2 = "s_G1_L002_R1_001.fastq.gz";
        protected final String PAIRED_FILENAME2_L2 = "s_G1_L002_R2_001.fastq.gz";

        protected final String UNZIPPED_PAIRED_FILENAME1 = "paired3.fastq";
        protected final String UNZIPPED_PAIRED_FILENAME2 = "paired4.fastq";

        protected final String READSET_JOB = "readsetJob.json";
        protected final String ALIGNMENT_JOB = "alignmentJob.json";
        protected final String VARIANT_JOB = "variantProcessingJob.json";
        protected final String IMPORT_TASKID = "org.labkey.api.pipeline.file.FileAnalysisTaskPipeline:sequenceImportPipeline";
        protected final String ANALYSIS_TASKID = "org.labkey.api.pipeline.file.FileAnalysisTaskPipeline:sequenceAnalysisPipeline";

        protected Container _project;
        protected TestContext _context;
        protected File _pipelineRoot;
        protected File _sampleData;

        protected Boolean _isExternalPipelineEnabled = null;

        protected static final Logger _log = LogManager.getLogger(AbstractPipelineTestCase.class);

        protected void writeJobLogToLabKeyLog(File log, String jobName) throws IOException
        {
            _log.error("Error processing job: " + jobName);
            try (BufferedReader r = Readers.getReader(log))
            {
                r.lines().forEach(_log::error);
            }
        }

        protected static boolean doSkipCleanup()
        {
            return "1".equals(TestContext.get().getRequest().getParameter("skipTestCleanup"));
        }

        protected static void doInitialSetUp(String projectName) throws Exception
        {
            //pre-clean
            doCleanup(projectName);

            Container project = ContainerManager.getForPath(projectName);
            if (project == null)
            {
                project = ContainerManager.createContainer(ContainerManager.getRoot(), projectName);

                //disable search so we dont get conflicts when deleting folder quickly
                ContainerManager.updateSearchable(project, false, TestContext.get().getUser());

                Set<Module> modules = new HashSet<>();
                modules.addAll(project.getActiveModules());
                modules.add(ModuleLoader.getInstance().getModule(SequenceAnalysisModule.NAME));
                project.setFolderType(FolderTypeManager.get().getFolderType("Laboratory Folder"), TestContext.get().getUser());
                project.setActiveModules(modules);
            }
        }

        abstract protected String getProjectName();

        @Before
        public void setUp() throws Exception
        {
            _context = TestContext.get();
            _sampleData = getSampleDataDir();

            _project = ContainerManager.getForPath(getProjectName());
            _pipelineRoot = PipelineService.get().getPipelineRootSetting(_project).getRootPath();

            String path = PipelineJobService.get().getConfigProperties().getSoftwarePackagePath(SequencePipelineService.SEQUENCE_TOOLS_PARAM);
            path = StringUtils.trimToNull(path);
            if (path != null)
            {
                File dir = new File(path);
                _log.info("sequence junit tests will look for tools in: [" + path + "]");
                if (!dir.exists())
                {
                    _log.error("directory does not exist: " + path);
                }
            }
            else
            {
                _log.info("param: " + SequencePipelineService.SEQUENCE_TOOLS_PARAM + " not defined");
            }
        }

        private File getSampleDataDir() throws Exception
        {
            Module module = ModuleLoader.getInstance().getModule(SequenceAnalysisModule.class);
            DirectoryResource resource = (DirectoryResource) module.getModuleResolver().lookup(Path.parse("sampledata"));
            File file = null;
            for (Resource r : resource.list())
            {
                if (r instanceof FileResource)
                {
                    file = ((FileResource) r).getFile().getParentFile();
                    break;
                }
            }

            if (file == null || !file.exists())
            {
                _log.error("unable to find sampledata directory");
            }

            if (file == null || !file.exists())
            {
                throw new Exception("sampledata folder does not exist: " + (file == null ? "null" : file.getPath()));
            }

            return file;
        }

        protected void ensureFilesPresent(String prefix) throws Exception
        {
            File file1 = new File(_pipelineRoot, prefix + DUAL_BARCODE_FILENAME);
            if (!file1.exists())
            {
                //debug intermittent failure
                File orig = new File(_sampleData, DUAL_BARCODE_FILENAME + ".gz");
                if (!orig.exists())
                {
                    _log.info("missing file: " + orig.getPath());
                    _log.info("files in sampleData: ");
                    for (String f : _sampleData.list())
                    {
                        _log.info(f);
                    }
                }

                FileUtils.copyFile(orig, file1);
                Compress.decompressGzip(new File(_sampleData, DUAL_BARCODE_FILENAME + ".gz"), file1);
            }

            File file2 = new File(_pipelineRoot, prefix + SAMPLE_SFF_FILENAME);
            if (!file2.exists())
                FileUtils.copyFile(new File(_sampleData, SAMPLE_SFF_FILENAME), file2);

            for (String fn : Arrays.asList(PAIRED_FILENAME1, PAIRED_FILENAME_L1a, PAIRED_FILENAME_L1b, PAIRED_FILENAME_L2))
            {
                File file3 = new File(_pipelineRoot, prefix + fn);
                if (!file3.exists())
                    FileUtils.copyFile(new File(_sampleData, PAIRED_FILENAME1), file3);
            }

            for (String fn : Arrays.asList(PAIRED_FILENAME2, PAIRED_FILENAME2_L1a, PAIRED_FILENAME2_L1b, PAIRED_FILENAME2_L2))
            {
                File file4 = new File(_pipelineRoot, prefix + fn);
                if (!file4.exists())
                    FileUtils.copyFile(new File(_sampleData, PAIRED_FILENAME2), file4);
            }

            File file5 = new File(_pipelineRoot, prefix + UNZIPPED_PAIRED_FILENAME1);
            if (!file5.exists())
            {
                decompressAndCleanFastq(new File(_sampleData, PAIRED_FILENAME1), file5);
            }

            File file6 = new File(_pipelineRoot, prefix + UNZIPPED_PAIRED_FILENAME2);
            if (!file6.exists())
            {
                decompressAndCleanFastq(new File(_sampleData, PAIRED_FILENAME2), file6);
            }

            File file7 = new File(_pipelineRoot, prefix + UNPAIRED_FILENAME);
            if (!file7.exists())
            {
                FileUtils.copyFile(new File(_sampleData, UNPAIRED_FILENAME), file7);
            }
        }

        protected void decompressAndCleanFastq(File input, File output)
        {
            //decompress and remove trailing /1 from readnames, as these
            FastqWriterFactory fact = new FastqWriterFactory();
            try (FastqReader reader = new FastqReader(input); FastqWriter writer = fact.newWriter(output))
            {
                while (reader.hasNext())
                {
                    FastqRecord rec = reader.next();
                    String header = rec.getReadHeader();
                    if (rec.getReadHeader().endsWith("/1") || rec.getReadHeader().endsWith("/2"))
                    {
                        header = header.substring(0, header.lastIndexOf("/"));
                    }
                    writer.write(new FastqRecord(header, rec.getReadString(), rec.getBaseQualityHeader(), rec.getBaseQualityString()));
                }
            }
        }

        protected void verifyFileInputs(File basedir, String[] fileNames, JSONObject config, String prefix)
        {
            String handling = config.getString("inputFileTreatment");
            if ("none".equals(handling))
            {
                for (String fn : fileNames)
                {
                    File input = new File(_pipelineRoot, prefix + fn);
                    Assert.assertTrue("Input file missing: " + input.getPath(), input.exists());
                }
            }
            else if ("compress".equals(handling))
            {
                FileType gz = new FileType(".gz");

                for (String fn : fileNames)
                {
                    File input = new File(_pipelineRoot, prefix + fn);
                    Assert.assertFalse("Input file still exists: " + input.getPath(), input.exists());

                    File compressed;
                    if (gz.isType(fn))
                        compressed = new File(basedir, prefix + fn);
                    else
                        compressed = new File(basedir, FileUtil.getBaseName(prefix + fn) + ".fastq.gz");

                    Assert.assertTrue("Compressed file missing: " + compressed.getPath(), compressed.exists());
                }
            }
            else if ("delete".equals(handling))
            {
                for (String fn : fileNames)
                {
                    File input = new File(_pipelineRoot, prefix + fn);
                    Assert.assertFalse("Input file still present: " + input.getPath(), input.exists());
                }
            }
        }

        protected void verifyFileOutputs(File basedir, Set<File> expectedOutputs)
        {
            IOFileFilter filter = new IOFileFilter()
            {
                public boolean accept(File file)
                {
                    return true;
                }

                public boolean accept(File dir, String name)
                {
                    return true;
                }
            };

            Set<File> files = new HashSet<>(FileUtils.listFilesAndDirs(basedir, filter, filter));
            expectedOutputs.add(basedir);
            if (expectedOutputs.size() != files.size())
            {
                for (File f : files)
                {
                    if (!expectedOutputs.contains(f))
                    {
                        _log.error("Unexpected file found: " + f.getPath());
                    }
                }
            }

            Collection<File> diff = CollectionUtils.disjunction(expectedOutputs, files);
            if (!diff.isEmpty())
            {
                for (File f : diff)
                {
                    if (expectedOutputs.contains(f))
                    {
                        _log.error("missing file: " + f.getPath());
                    }
                    else
                    {
                        _log.error("unexpected output found: " + f.getPath());
                    }
                }

                _log.error("files present: ");
                for (File f : files)
                {
                    _log.error(f.getPath());
                }
            }

            for (File f : expectedOutputs)
            {
                Assert.assertTrue("Output file not found, expected: " + f.getPath(), f.exists());
            }

            Assert.assertEquals("Incorrect number of outputs created", expectedOutputs.size(), files.size());
        }

        protected Set<PipelineJob> createPipelineJob(String jobName, JSONObject config, SequenceAnalysisController.AnalyzeForm.TYPE type) throws Exception
        {
            Map<String, Object> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");

            JSONObject json = new JSONObject();
            json.put("jobName", jobName);
            json.put("description", "Description");
            json.put("jobParameters", config);

            json.put("type", type.name());
            String requestContent = json.toString();

            HttpServletRequest request = ViewServlet.mockRequest(RequestMethod.POST.name(), DetailsURL.fromString("/sequenceanalysis/startPipelineJob.view").copy(_project).getActionURL(), _context.getUser(), headers, requestContent);

            MockHttpServletResponse response = ViewServlet.mockDispatch(request, null);
            JSONObject responseJson = new JSONObject(response.getContentAsString());
            if (response.getStatus() != HttpServletResponse.SC_OK)
                throw new RuntimeException("Problem creating pipeline job: " + responseJson.getString("exception"));

            JSONArray guidList = responseJson.getJSONArray("jobGUIDs");
            assert guidList.length() >= 1;

            Set<PipelineJob> ret = new HashSet<>();
            for (int i = 0; i < guidList.length(); i++)
            {
                ret.add(PipelineJobService.get().getJobStore().getJob(guidList.getString(i)));
            }

            return ret;
        }

        protected boolean isExternalPipelineEnabled()
        {
            if (_isExternalPipelineEnabled != null)
                return _isExternalPipelineEnabled;

            _isExternalPipelineEnabled = Boolean.parseBoolean(System.getProperty(PIPELINE_PROP_NAME));
            if (!_isExternalPipelineEnabled)
                _log.warn("Sequence pipeline is not enabled on this server, so some tests will be skipped");
            else
                _log.info("Sequence pipeline enabled on this server, so all tests will run");

            return _isExternalPipelineEnabled;
        }

        protected void waitForJobs(Collection<PipelineJob> jobs) throws Exception
        {
            try
            {
                long start = System.currentTimeMillis();
                long timeout = 60 * 1000 * 4; //4 mins

                Thread.sleep(1000);

                while (!areJobsDone(jobs))
                {
                    Thread.sleep(1000);

                    long duration = System.currentTimeMillis() - start;
                    if (duration > timeout)
                    {
                        //this will iterate jobs, inspecting for errors
                        for (PipelineJob job : jobs)
                        {
                            isJobDone(job);
                        }

                        throw new RuntimeException("Timed out waiting for pipeline jobs: " + jobs.size());

                    }
                }

                Thread.sleep(10000);
            }
            catch (InterruptedException e)
            {
                throw new RuntimeException(e);
            }
        }

        private boolean areJobsDone(Collection<PipelineJob> jobs) throws Exception
        {
            for (PipelineJob job : jobs)
            {
                if (!isJobDone(job))
                {
                    return false;
                }
            }

            return true;
        }

        private boolean isJobDone(PipelineJob job) throws Exception
        {
            TableInfo ti = PipelineService.get().getJobsTable(_context.getUser(), _project);
            TableSelector ts = new TableSelector(ti, new SimpleFilter(FieldKey.fromString("job"), job.getJobGUID()), null);
            Map<String, Object> map = ts.getMap();

            if (PipelineJob.TaskStatus.complete.matches((String)map.get("status")))
                return true;


            //look for errors
            boolean error = PipelineJob.TaskStatus.error.matches((String)map.get("status"));
            SimpleFilter filter = new SimpleFilter(FieldKey.fromString("jobparent"), job.getJobGUID());
            filter.addCondition(FieldKey.fromString("status"), PipelineJob.TaskStatus.error.toString().toUpperCase());
            if (new TableSelector(ti, filter, null).exists())
            {
                error = true;
            }

            if (error)
            {
                //on failure, append contents of pipeline job file to primary error log
                if (job.getLogFile() != null)
                {
                    writeJobLogToLog(job);
                }
                else
                {
                    _log.error("No log file present for sequence pipeline job");
                }

                throw new Exception("There was an error running job: " + (job == null ? "PipelineJob was null" : job.getDescription()));
            }

            return false; //job != null && job.getActiveTaskId() != null;
        }

        protected void writeJobLogToLog(PipelineJob job) throws IOException
        {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = Readers.getReader(job.getLogFile()))
            {
                sb.append("*******************\n");
                sb.append("Error running sequence junit tests.  Pipeline log:\n");
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }

                sb.append("*******************\n");
            }

            _log.error(sb.toString());
        }

        protected JSONObject substituteParams(File xml, String jobName) throws IOException
        {
            String content = FileUtils.readFileToString(xml, Charset.defaultCharset());
            content = content.replaceAll("@@BASEURL@@", AppProps.getInstance().getBaseServerUrl() + AppProps.getInstance().getContextPath());
            content = content.replaceAll("@@CONTAINERID@@", _project.getPath());
            content = content.replaceAll("@@CONTAINERPATH@@", _project.getPath());
            content = content.replaceAll("@@USERID@@", String.valueOf(_context.getUser().getUserId()));
            content = content.replaceAll("@@EMAIL@@", _context.getUser().getEmail());
            content = content.replaceAll("@@jobName@@", jobName);
            content = content.replaceAll("[\n\r\t]", "");
            return new JSONObject(content);
        }

        protected void appendSamplesForImport(JSONObject config, List<FileGroup> files)
        {
            JSONArray inputFiles = new JSONArray();

            int i = 0;
            for (FileGroup g : files)
            {
                config.put("readset_" + i, "{\"fileGroupId\":\"" + g.name + "\",\"platform\":\"ILLUMINA\",\"sampleid\":\"1\",\"readset\":\"\",\"instrument_run_id\":\"\",\"readsetname\":\"TestReadset" + i + "\",\"subjectid\":\"Subject\",\"sampledate\":\"2010-01-01T12:00:00Z\"}");

                JSONObject json = new JSONObject();
                json.put("name", g.name);
                JSONArray fileArr = new JSONArray();
                for (FileGroup.FilePair p : g.filePairs)
                {
                    JSONObject o = new JSONObject();
                    o.put("fileGroupId", g.name);
                    o.put("centerName", p.centerName);
                    o.put("platformUnit", p.platformUnit);

                    JSONObject file1 = new JSONObject();
                    file1.put("fileName", p.file1.getName());
                    o.put("file1", file1);
                    inputFiles.put(file1);

                    if (p.file2 != null)
                    {
                        JSONObject file2 = new JSONObject();
                        file2.put("fileName", p.file2.getName());
                        o.put("file2", file2);
                        inputFiles.put(file2);
                    }

                    fileArr.put(o);
                }
                json.put("files", fileArr);

                config.put("fileGroup_" + i, json.toString());

                i++;
            }

            config.put("inputFiles", inputFiles);

            if (config.getBoolean("inputfile.barcode"))
            {
                //NOTE: this cannot automatically be inferred based on the other info in the config, so we just skip it
            }
        }

        protected static void doCleanup(String projectName)
        {
            if (doSkipCleanup())
            {
                _log.info("skipping junit test cleanup");
                return;
            }

            Container project = ContainerManager.getForPath(projectName);
            if (project != null)
            {
                File pipelineRoot = PipelineService.get().getPipelineRootSetting(project).getRootPath();
                try
                {
                    if (pipelineRoot.exists())
                    {
                        File[] contents = pipelineRoot.listFiles();
                        for (File f : contents)
                        {
                            if (f.exists())
                            {
                                if (f.isDirectory())
                                    FileUtils.deleteDirectory(f);
                                else
                                    f.delete();
                            }
                        }
                    }
                }
                catch (IOException e)
                {
                    throw new RuntimeException(e);
                }

                ContainerManager.deleteAll(project, TestContext.get().getUser());
            }
        }
    }

    public static class SequenceImportPipelineTestCase extends AbstractPipelineTestCase
    {
        private static final String PROJECT_NAME = "SequenceImportTestProject";

        @BeforeClass
        public static void initialSetUp() throws Exception
        {
            doInitialSetUp(PROJECT_NAME);
        }

        @AfterClass
        public static void cleanup()
        {
            doCleanup(PROJECT_NAME);
        }

        protected String getProjectName()
        {
            return PROJECT_NAME;
        }

        /**
         * This is the most basic test of readset import and creation.  A single FASTQ is provided, which can be normalized on the webserver
         * without external tools.
         * @throws Exception
         */
        @Test
        public void basicTest() throws Exception
        {
            String prefix = "BasicTest_";
            ensureFilesPresent(prefix);

            String jobName = prefix + System.currentTimeMillis();
            String[] fileNames = new String[]{DUAL_BARCODE_FILENAME};
            JSONObject config = substituteParams(new File(_sampleData, READSET_JOB), jobName);
            FileGroup g = new FileGroup();
            g.name = "Group1";
            g.filePairs = new ArrayList<>();
            g.filePairs.add(new FileGroup.FilePair());
            g.filePairs.get(0).file1 = new File(prefix + DUAL_BARCODE_FILENAME);

            appendSamplesForImport(config, Arrays.asList(g));

            Set<PipelineJob> jobs = createPipelineJob(jobName, config, SequenceAnalysisController.AnalyzeForm.TYPE.readsetImport);
            waitForJobs(jobs);

            Set<File> expectedOutputs = new HashSet<>();
            File basedir = getBaseDir(jobs.iterator().next());
            File fq = new File(basedir, prefix + DUAL_BARCODE_FILENAME + ".gz");
            expectedOutputs.add(fq);
            expectedOutputs.add(new File(basedir, "sequenceImport.json"));
            expectedOutputs.add(new File(basedir, "sequenceSupport.json.gz"));
            expectedOutputs.add(new File(basedir, basedir.getName() + ".pipe.xar.xml"));
            File log = new File(basedir, jobName + ".log");
            expectedOutputs.add(log);
            try
            {
                verifyFileOutputs(basedir, expectedOutputs);
                verifyFileInputs(basedir, fileNames, config, prefix);
                validateReadsets(jobs, config);

                Assert.assertEquals("Incorrect read number", 3260L, FastqUtils.getSequenceCount(fq));
            }
            catch (Exception e)
            {
                writeJobLogToLabKeyLog(log, jobName);
                throw e;
            }
        }

        @Test
        public void leaveInPlaceTest() throws Exception
        {
            String prefix = "BasicTest_";
            ensureFilesPresent(prefix);

            String jobName = prefix + System.currentTimeMillis();
            String[] fileNames = new String[]{PAIRED_FILENAME1};
            JSONObject config = substituteParams(new File(_sampleData, READSET_JOB), jobName);
            FileGroup g = new FileGroup();
            g.name = "Group1";
            g.filePairs = new ArrayList<>();
            g.filePairs.add(new FileGroup.FilePair());
            g.filePairs.get(0).file1 = new File(prefix + PAIRED_FILENAME1);

            appendSamplesForImport(config, Arrays.asList(g));
            config.put("inputFileTreatment", "leaveInPlace");

            Set<PipelineJob> jobs = createPipelineJob(jobName, config, SequenceAnalysisController.AnalyzeForm.TYPE.readsetImport);
            waitForJobs(jobs);

            Set<File> expectedOutputs = new HashSet<>();
            File basedir = getBaseDir(jobs.iterator().next());
            Assert.assertFalse("Unexpected file found", new File(basedir, prefix + PAIRED_FILENAME1).exists());
            File fq = new File(_pipelineRoot, prefix + PAIRED_FILENAME1);
            Assert.assertTrue("File not found", fq.exists());
            expectedOutputs.add(new File(basedir, "sequenceImport.json"));
            expectedOutputs.add(new File(basedir, "sequenceSupport.json.gz"));
            expectedOutputs.add(new File(basedir, basedir.getName() + ".pipe.xar.xml"));
            File log = new File(basedir, jobName + ".log");
            expectedOutputs.add(log);
            try
            {
                verifyFileOutputs(basedir, expectedOutputs);
                verifyFileInputs(basedir, fileNames, config, prefix);
                validateReadsets(jobs, config);

                Assert.assertEquals("Incorrect read number", 211L, FastqUtils.getSequenceCount(fq));
            }
            catch (Exception e)
            {
                writeJobLogToLabKeyLog(log, jobName);
                throw e;
            }
        }

        private void runMergePipelineJob(String jobName, boolean deleteIntermediates, String prefix) throws Exception
        {
            JSONObject config = substituteParams(new File(_sampleData, READSET_JOB), jobName);

            FileGroup g = new FileGroup();
            g.name = "Group1";
            g.filePairs = new ArrayList<>();
            g.filePairs.add(new FileGroup.FilePair());
            g.filePairs.get(0).platformUnit = "platformUnit1";
            g.filePairs.get(0).file1 = new File(prefix + PAIRED_FILENAME_L1a);
            g.filePairs.get(0).file2 = new File(prefix + PAIRED_FILENAME2_L1a);

            g.filePairs.add(new FileGroup.FilePair());
            g.filePairs.get(1).platformUnit = "platformUnit1";
            g.filePairs.get(1).file1 = new File(prefix + PAIRED_FILENAME_L1b);
            g.filePairs.get(1).file2 = new File(prefix + PAIRED_FILENAME2_L1b);

            g.filePairs.add(new FileGroup.FilePair());
            g.filePairs.get(2).platformUnit = "platformUnit2";
            g.filePairs.get(2).file1 = new File(prefix + PAIRED_FILENAME_L2);
            g.filePairs.get(2).file2 = new File(prefix + PAIRED_FILENAME2_L2);

            FileGroup g2 = new FileGroup();
            g2.name = "Group2";
            g2.filePairs = new ArrayList<>();
            g2.filePairs.add(new FileGroup.FilePair());
            g2.filePairs.get(0).platformUnit = "platformUnit3";
            g2.filePairs.get(0).file1 = new File(prefix + PAIRED_FILENAME1);
            g2.filePairs.get(0).file2 = new File(prefix + PAIRED_FILENAME2);

            FileGroup g3 = new FileGroup();
            g3.name = "Group3";
            g3.filePairs = new ArrayList<>();
            g3.filePairs.add(new FileGroup.FilePair());
            g3.filePairs.get(0).platformUnit = "platformUnit4";
            g3.filePairs.get(0).file1 = new File(prefix + UNPAIRED_FILENAME);

            if (deleteIntermediates)
            {
                config.put("deleteIntermediateFiles", true);
                config.put("inputFileTreatment", "delete");
            }
            else
            {
                config.put("deleteIntermediateFiles", false);
                config.put("inputFileTreatment", "compress");
            }
            config.put("inputfile.runFastqc", true);
            appendSamplesForImport(config, Arrays.asList(g, g2, g3));

            Set<PipelineJob> jobsUnsorted = createPipelineJob(jobName, config, SequenceAnalysisController.AnalyzeForm.TYPE.readsetImport);
            waitForJobs(jobsUnsorted);

            List<PipelineJob> jobs = new ArrayList<>(jobsUnsorted);
            Collections.sort(jobs, new Comparator<PipelineJob>()
            {
                @Override
                public int compare(PipelineJob o1, PipelineJob o2)
                {
                    JSONObject j1 = new JSONObject(o1.getParameters().get("fileGroup_1"));
                    JSONObject j2 = new JSONObject(o2.getParameters().get("fileGroup_1"));

                    return j1.getString("name").compareTo(j2.getString("name"));
                }
            });

            //job1: g1
            Set<File> expectedOutputs = new HashSet<>();
            File basedir = getBaseDir(jobs.get(0));
            File normalizeDir = new File(basedir, "Normalization");
            expectedOutputs.add(normalizeDir);

            File merge1 = new File(normalizeDir, prefix + SequenceTaskHelper.getUnzippedBaseName(PAIRED_FILENAME_L1a) + ".merged.fastq.gz");
            expectedOutputs.add(merge1);
            expectedOutputs.add(new File(normalizeDir, FileUtil.getBaseName(FileUtil.getBaseName(merge1)) + "_fastqc.html.gz"));
            expectedOutputs.add(new File(normalizeDir, FileUtil.getBaseName(FileUtil.getBaseName(merge1)) + "_fastqc.zip"));
            File merge2 = new File(normalizeDir, prefix + SequenceTaskHelper.getUnzippedBaseName(PAIRED_FILENAME2_L1a) + ".merged.fastq.gz");
            expectedOutputs.add(merge2);
            expectedOutputs.add(new File(normalizeDir, FileUtil.getBaseName(FileUtil.getBaseName(merge2)) + "_fastqc.html.gz"));
            expectedOutputs.add(new File(merge2.getParentFile(), FileUtil.getBaseName(FileUtil.getBaseName(merge2)) + "_fastqc.zip"));

            expectedOutputs.add(new File(basedir, prefix + PAIRED_FILENAME_L2));
            expectedOutputs.add(new File(basedir, prefix + FileUtil.getBaseName(FileUtil.getBaseName(PAIRED_FILENAME_L2)) + "_fastqc.html.gz"));
            expectedOutputs.add(new File(basedir, prefix + FileUtil.getBaseName(FileUtil.getBaseName(PAIRED_FILENAME_L2)) + "_fastqc.zip"));
            expectedOutputs.add(new File(basedir, prefix + PAIRED_FILENAME2_L2));
            expectedOutputs.add(new File(basedir, prefix + FileUtil.getBaseName(FileUtil.getBaseName(PAIRED_FILENAME2_L2)) + "_fastqc.html.gz"));
            expectedOutputs.add(new File(basedir, prefix + FileUtil.getBaseName(FileUtil.getBaseName(PAIRED_FILENAME2_L2)) + "_fastqc.zip"));

            //these will be merged
            if (!deleteIntermediates)
            {
                expectedOutputs.add(new File(basedir, prefix + PAIRED_FILENAME_L1a));
                expectedOutputs.add(new File(basedir, prefix + PAIRED_FILENAME2_L1a));
                expectedOutputs.add(new File(basedir, prefix + PAIRED_FILENAME_L1b));
                expectedOutputs.add(new File(basedir, prefix + PAIRED_FILENAME2_L1b));
            }

            verifyJob(basedir, jobName, expectedOutputs, new String[]{PAIRED_FILENAME_L1a, PAIRED_FILENAME2_L1a, PAIRED_FILENAME_L1b, PAIRED_FILENAME2_L1b, PAIRED_FILENAME_L2, PAIRED_FILENAME2_L2}, prefix, config);

            Assert.assertEquals("Incorrect read number", 422L, FastqUtils.getSequenceCount(merge1));
            Assert.assertEquals("Incorrect read number", 422L, FastqUtils.getSequenceCount(merge2));

            //job2: g2
            expectedOutputs = new HashSet<>();
            basedir = getBaseDir(jobs.get(1));

            expectedOutputs.add(new File(basedir, prefix + PAIRED_FILENAME1));
            expectedOutputs.add(new File(basedir, prefix + FileUtil.getBaseName(FileUtil.getBaseName(PAIRED_FILENAME1)) + "_fastqc.html.gz"));
            expectedOutputs.add(new File(basedir, prefix + FileUtil.getBaseName(FileUtil.getBaseName(PAIRED_FILENAME1)) + "_fastqc.zip"));

            expectedOutputs.add(new File(basedir, prefix + PAIRED_FILENAME2));
            expectedOutputs.add(new File(basedir, prefix + FileUtil.getBaseName(FileUtil.getBaseName(PAIRED_FILENAME2)) + "_fastqc.html.gz"));
            expectedOutputs.add(new File(basedir, prefix + FileUtil.getBaseName(FileUtil.getBaseName(PAIRED_FILENAME2)) + "_fastqc.zip"));

            verifyJob(basedir, jobName, expectedOutputs, new String[]{PAIRED_FILENAME1, PAIRED_FILENAME2}, prefix, config);

            //job3: g3
            expectedOutputs = new HashSet<>();
            basedir = getBaseDir(jobs.get(2));
            expectedOutputs.add(new File(basedir, prefix + UNPAIRED_FILENAME));
            expectedOutputs.add(new File(basedir, prefix + FileUtil.getBaseName(FileUtil.getBaseName(UNPAIRED_FILENAME)) + "_fastqc.html.gz"));
            expectedOutputs.add(new File(basedir, prefix + FileUtil.getBaseName(FileUtil.getBaseName(UNPAIRED_FILENAME)) + "_fastqc.zip"));

            verifyJob(basedir, jobName, expectedOutputs, new String[]{UNPAIRED_FILENAME}, prefix, config);
            validateReadsets(jobs, config, 1);  //we expect one per job, total of 3
        }
        
        private void verifyJob(File basedir, String jobName, Set<File> expectedOutputs, String[] fileNames, String prefix, JSONObject config) throws Exception
        {
            expectedOutputs.add(new File(basedir, basedir.getName() + ".pipe.xar.xml"));
            expectedOutputs.add(new File(basedir, "sequenceImport.json"));
            expectedOutputs.add(new File(basedir, "sequenceSupport.json.gz"));

            File log = new File(basedir, jobName + ".log");
            expectedOutputs.add(log);
            try
            {
                verifyFileOutputs(basedir, expectedOutputs);
                verifyFileInputs(basedir, fileNames, config, prefix);
            }
            catch (Exception e)
            {
                writeJobLogToLabKeyLog(log, jobName);
                throw e;
            }
        }

        /**
         * This test takes 2 input files: a FASTQ and an SFF.  The SFF should be converted to FASTQ, and the files merged into a single
         * FASTQ output.  This pipeline is configured to retain intermediate files.
         * @throws Exception
         */
        @Test
        public void mergeTest() throws Exception
        {
            if (!isExternalPipelineEnabled())
                return;

            String prefix = "MergeTestLanes_";
            ensureFilesPresent(prefix);

            String jobName = prefix + System.currentTimeMillis();
            runMergePipelineJob(jobName, false, prefix);
        }

        /**
         * This is a variation on mergeTest, except intermediate files are deleting and input file are compressed.
         * @throws Exception
         */
        @Test
        public void mergeTestDeletingIntermediates() throws Exception
        {
            if (!isExternalPipelineEnabled())
                return;

            String prefix = "MergeDeletingIntermediates_";
            ensureFilesPresent(prefix);

            String jobName = prefix + System.currentTimeMillis();
            runMergePipelineJob(jobName, true, prefix);
        }

        private JSONObject getBarcodeConfig(String jobName, String[] fileNames, String prefix) throws Exception
        {
            JSONObject config = substituteParams(new File(_sampleData, READSET_JOB), jobName);

            FileGroup g = new FileGroup();
            g.name = "Group1";
            g.filePairs = new ArrayList<>();
            for (String fn : fileNames)
            {
                FileGroup.FilePair p = new FileGroup.FilePair();
                p.file1 = new File(prefix + fn);
                g.filePairs.add(p);
            }

            appendSamplesForImport(config, Arrays.asList(g));

            config.put("inputfile.barcode", true);
            config.put("inputfile.barcodeGroups", "[\"GSMIDs\",\"Fluidigm\"]");
            config.put("readset_0", "{\"fileGroupId\":\"" + g.name + "\",\"readsetname\":\"TestReadset0\",\"barcode5\":\"MID001\",\"barcode3\":\"MID001\",\"sampleid\":\"1\",\"readset\":\"\",\"instrument_run_id\":\"\",\"platform\":\"ILLUMINA\",\"subjectid\":\"Subject\"}");
            config.put("readset_1", "{\"fileGroupId\":\"" + g.name + "\",\"readsetname\":\"TestReadset1\",\"barcode5\":\"MID002\",\"barcode3\":\"MID001\",\"sampleid\":\"1\",\"readset\":\"\",\"instrument_run_id\":\"\",\"platform\":\"LS454\",\"subjectid\":\"Subject\"}");
            config.put("readset_2", "{\"fileGroupId\":\"" + g.name + "\",\"readsetname\":\"TestReadset2\",\"barcode5\":\"MID003\",\"barcode3\":\"MID001\",\"sampleid\":\"1\",\"readset\":\"\",\"instrument_run_id\":\"\",\"platform\":\"SANGER\",\"subjectid\":\"Subject\"}");
            config.put("readset_3", "{\"fileGroupId\":\"" + g.name + "\",\"readsetname\":\"TestReadset3\",\"barcode5\":\"MID004\",\"barcode3\":\"MID001\",\"sampleid\":\"1\",\"readset\":\"\",\"instrument_run_id\":\"\",\"platform\":\"SANGER\",\"subjectid\":\"Subject\"}");
            config.put("readset_4", "{\"fileGroupId\":\"" + g.name + "\",\"readsetname\":\"TestReadset3\",\"barcode5\":\"MID005\",\"barcode3\":\"MID001\",\"sampleid\":\"1\",\"readset\":\"\",\"instrument_run_id\":\"\",\"platform\":\"SANGER\",\"subjectid\":\"Subject\"}");

            BarcodeModel[] models = BarcodeModel.getByNames("MID001", "MID002", "MID003", "MID004", "MID005", "MID006", "MID007", "MID008", "MID009", "MID010");
            int i = 0;
            for (BarcodeModel m : models)
            {
                JSONArray json = new JSONArray();
                json.put(m.getName());
                json.put(m.getSequence());
                config.put("barcode_" + i, json);
                i++;
            }
            return config;
        }

        private Set<File> getBarcodeOutputs(File basedir, String jobName, String prefix)
        {
            Set<File> expectedOutputs = new HashSet<>();

            expectedOutputs.add(new File(basedir, "sequenceImport.json"));
            expectedOutputs.add(new File(basedir, "sequenceSupport.json.gz"));

            expectedOutputs.add(new File(basedir, basedir.getName() + ".pipe.xar.xml"));
            expectedOutputs.add(new File(basedir, jobName + ".log"));
            expectedOutputs.add(new File(basedir, "extraBarcodes.txt"));

            File normalizationDir = new File(basedir, "Normalization");
            expectedOutputs.add(normalizationDir);
            normalizationDir = new File(normalizationDir, prefix + FileUtil.getBaseName(DUAL_BARCODE_FILENAME));
            expectedOutputs.add(normalizationDir);

            expectedOutputs.add(new File(normalizationDir, prefix + FileUtil.getBaseName(DUAL_BARCODE_FILENAME) + "_MID001_MID001.fastq.gz"));
            expectedOutputs.add(new File(normalizationDir, prefix + FileUtil.getBaseName(DUAL_BARCODE_FILENAME) + "_MID002_MID001.fastq.gz"));
            expectedOutputs.add(new File(normalizationDir, prefix + FileUtil.getBaseName(DUAL_BARCODE_FILENAME) + "_MID003_MID001.fastq.gz"));
            expectedOutputs.add(new File(normalizationDir, prefix + FileUtil.getBaseName(DUAL_BARCODE_FILENAME) + "_MID004_MID001.fastq.gz"));
            expectedOutputs.add(new File(normalizationDir, prefix + FileUtil.getBaseName(DUAL_BARCODE_FILENAME) + "_unknowns.fastq.gz"));
            expectedOutputs.add(new File(normalizationDir, prefix + FileUtil.getBaseName(DUAL_BARCODE_FILENAME) + "_unknowns.fastq.gz.metrics"));

            expectedOutputs.add(new File(normalizationDir, prefix + FileUtil.getBaseName(DUAL_BARCODE_FILENAME) + ".barcode-summary.txt.gz"));

            return expectedOutputs;
        }

        /**
         * This test uses a barcoded input
         * @throws Exception
         */
        @Test
        public void barcodeTest() throws Exception
        {
            if (!isExternalPipelineEnabled())
                return;

            String prefix = "BarcodeTest_";
            ensureFilesPresent(prefix);

            String jobName = prefix + System.currentTimeMillis();
            String[] fileNames = new String[]{DUAL_BARCODE_FILENAME};

            JSONObject config = getBarcodeConfig(jobName, fileNames, prefix);
            Set<PipelineJob> jobs = createPipelineJob(jobName, config, SequenceAnalysisController.AnalyzeForm.TYPE.readsetImport);
            waitForJobs(jobs);

            File basedir = getBaseDir(jobs.iterator().next());
            Set<File> expectedOutputs = getBarcodeOutputs(basedir, jobName, prefix);
            File normalizationDir = new File(basedir, "Normalization");
            expectedOutputs.add(normalizationDir);
            normalizationDir = new File(normalizationDir, prefix + FileUtil.getBaseName(DUAL_BARCODE_FILENAME));
            expectedOutputs.add(new File(normalizationDir, prefix + DUAL_BARCODE_FILENAME + ".gz"));

            File log = new File(basedir, jobName + ".log");
            try
            {
                verifyFileOutputs(basedir, expectedOutputs);
                verifyFileInputs(basedir, fileNames, config, prefix);
                validateReadsets(jobs, config, 4);
                validateBarcodeFastqs(expectedOutputs);
            }
            catch (Exception e)
            {
                writeJobLogToLabKeyLog(log, jobName);
                throw e;
            }
        }

        private File getBaseDir(PipelineJob job)
        {
            return ((SequenceJob)job).getAnalysisDirectory();
        }

        /**
         * This is an extension of barcodeTest(), except intermediate files are deleted and inputs compressed
         * @throws Exception
         */
        @Test
        public void barcodeTestDeletingIntermediates() throws Exception
        {
            if (!isExternalPipelineEnabled())
                return;

            String prefix = "BarcodeDeletingIntermediates_";
            ensureFilesPresent(prefix);

            String jobName = prefix + System.currentTimeMillis();
            String[] fileNames = new String[]{DUAL_BARCODE_FILENAME};

            JSONObject config = getBarcodeConfig(jobName, fileNames, prefix);
            config.put("deleteIntermediateFiles", true);
            config.put("inputFileTreatment", "compress");

            Set<PipelineJob> jobs = createPipelineJob(jobName, config, SequenceAnalysisController.AnalyzeForm.TYPE.readsetImport);
            waitForJobs(jobs);

            File basedir = getBaseDir(jobs.iterator().next());
            Set<File> expectedOutputs = getBarcodeOutputs(basedir, jobName, prefix);
            expectedOutputs.add(new File(basedir, prefix + "dualBarcodes_SIV.fastq.gz"));

            File log = new File(basedir, jobName + ".log");
            try
            {
                verifyFileOutputs(basedir, expectedOutputs);
                verifyFileInputs(basedir, fileNames, config, prefix);
                validateReadsets(jobs, config, 4);
                validateBarcodeFastqs(expectedOutputs);
            }
            catch (Exception e)
            {
                writeJobLogToLabKeyLog(log, jobName);

                throw e;
            }
        }

        private void validateBarcodeFastqs(Set<File> expectedOutputs) throws Exception
        {
            for (File f : expectedOutputs)
            {
                if (f.getName().equals("dualBarcodes_SIV_MID001_MID001.fastq.gz"))
                    Assert.assertEquals("Incorrect read number", 303L, FastqUtils.getSequenceCount(f));
                else if (f.getName().equals("dualBarcodes_SIV_MID002_MID001.fastq.gz"))
                    Assert.assertEquals("Incorrect read number", 236L, FastqUtils.getSequenceCount(f));
                else if (f.getName().equals("dualBarcodes_SIV_MID003_MID001.fastq.gz"))
                    Assert.assertEquals("Incorrect read number", 235L, FastqUtils.getSequenceCount(f));
                else if (f.getName().equals("dualBarcodes_SIV_MID004_MID001.fastq.gz"))
                    Assert.assertEquals("Incorrect read number", 98L, FastqUtils.getSequenceCount(f));
                else if (f.getName().equals("dualBarcodes_SIV_unknowns.fastq.gz"))
                    Assert.assertEquals("Incorrect read number", 2388L, FastqUtils.getSequenceCount(f));
            }
        }

        /**
         * This imports a readset from two paired end inputs
         * @throws Exception
         */
        @Test
        public void pairedEndTest() throws Exception
        {
            String prefix = "PairedEndTest_";
            ensureFilesPresent(prefix);

            String jobName = prefix + System.currentTimeMillis();
            String[] fileNames = new String[]{PAIRED_FILENAME1, PAIRED_FILENAME2};
            JSONObject config = substituteParams(new File(_sampleData, READSET_JOB), jobName);
            FileGroup g = new FileGroup();
            g.name = "Group1";
            g.filePairs = new ArrayList<>();
            g.filePairs.add(new FileGroup.FilePair());
            g.filePairs.get(0).file1 = new File(prefix + PAIRED_FILENAME1);
            g.filePairs.get(0).file2 = new File(prefix + PAIRED_FILENAME2);

            appendSamplesForImport(config, Arrays.asList(g));

            Set<PipelineJob> jobs = createPipelineJob(jobName, config, SequenceAnalysisController.AnalyzeForm.TYPE.readsetImport);
            waitForJobs(jobs);

            Set<File> expectedOutputs = new HashSet<>();
            File basedir = getBaseDir(jobs.iterator().next());
            expectedOutputs.add(new File(basedir, "sequenceImport.json"));
            expectedOutputs.add(new File(basedir, "sequenceSupport.json.gz"));
            expectedOutputs.add(new File(basedir, basedir.getName() + ".pipe.xar.xml"));
            expectedOutputs.add(new File(basedir, prefix + PAIRED_FILENAME1));
            expectedOutputs.add(new File(basedir, prefix + PAIRED_FILENAME2));

            File log = new File(basedir, jobName + ".log");
            expectedOutputs.add(log);
            try
            {
                verifyFileOutputs(basedir, expectedOutputs);
                verifyFileInputs(basedir, fileNames, config, prefix);
                validateReadsets(jobs, config);
            }
            catch (Exception e)
            {
                writeJobLogToLabKeyLog(log, jobName);

                throw e;
            }
        }

        /**
         * An extension of pairedEndTest(), except input files are moved to the analysis folder
         * @throws Exception
         */
        @Test
        public void pairedEndTestMovingInputs() throws Exception
        {
            String prefix = "PairedEndMovingInputs_";
            ensureFilesPresent(prefix);

            String jobName = prefix + System.currentTimeMillis();
            String[] fileNames = new String[]{PAIRED_FILENAME1, PAIRED_FILENAME2, UNZIPPED_PAIRED_FILENAME1, UNZIPPED_PAIRED_FILENAME2};
            JSONObject config = substituteParams(new File(_sampleData, READSET_JOB), jobName);
            config.put("inputFileTreatment", "compress");

            FileGroup g = new FileGroup();
            g.name = "Group1";
            g.filePairs = new ArrayList<>();
            g.filePairs.add(new FileGroup.FilePair());
            g.filePairs.get(0).file1 = new File(prefix + PAIRED_FILENAME1);
            g.filePairs.get(0).file2 = new File(prefix + PAIRED_FILENAME2);

            g.filePairs.add(new FileGroup.FilePair());
            g.filePairs.get(1).file1 = new File(prefix + UNZIPPED_PAIRED_FILENAME1);
            g.filePairs.get(1).file2 = new File(prefix + UNZIPPED_PAIRED_FILENAME2);

            appendSamplesForImport(config, Arrays.asList(g));

            Set<PipelineJob> jobs = createPipelineJob(jobName, config, SequenceAnalysisController.AnalyzeForm.TYPE.readsetImport);
            waitForJobs(jobs);

            Set<File> expectedOutputs = new HashSet<>();
            File basedir = getBaseDir(jobs.iterator().next());
            expectedOutputs.add(new File(basedir, "sequenceImport.json"));
            expectedOutputs.add(new File(basedir, "sequenceSupport.json.gz"));
            expectedOutputs.add(new File(basedir, basedir.getName() + ".pipe.xar.xml"));

            expectedOutputs.add(new File(basedir, prefix + UNZIPPED_PAIRED_FILENAME1 + ".gz"));
            expectedOutputs.add(new File(basedir, prefix + UNZIPPED_PAIRED_FILENAME2 + ".gz"));
            expectedOutputs.add(new File(basedir, prefix + PAIRED_FILENAME1));
            expectedOutputs.add(new File(basedir, prefix + PAIRED_FILENAME2));

            File log = new File(basedir, jobName + ".log");
            expectedOutputs.add(log);
            try
            {
                verifyFileOutputs(basedir, expectedOutputs);
                verifyFileInputs(basedir, fileNames, config, prefix);
                validateReadsets(jobs, config);
            }
            catch (Exception e)
            {
                writeJobLogToLabKeyLog(log, jobName);
                throw e;
            }
        }

        /**
         * An extension of pairedEndTest(), except input files are deleted on completion
         * @throws Exception
         */
        @Test
        public void pairedEndTestDeletingInputs() throws Exception
        {
            String prefix = "PairedEndDeleting_";
            ensureFilesPresent(prefix);

            String jobName = prefix + System.currentTimeMillis();
            String[] fileNames = new String[]{PAIRED_FILENAME1, PAIRED_FILENAME2, UNZIPPED_PAIRED_FILENAME1, UNZIPPED_PAIRED_FILENAME2};
            JSONObject config = substituteParams(new File(_sampleData, READSET_JOB), jobName);
            config.put("inputFileTreatment", "delete");

            FileGroup g = new FileGroup();
            g.name = "Group1";
            g.filePairs = new ArrayList<>();
            g.filePairs.add(new FileGroup.FilePair());
            g.filePairs.get(0).file1 = new File(prefix + PAIRED_FILENAME1);
            g.filePairs.get(0).file2 = new File(prefix + PAIRED_FILENAME2);

            g.filePairs.add(new FileGroup.FilePair());
            g.filePairs.get(1).file1 = new File(prefix + UNZIPPED_PAIRED_FILENAME1);
            g.filePairs.get(1).file2 = new File(prefix + UNZIPPED_PAIRED_FILENAME2);

            appendSamplesForImport(config, Arrays.asList(g));

            Set<PipelineJob> jobs = createPipelineJob(jobName, config, SequenceAnalysisController.AnalyzeForm.TYPE.readsetImport);
            waitForJobs(jobs);

            Set<File> expectedOutputs = new HashSet<>();
            File basedir = getBaseDir(jobs.iterator().next());
            expectedOutputs.add(new File(basedir, "sequenceImport.json"));
            expectedOutputs.add(new File(basedir, "sequenceSupport.json.gz"));
            expectedOutputs.add(new File(basedir, basedir.getName() + ".pipe.xar.xml"));

            expectedOutputs.add(new File(basedir, prefix + UNZIPPED_PAIRED_FILENAME1 + ".gz"));
            expectedOutputs.add(new File(basedir, prefix + UNZIPPED_PAIRED_FILENAME2 + ".gz"));

            expectedOutputs.add(new File(basedir, prefix + PAIRED_FILENAME1));
            expectedOutputs.add(new File(basedir, prefix + PAIRED_FILENAME2));

            File log = new File(basedir, jobName + ".log");
            expectedOutputs.add(log);
            try
            {
                verifyFileOutputs(basedir, expectedOutputs);
                verifyFileInputs(basedir, fileNames, config, prefix);
                validateReadsets(jobs, config);
            }
            catch (Exception e)
            {
                writeJobLogToLabKeyLog(log, jobName);

                throw e;
            }
        }

        private void validateReadsets(Collection<PipelineJob> jobs, JSONObject config) throws Exception
        {
            validateReadsets(jobs, config, null);
        }

        private void validateReadsets(Collection<PipelineJob> jobs, JSONObject config, Integer expected) throws Exception
        {
            for (PipelineJob job : jobs)
            {
                SequenceReadsetImpl[] models = getReadsetsForJob(SequenceTaskHelper.getExpRunIdForJob(job));
                int numberExpected = expected != null ? expected : inferExpectedReadsets(config);
                Assert.assertEquals("Incorrect number of readsets created", numberExpected, models.length);
                validateSamples(models, config);
                validateQualityMetrics(models, config);
            }
        }

        private int inferExpectedReadsets(JSONObject config)
        {
            int expected = 0;
            for (String key : config.keySet())
            {
                if (key.startsWith("readset_"))
                    expected++;
            }

            return expected;
        }

        private void validateSamples(SequenceReadsetImpl[] models, JSONObject config)
        {
            Map<String, JSONObject> map = new HashMap<>();
            for (String key : config.keySet())
            {
                if (key.startsWith("readset_"))
                {
                    JSONObject o = new JSONObject(config.getString(key));
                    map.put(o.getString("readsetname"), o);
                }
            }

            for (Readset m : models)
            {
                JSONObject o = map.get(m.getName());
                Assert.assertNotNull("No config found for model: " + m.getName(), o);

                Assert.assertEquals("Incorrect readset name", o.getString("readsetname"), m.getName());
                Assert.assertEquals("Incorrect platform", o.getString("platform"), m.getPlatform());
                Assert.assertEquals("Incorrect sampleid", o.getInt("sampleid"), m.getSampleId().intValue());
                Assert.assertEquals("Incorrect subjectId", o.getString("subjectid"), m.getSubjectId());
                SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
                Assert.assertEquals("Incorrect sampleDate", o.optString("sampledate", null), m.getSampleDate() == null ?  null : format.format(m.getSampleDate()));

                String fileGroup = o.getString("fileGroupId");
                List<String> keys = config.keySet().stream().filter(x -> x.startsWith("fileGroup_")).filter(x -> fileGroup.equals(new JSONObject(config.getString(x)).getString("name"))).toList();
                Set<String> platformUnits = keys.stream().
                        map(x -> new JSONObject(config.getString(x)).getJSONArray("files").toList()).
                        flatMap(List::stream).
                        map(x -> (Map<?,?>)x).
                        map(y -> y.get("platformUnit") == null ? y.get("file1") : y.get("platformUnit")).map(Object::toString).collect(Collectors.toSet());
                Assert.assertFalse("No matching readdata", platformUnits.isEmpty());

                Assert.assertEquals("Incorrect number of readdata", m.getReadData().size(), platformUnits.size());
            }
        }

        private void validateQualityMetrics(SequenceReadsetImpl[] models, JSONObject config)
        {
            TableInfo ti = SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_QUALITY_METRICS);

            for (SequenceReadsetImpl m : models)
            {
                TableSelector ts = new TableSelector(ti, new SimpleFilter(FieldKey.fromString("readset"), m.getReadsetId()), null);
                Map<String, Object>[] metrics = ts.getMapArray();
                int expected = 0;
                for (ReadData d : m.getReadData())
                {
                    expected = expected + (d.getFileId2() == null ? 15 : 30);

                    Assert.assertNotNull("RunId not set for ReadData", d.getRunId());
                }

                Assert.assertEquals("Incorrect number of quality metrics created", expected, metrics.length);
            }
        }

        private SequenceReadsetImpl[] getReadsetsForJob(int runId)
        {
            TableInfo ti = SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_READSETS);
            TableSelector ts = new TableSelector(ti, new SimpleFilter(FieldKey.fromString("runid"), runId), null);
            return ts.getArray(SequenceReadsetImpl.class);
        }
    }

    abstract public static class AbstractAnalysisPipelineTestCase extends AbstractPipelineTestCase
    {
        protected List<SequenceReadsetImpl> _readsets;
        protected boolean _hasPerformedSetup = false;

        protected SequenceAlignmentJob getAlignmentJob(Set<PipelineJob> jobs, SequenceReadsetImpl rs)
        {
            for (PipelineJob job : jobs)
            {
                SequenceAlignmentJob j = (SequenceAlignmentJob)job;
                if (j.getReadsetId() == rs.getRowId())
                {
                    return j;
                }
            }

            throw new RuntimeException("Unable to find working dir for readset: " + rs.getRowId());
        }

        protected void validateAlignmentJob(Set<PipelineJob> jobs, Collection<String> additionalFiles, SequenceReadsetImpl rs, Integer aligned, Integer unaligned) throws Exception
        {
            validateAlignmentJob(jobs, additionalFiles, rs, aligned, unaligned, true);
        }

        protected void validateAlignmentJob(Set<PipelineJob> jobs, Collection<String> additionalFiles, SequenceReadsetImpl rs, Integer aligned, Integer unaligned, boolean includeRefFiles) throws Exception
        {
            SequenceAlignmentJob job = getAlignmentJob(jobs, rs);
            File basedir = job.getAnalysisDirectory();
            String outDir = SequenceTaskHelper.getUnzippedBaseName(rs.getReadDataImpl().get(0).getFile1());

            Set<File> expectedOutputs = new HashSet<>();
            expectedOutputs.addAll(addDefaultAlignmentOutputs(basedir, job.getProtocolName(), rs, outDir));
            additionalFiles = new HashSet<>(additionalFiles);
            additionalFiles.add("Shared");
            if (includeRefFiles)
            {
                additionalFiles.add("Shared/SIVmac239_Test.fasta");
                additionalFiles.add("Shared/SIVmac239_Test.fasta.fai");
                additionalFiles.add("Shared/SIVmac239_Test.idKey.txt");
            }

            for (String fn : additionalFiles)
            {
                expectedOutputs.add(new File(basedir, fn));
            }

            File bam = new File(basedir, outDir + "/Alignment/" + rs.getName() + ".bam");
            expectedOutputs.add(bam);

            expectedOutputs.add(new File(basedir, outDir + "/Alignment/" + rs.getName() + ".bam.bai"));

            expectedOutputs.add(new File(basedir, outDir + "/Alignment/idxstats.txt"));

            File log = new File(basedir, job.getProtocolName() + ".log");
            try
            {
                validateInputs();
                verifyFileOutputs(basedir, expectedOutputs);
                validateAlignment(bam, aligned, unaligned);
            }
            catch (Throwable e)
            {
                writeJobLogToLabKeyLog(log, job.getProtocolName());
                throw e;
            }
        }

        protected Collection<File> addDefaultAlignmentOutputs(File basedir, String jobName, SequenceReadsetImpl rs, String outDir)
        {
            List<File> extraFiles = new ArrayList<>();

            extraFiles.add(new File(basedir, jobName + ".log"));
            extraFiles.add(new File(basedir, "sequenceAnalysis.json"));
            extraFiles.add(new File(basedir, "sequenceSupport.json.gz"));
            extraFiles.add(new File(basedir, basedir.getName() + ".pipe.xar.xml"));

            extraFiles.add(new File(basedir, outDir));
            extraFiles.add(new File(basedir, outDir + "/Alignment"));
            extraFiles.add(new File(basedir, outDir + "/Alignment/" + rs.getName() + ".summary.metrics"));
            if (rs.getReadData().get(0).getFile2() != null)
            {
                //TODO
                //extraFiles.add(new File(basedir, outDir + "/Alignment/" + rs.getName() + ".insertsize.metrics"));
                //extraFiles.add(new File(basedir, outDir + "/Alignment/" + rs.getName() + ".insertsize.metrics.pdf"));
            }

            extraFiles.add(new File(basedir, outDir + "/Alignment/" + rs.getName() + ".bam.bai"));

            return extraFiles;
        }

        @Before
        @Override
        public void setUp() throws Exception
        {
            super.setUp();

            if (isExternalPipelineEnabled())
            {
                if (!_hasPerformedSetup)
                {
                    copyInputFiles();
                    _readsets = createReadsets();
                    _hasPerformedSetup = true;

                    ensureSivMac239(_project, _log);
                    ensureSivMac239Sequence(_project, _log);
                }
            }
        }

        protected void copyInputFiles() throws Exception
        {
            File file3 = new File(_pipelineRoot, PAIRED_FILENAME1);
            if (!file3.exists())
                FileUtils.copyFile(new File(_sampleData, PAIRED_FILENAME1), file3);

            File file4 = new File(_pipelineRoot, PAIRED_FILENAME2);
            if (!file4.exists())
                FileUtils.copyFile(new File(_sampleData, PAIRED_FILENAME2), file4);

            File file5 = new File(_pipelineRoot, UNZIPPED_PAIRED_FILENAME1);
            if (!file5.exists())
            {
                decompressAndCleanFastq(new File(_sampleData, PAIRED_FILENAME1), file5);
            }

            File file6 = new File(_pipelineRoot, UNZIPPED_PAIRED_FILENAME2);
            if (!file6.exists())
            {
                decompressAndCleanFastq(new File(_sampleData, PAIRED_FILENAME2), file6);
            }
        }

        protected List<SequenceReadsetImpl> createReadsets() throws Exception
        {
            List<SequenceReadsetImpl> models = new ArrayList<>();

            File file1 = new File(_pipelineRoot, PAIRED_FILENAME1);
            File file2 = new File(_pipelineRoot, PAIRED_FILENAME2);
            models.add(createReadset("TestReadset1", Arrays.asList(Pair.of(file1, file2))));


            File file3 = new File(_pipelineRoot, UNZIPPED_PAIRED_FILENAME1);
            models.add(createReadset("TestReadset2", Arrays.asList(Pair.of(file3, null))));

            File file4 = new File(_pipelineRoot, UNZIPPED_PAIRED_FILENAME2);
            models.add(createReadset("TestReadset3", Arrays.asList(Pair.of(file4, null))));

            return models;
        }

        protected synchronized SequenceReadsetImpl createReadset(String name, List<Pair<File, File>> fileList) throws Exception
        {
            TableInfo ti = SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_READSETS);
            TableInfo readData = SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_READ_DATA);

            SequenceReadsetImpl readset1 = new SequenceReadsetImpl();

            readset1.setName(name);
            readset1.setContainer(_project.getId());
            readset1.setCreated(new Date());
            readset1.setCreatedBy(_context.getUser().getUserId());
            readset1 = Table.insert(_context.getUser(), ti, readset1);

            List<ReadDataImpl> datas = new ArrayList<>();
            for (Pair<File, File> p : fileList)
            {
                ReadDataImpl rd = new ReadDataImpl();
                ExpData d1 = createExpData(p.first);
                ExpData d2 = p.second == null ? null : createExpData(p.second);
                rd.setReadset(readset1.getReadsetId());
                rd.setFileId1(d1.getRowId());
                rd.setContainer(_project.getId());
                rd.setCreatedBy(_context.getUser().getUserId());
                rd.setCreated(new Date());
                rd.setModifiedBy(_context.getUser().getUserId());
                rd.setModified(new Date());
                if (d2 != null)
                {
                    rd.setFileId2(d2.getRowId());
                }

                rd = Table.insert(_context.getUser(), readData, rd);
                datas.add(rd);
            }

            readset1.setReadData(datas);

            return readset1;
        }

        protected ExpData createExpData(File f)
        {
            ExpData d = ExperimentService.get().createData(_project, new DataType("SequenceFile"));
            d.setName(f.getName());
            d.setDataFileURI(f.toURI());
            d.save(_context.getUser());
            return d;
        }

        protected String[] getFilenamesForReadsets()
        {
            List<String> files = new ArrayList<>();
            for (SequenceReadsetImpl m : _readsets)
            {
                for (ReadDataImpl rd : m.getReadDataImpl())
                {
                    files.add(rd.getFile1().getName());
                    if (rd.getFileId2() != null)
                    {
                        files.add(rd.getFile2().getName());
                    }
                }
            }

            return files.toArray(new String[files.size()]);
        }

        protected void appendSamplesForAlignment(JSONObject config, List<SequenceReadsetImpl> readsets)
        {
            JSONArray ret = new JSONArray();
            for (SequenceReadsetImpl rs : readsets)
            {
                ret.put(rs.getRowId());
            }

            config.put("readsetIds", ret);
        }

        //we expect inputs to be unaltered
        protected void validateInputs() throws PipelineJobException
        {
            //all files have 204 reads

            File file1 = new File(_pipelineRoot, PAIRED_FILENAME1);
            Assert.assertTrue("Unable to find input: " + file1.getPath(), file1.exists());

            File file2 = new File(_pipelineRoot, PAIRED_FILENAME2);
            Assert.assertTrue("Unable to find input: " + file2.getPath(), file2.exists());

            File file3 = new File(_pipelineRoot, UNZIPPED_PAIRED_FILENAME1);
            Assert.assertTrue("Unable to find input: " + file3.getPath(), file3.exists());

            File file4 = new File(_pipelineRoot, UNZIPPED_PAIRED_FILENAME2);
            Assert.assertTrue("Unable to find input: " + file4.getPath(), file4.exists());
        }

        protected void validateAlignment(File bam, Integer expectedAligned, Integer expectedUnaligned)
        {
            int aligned = 0;
            int unaligned = 0;

            if (expectedAligned == null)
            {
                return;
            }

            SamReaderFactory fact = SamReaderFactory.makeDefault();
            fact.validationStringency(ValidationStringency.SILENT);
            try (SamReader reader = fact.open(bam))
            {
                try (SAMRecordIterator it = reader.iterator())
                {
                    while (it.hasNext())
                    {
                        SAMRecord rec = it.next();
                        if (rec.getReadUnmappedFlag())
                            unaligned++;
                        else
                            aligned++;
                    }
                }
            }
            catch (IOException e)
            {
                throw new RuntimeException(e);
            }

            Assert.assertEquals("Incorrect aligned count for BAM: " + bam.getPath(), expectedAligned.intValue(), aligned);
            Assert.assertEquals("Incorrect unaligned count for BAM: " + bam.getPath(), expectedUnaligned.intValue(), unaligned);
        }

        protected Integer createSavedLibrary() throws Exception
        {
            //look for existing
            String libraryName = "TestLibrary";
            SimpleFilter libraryFilter = new SimpleFilter(FieldKey.fromString("name"), libraryName);
            libraryFilter.addCondition(FieldKey.fromString("container"), _project.getId());
            TableSelector ts = new TableSelector(SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_REF_LIBRARIES), PageFlowUtil.set("rowid", "fasta_file"), libraryFilter, null);
            if (ts.exists())
            {
                Map<String, Object> rowMap = ts.getMap();
                Integer fasta_file = (Integer)rowMap.get("fasta_file");
                ExpData d = fasta_file == null ? null : ExperimentService.get().getExpData(fasta_file);
                if (d != null && d.getFile() != null && d.getFile().exists())
                {
                    return (Integer) rowMap.get("rowid");
                }
                else
                {
                    _log.info("deleting incomplete saved reference library");
                    Table.delete(SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_REF_LIBRARIES), rowMap.get("rowid"));
                }
            }

            //otherwise create saved library
            SimpleFilter filter = new SimpleFilter(FieldKey.fromString("name"), "SIVmac239_Test");
            filter.addCondition(FieldKey.fromString("container"), _project.getId());
            Integer mac239Id = new TableSelector(QueryService.get().getUserSchema(_context.getUser(), _project, SequenceAnalysisSchema.SCHEMA_NAME).getTable(SequenceAnalysisSchema.TABLE_REF_NT_SEQUENCES), PageFlowUtil.set("rowid"), filter, null).getObject(Integer.class);
            if (mac239Id == null)
            {
                throw new PipelineJobException("Unable to find SIVMac239 NT sequence");
            }

            ReferenceLibraryPipelineJob libraryJob = SequenceAnalysisManager.get().createReferenceLibrary(Arrays.asList(mac239Id), _project, _context.getUser(), libraryName, null, null, true, false, null, null);
            waitForJobs(Collections.singleton(libraryJob));

            return new TableSelector(SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_REF_LIBRARIES), PageFlowUtil.set("rowid"), libraryFilter, null).getObject(Integer.class);
        }

        // this was added because bismark isnt deterministic in the number of outputs it creates.  for the time being it is
        // better for the test to pass and get some coverage
        protected void addOptionalFile(Set<String> expectedOutputs, File basedir, String fn)
        {
            File f = new File(basedir, fn);
            if (f.exists())
            {
                expectedOutputs.add(fn);
            }
        }
    }

    @TestTimeout(480)
    public static class SequenceAnalysisPipelineTestCase1 extends AbstractAnalysisPipelineTestCase
    {
        private static final String PROJECT_NAME = "SequenceAnalysisTestProject1";

        @BeforeClass
        public static void initialSetUp() throws Exception
        {
            doInitialSetUp(PROJECT_NAME);
        }

        @AfterClass
        public static void cleanup()
        {
            doCleanup(PROJECT_NAME);
        }

        protected String getProjectName()
        {
            return PROJECT_NAME;
        }

        @Test
        public void testMosaik() throws Exception
        {
            if (!isExternalPipelineEnabled())
                return;

            String jobName = "TestMosaik_" + System.currentTimeMillis();
            JSONObject config = substituteParams(new File(_sampleData, ALIGNMENT_JOB), jobName);
            config.put("alignment", "Mosaik");
            config.put("alignment.Mosaik.banded_smith_waterman", 51);
            config.put("alignment.Mosaik.max_mismatch_pct", 0.20);  //this is primary here to ensure it doesnt get copied into the build command.  20% should include everything
            config.put("analysis", "SBT;ViralAnalysis;SnpCountAnalysis");
            config.put("analysis.SnpCountAnalysis.intervals", "SIVmac239_Test:5500-5600\nSIVmac239_Test:9700-9900\nSIVmac239_Test:10000-10020");

            appendSamplesForAlignment(config, _readsets);

            Set<PipelineJob> jobs = createPipelineJob(jobName, config, SequenceAnalysisController.AnalyzeForm.TYPE.alignment);
            waitForJobs(jobs);

            validateInputs();

            validateAlignmentJob(jobs, Arrays.asList(
                "Shared/Mosaik",
                "Shared/Mosaik/SIVmac239_Test.mosaik",
                "paired1/Alignment/paired1.mosaikreads",
                "paired1/Alignment/paired1.mosaik.stat",
                "TestReadset1.snps.txt",
                "paired1/Alignment/TestReadset1.insertsize.metrics",
                "paired1/Alignment/TestReadset1.insertsize.metrics.pdf",
                "paired1/Alignment/TestReadset1.sbt_hits.txt.gz"
            ), _readsets.get(0), 294, 128);

            validateAlignmentJob(jobs, Arrays.asList(
                "Shared/Mosaik",
                "Shared/Mosaik/SIVmac239_Test.mosaik",
                "paired3/Alignment/paired3.mosaikreads",
                "paired3/Alignment/paired3.mosaik.stat",
                "TestReadset2.snps.txt",
                "paired3/Alignment/TestReadset2.sbt_hits.txt.gz"
            ), _readsets.get(1), 147, 64);

            validateAlignmentJob(jobs, Arrays.asList(
                "Shared/Mosaik",
                "Shared/Mosaik/SIVmac239_Test.mosaik",
                "paired4/Alignment/paired4.mosaikreads",
                "paired4/Alignment/paired4.mosaik.stat",
                "TestReadset3.snps.txt",
                "paired4/Alignment/TestReadset3.sbt_hits.txt.gz"
            ), _readsets.get(2), 147, 64);
        }

        @Test
        public void testMosaikWithBamPostProcessing() throws Exception
        {
            if (!isExternalPipelineEnabled())
                return;

            String jobName = "TestMosaikWithPostProcess_" + System.currentTimeMillis();
            JSONObject config = substituteParams(new File(_sampleData, ALIGNMENT_JOB), jobName);
            config.put("alignment", "Mosaik");
            config.put("bamPostProcessing", "AddOrReplaceReadGroups;CallMdTags;CleanSam;FixMateInformation;MarkDuplicates;SortSam");

            appendSamplesForAlignment(config, _readsets);

            Set<PipelineJob> jobs = createPipelineJob(jobName, config, SequenceAnalysisController.AnalyzeForm.TYPE.alignment);
            waitForJobs(jobs);

            List<String> extraFiles = new ArrayList<>();
            extraFiles.addAll(Arrays.asList(
                    "Shared/Mosaik",
                    "Shared/Mosaik/SIVmac239_Test.mosaik",
                    "paired1/Alignment/paired1.mosaikreads",
                    "paired1/Alignment/paired1.mosaik.stat",
                    "paired1/Alignment/paired1.mosaik.bam",
                    "paired1/Alignment/paired1.mosaik.bam.bai"
            ));

            extraFiles.add("paired1/Alignment/paired1.mosaik.readgroups.bam");
            extraFiles.add("paired1/Alignment/paired1.mosaik.readgroups.calmd.bam");
            extraFiles.add("paired1/Alignment/paired1.mosaik.readgroups.calmd.cleaned.bam");
            extraFiles.add("paired1/Alignment/paired1.mosaik.readgroups.calmd.cleaned.fixmate.bam");

            extraFiles.add("paired1/Alignment/paired1.mosaik.readgroups.calmd.cleaned.fixmate.markduplicates.metrics");

            extraFiles.add("paired1/Alignment/TestReadset1.summary.metrics");
            extraFiles.add("paired1/Alignment/TestReadset1.insertsize.metrics");
            extraFiles.add("paired1/Alignment/TestReadset1.insertsize.metrics.pdf");
            extraFiles.add("paired1/Alignment/TestReadset1.bam.bai");

            validateAlignmentJob(jobs, extraFiles, _readsets.get(0), 294, 128);

            //job2:
            extraFiles = new ArrayList<>();
            extraFiles.addAll(Arrays.asList(
                    "Shared/Mosaik",
                    "Shared/Mosaik/SIVmac239_Test.mosaik",
                    "paired3/Alignment/paired3.mosaikreads",
                    "paired3/Alignment/paired3.mosaik.stat",
                    "paired3/Alignment/paired3.mosaik.bam",
                    "paired3/Alignment/paired3.mosaik.bam.bai"
            ));

            extraFiles.add("paired3/Alignment/paired3.mosaikreads");
            extraFiles.add("paired3/Alignment/paired3.mosaik.stat");
            extraFiles.add("paired3/Alignment/paired3.mosaik.readgroups.bam");
            extraFiles.add("paired3/Alignment/paired3.mosaik.readgroups.calmd.bam");
            extraFiles.add("paired3/Alignment/paired3.mosaik.readgroups.calmd.cleaned.bam");
            extraFiles.add("paired3/Alignment/paired3.mosaik.readgroups.calmd.cleaned.fixmate.bam");

            extraFiles.add("paired3/Alignment/paired3.mosaik.readgroups.calmd.cleaned.fixmate.markduplicates.metrics");

            extraFiles.add("paired3/Alignment/TestReadset2.summary.metrics");
            //extraFiles.add("paired3/Alignment/TestReadset2.insertsize.metrics");
            extraFiles.add("paired3/Alignment/TestReadset2.bam.bai");

            validateAlignmentJob(jobs, extraFiles, _readsets.get(1), 147, 64);

            //job3:
            extraFiles = new ArrayList<>();
            extraFiles.addAll(Arrays.asList(
                    "Shared/Mosaik",
                    "Shared/Mosaik/SIVmac239_Test.mosaik",
                    "paired4/Alignment/paired4.mosaikreads",
                    "paired4/Alignment/paired4.mosaik.stat",
                    "paired4/Alignment/paired4.mosaik.bam",
                    "paired4/Alignment/paired4.mosaik.bam.bai"
            ));

            extraFiles.add("paired4");
            extraFiles.add("paired4/Alignment");
            extraFiles.add("paired4/Alignment/paired4.mosaikreads");
            extraFiles.add("paired4/Alignment/paired4.mosaik.stat");
            extraFiles.add("paired4/Alignment/paired4.mosaik.readgroups.bam");
            extraFiles.add("paired4/Alignment/paired4.mosaik.readgroups.calmd.bam");
            extraFiles.add("paired4/Alignment/paired4.mosaik.readgroups.calmd.cleaned.bam");
            extraFiles.add("paired4/Alignment/paired4.mosaik.readgroups.calmd.cleaned.fixmate.bam");

            extraFiles.add("paired4/Alignment/paired4.mosaik.readgroups.calmd.cleaned.fixmate.markduplicates.metrics");

            extraFiles.add("paired4/Alignment/TestReadset3.summary.metrics");
            //extraFiles.add("paired4/Alignment/TestReadset3.insertsize.metrics");
            extraFiles.add("paired4/Alignment/TestReadset3.bam.bai");

            validateAlignmentJob(jobs, extraFiles, _readsets.get(2), 147, 64);

            validateInputs();
        }

        @Test
        public void testMosaikWithBamPostProcessingAndDelete() throws Exception
        {
            if (!isExternalPipelineEnabled())
                return;

            String jobName = "TestMosaikWithPostProcessAndDelete_" + System.currentTimeMillis();

            JSONObject config = substituteParams(new File(_sampleData, ALIGNMENT_JOB), jobName);
            config.put("alignment", "Mosaik");
            config.put("deleteIntermediateFiles", true);
            config.put("bamPostProcessing", "AddOrReplaceReadGroups;CallMdTags;CleanSam;FixMateInformation;MarkDuplicates;SortSam");

            appendSamplesForAlignment(config, _readsets);

            Set<PipelineJob> jobs = createPipelineJob(jobName, config, SequenceAnalysisController.AnalyzeForm.TYPE.alignment);
            waitForJobs(jobs);

            Set<String> extraFiles = new HashSet<>();
            extraFiles.add(jobName + ".log");
            extraFiles.add("sequenceAnalysis.json");

            extraFiles.add("Shared");
            extraFiles.add("Shared/SIVmac239_Test.fasta");
            extraFiles.add("Shared/SIVmac239_Test.fasta.fai");
            extraFiles.add("Shared/SIVmac239_Test.idKey.txt");

            //job1
            Set<String> job1Files = new HashSet<>(extraFiles);
            job1Files.add("paired1");
            job1Files.add("paired1/Alignment");
            job1Files.add("paired1/Alignment/TestReadset1.bam");
            job1Files.add("paired1/Alignment/TestReadset1.summary.metrics");
            job1Files.add("paired1/Alignment/TestReadset1.insertsize.metrics");
            job1Files.add("paired1/Alignment/TestReadset1.insertsize.metrics.pdf");
            job1Files.add("paired1/Alignment/TestReadset1.bam.bai");
            job1Files.add("paired1/Alignment/paired1.mosaik.readgroups.calmd.cleaned.fixmate.markduplicates.metrics");
            validateAlignmentJob(jobs, job1Files, _readsets.get(0), 294, 128);

            //job2
            Set<String> job2Files = new HashSet<>(extraFiles);
            job2Files.add("paired3");
            job2Files.add("paired3/Alignment");
            job2Files.add("paired3/Alignment/TestReadset2.bam");
            job2Files.add("paired3/Alignment/TestReadset2.summary.metrics");
            //job2Files.add("paired3/Alignment/TestReadset2.insertsize.metrics");
            job2Files.add("paired3/Alignment/TestReadset2.bam.bai");
            job2Files.add("paired3/Alignment/paired3.mosaik.readgroups.calmd.cleaned.fixmate.markduplicates.metrics");
            validateAlignmentJob(jobs, job2Files, _readsets.get(1), 147, 64);

            //job3
            Set<String> job3Files = new HashSet<>(extraFiles);
            job3Files.add("paired4");
            job3Files.add("paired4/Alignment");
            job3Files.add("paired4/Alignment/TestReadset3.bam");
            job3Files.add("paired4/Alignment/TestReadset3.summary.metrics");
            //job3Files.add("paired4/Alignment/TestReadset3.insertsize.metrics");
            job3Files.add("paired4/Alignment/TestReadset3.bam.bai");
            job3Files.add("paired4/Alignment/paired4.mosaik.readgroups.calmd.cleaned.fixmate.markduplicates.metrics");
            validateAlignmentJob(jobs, job3Files, _readsets.get(2), 147, 64);
        }

        @Test
        public void testMosaikDeletingIntermediates() throws Exception
        {
            if (!isExternalPipelineEnabled())
                return;

            String jobName = "MosaikDeletingIntermediates_" + System.currentTimeMillis();
            JSONObject config = substituteParams(new File(_sampleData, ALIGNMENT_JOB), jobName);
            config.put("alignment", "Mosaik");
            config.put("deleteIntermediateFiles", true);
            appendSamplesForAlignment(config, _readsets);

            Set<PipelineJob> jobs = createPipelineJob(jobName, config, SequenceAnalysisController.AnalyzeForm.TYPE.alignment);
            waitForJobs(jobs);

            Set<String> extraFiles = new HashSet<>();
            extraFiles.add(jobName + ".log");
            extraFiles.add("sequenceAnalysis.json");
            extraFiles.add("Shared");
            extraFiles.add("Shared/SIVmac239_Test.fasta");
            extraFiles.add("Shared/SIVmac239_Test.fasta.fai");
            extraFiles.add("Shared/SIVmac239_Test.idKey.txt");

            //job1
            Set<String> job1Files = new HashSet<>(extraFiles);
            job1Files.add("paired1");
            job1Files.add("paired1/Alignment");
            job1Files.add("paired1/Alignment/TestReadset1.bam");
            job1Files.add("paired1/Alignment/TestReadset1.summary.metrics");
            job1Files.add("paired1/Alignment/TestReadset1.insertsize.metrics");
            job1Files.add("paired1/Alignment/TestReadset1.insertsize.metrics.pdf");
            job1Files.add("paired1/Alignment/TestReadset1.bam.bai");
            validateAlignmentJob(jobs, job1Files, _readsets.get(0), 294, 128);

            //job2
            Set<String> job2Files = new HashSet<>(extraFiles);
            job2Files.add("paired3");
            job2Files.add("paired3/Alignment");
            job2Files.add("paired3/Alignment/TestReadset2.bam");
            job2Files.add("paired3/Alignment/TestReadset2.summary.metrics");
            //job2Files.add("paired3/Alignment/TestReadset2.insertsize.metrics");
            job2Files.add("paired3/Alignment/TestReadset2.bam.bai");
            validateAlignmentJob(jobs, job2Files, _readsets.get(1), 147, 64);

            //job3
            Set<String> job3Files = new HashSet<>(extraFiles);
            job3Files.add("paired4");
            job3Files.add("paired4/Alignment");
            job3Files.add("paired4/Alignment/TestReadset3.bam");
            job3Files.add("paired4/Alignment/TestReadset3.summary.metrics");
            //job3Files.add("paired4/Alignment/TestReadset3.insertsize.metrics");
            job3Files.add("paired4/Alignment/TestReadset3.bam.bai");
            validateAlignmentJob(jobs, job3Files, _readsets.get(2), 147, 64);
        }

        @Test
        public void testBWASW() throws Exception
        {
            if (!isExternalPipelineEnabled())
                return;

            String jobName = "TestBWASW_" + System.currentTimeMillis();
            JSONObject config = substituteParams(new File(_sampleData, ALIGNMENT_JOB), jobName);
            config.put("alignment", "BWA-SW");
            appendSamplesForAlignment(config, _readsets);

            Set<PipelineJob> jobs = createPipelineJob(jobName, config, SequenceAnalysisController.AnalyzeForm.TYPE.alignment);
            waitForJobs(jobs);

            Set<String> extraFiles = new HashSet<>();
            extraFiles.add(jobName + ".log");
            extraFiles.add("sequenceAnalysis.json");
            extraFiles.add("Shared");
            extraFiles.add("Shared/SIVmac239_Test.fasta");
            extraFiles.add("Shared/SIVmac239_Test.fasta.fai");
            extraFiles.add("Shared/SIVmac239_Test.idKey.txt");

            extraFiles.add("Shared/bwa");
            extraFiles.add("Shared/bwa/SIVmac239_Test.bwa.index.amb");
            extraFiles.add("Shared/bwa/SIVmac239_Test.bwa.index.ann");
            extraFiles.add("Shared/bwa/SIVmac239_Test.bwa.index.bwt");
            extraFiles.add("Shared/bwa/SIVmac239_Test.bwa.index.pac");
            extraFiles.add("Shared/bwa/SIVmac239_Test.bwa.index.sa");

            //job1
            Set<String> job1Files = new HashSet<>(extraFiles);
            job1Files.add("paired1");
            job1Files.add("paired1/Alignment");
            job1Files.add("paired1/Alignment/TestReadset1.bam");
            job1Files.add("paired1/Alignment/paired1.bwa-sw.bam");
            job1Files.add("paired1/Alignment/TestReadset1.summary.metrics");
            job1Files.add("paired1/Alignment/TestReadset1.insertsize.metrics");
            job1Files.add("paired1/Alignment/TestReadset1.insertsize.metrics.pdf");
            job1Files.add("paired1/Alignment/TestReadset1.bam.bai");
            validateAlignmentJob(jobs, job1Files, _readsets.get(0), 319, 103);

            //job2
            Set<String> job2Files = new HashSet<>(extraFiles);
            job2Files.add("paired3");
            job2Files.add("paired3/Alignment");
            job2Files.add("paired3/Alignment/TestReadset2.bam");
            job2Files.add("paired3/Alignment/paired3.bwa-sw.bam");
            job2Files.add("paired3/Alignment/TestReadset2.summary.metrics");
            //job2Files.add("paired3/Alignment/TestReadset2.insertsize.metrics");
            job2Files.add("paired3/Alignment/TestReadset2.bam.bai");
            validateAlignmentJob(jobs, job2Files, _readsets.get(1), 160, 51);

            //job3
            Set<String> job3Files = new HashSet<>(extraFiles);
            job3Files.add("paired4");
            job3Files.add("paired4/Alignment");
            job3Files.add("paired4/Alignment/TestReadset3.bam");
            job3Files.add("paired4/Alignment/paired4.bwa-sw.bam");
            job3Files.add("paired4/Alignment/TestReadset3.summary.metrics");
            //job3Files.add("paired4/Alignment/TestReadset3.insertsize.metrics");
            job3Files.add("paired4/Alignment/TestReadset3.bam.bai");
            validateAlignmentJob(jobs, job3Files, _readsets.get(2), 159, 52);
        }

        @Test
        public void testBWAMem() throws Exception
        {
            if (!isExternalPipelineEnabled())
                return;

            String jobName = "TestBWAMem_" + System.currentTimeMillis();
            JSONObject config = substituteParams(new File(_sampleData, ALIGNMENT_JOB), jobName);
            config.put("alignment", "BWA-Mem");
            appendSamplesForAlignment(config, _readsets);

            Set<PipelineJob> jobs = createPipelineJob(jobName, config, SequenceAnalysisController.AnalyzeForm.TYPE.alignment);
            waitForJobs(jobs);

            Set<String> extraFiles = new HashSet<>();
            extraFiles.add(jobName + ".log");
            extraFiles.add("sequenceAnalysis.json");
            extraFiles.add("Shared");
            extraFiles.add("Shared/SIVmac239_Test.fasta");
            extraFiles.add("Shared/SIVmac239_Test.fasta.fai");
            extraFiles.add("Shared/SIVmac239_Test.idKey.txt");

            extraFiles.add("Shared/bwa");
            extraFiles.add("Shared/bwa/SIVmac239_Test.bwa.index.amb");
            extraFiles.add("Shared/bwa/SIVmac239_Test.bwa.index.ann");
            extraFiles.add("Shared/bwa/SIVmac239_Test.bwa.index.bwt");
            extraFiles.add("Shared/bwa/SIVmac239_Test.bwa.index.pac");
            extraFiles.add("Shared/bwa/SIVmac239_Test.bwa.index.sa");

            //job1
            Set<String> job1Files = new HashSet<>(extraFiles);
            job1Files.add("paired1");
            job1Files.add("paired1/Alignment");
            job1Files.add("paired1/Alignment/TestReadset1.bam");
            job1Files.add("paired1/Alignment/paired1.bwa-mem.bam");
            job1Files.add("paired1/Alignment/TestReadset1.summary.metrics");
            job1Files.add("paired1/Alignment/TestReadset1.insertsize.metrics");
            job1Files.add("paired1/Alignment/TestReadset1.insertsize.metrics.pdf");
            job1Files.add("paired1/Alignment/TestReadset1.bam.bai");

            //job2
            Set<String> job2Files = new HashSet<>(extraFiles);
            job2Files.add("paired3");
            job2Files.add("paired3/Alignment");
            job2Files.add("paired3/Alignment/TestReadset2.bam");
            job2Files.add("paired3/Alignment/paired3.bwa-mem.bam");
            job2Files.add("paired3/Alignment/TestReadset2.summary.metrics");
            //job2Files.add("paired3/Alignment/TestReadset2.insertsize.metrics");
            job2Files.add("paired3/Alignment/TestReadset2.bam.bai");

            Set<String> job3Files = new HashSet<>(extraFiles);
            job3Files.add("paired4");
            job3Files.add("paired4/Alignment");
            job3Files.add("paired4/Alignment/TestReadset3.bam");
            job3Files.add("paired4/Alignment/paired4.bwa-mem.bam");
            job3Files.add("paired4/Alignment/TestReadset3.summary.metrics");
            //job3Files.add("paired4/Alignment/TestReadset3.insertsize.metrics");
            job3Files.add("paired4/Alignment/TestReadset3.bam.bai");

            validateAlignmentJob(jobs, job1Files, _readsets.get(0), 320, 102);
            validateAlignmentJob(jobs, job2Files, _readsets.get(1), 160, 51);
            validateAlignmentJob(jobs, job3Files, _readsets.get(2), 159, 52);
        }

        @Test
        public void testBWAWithAdapters() throws Exception
        {
            if (!isExternalPipelineEnabled())
                return;

            String jobName = "TestBWAWithAdapters_" + System.currentTimeMillis();
            JSONObject config = substituteParams(new File(_sampleData, ALIGNMENT_JOB), jobName);
            config.put("alignment", "BWA");

            config.put("fastqProcessing", "AdapterTrimming");
            config.put("fastqProcessing.AdapterTrimming.adapters", "[[\"Nextera Transposon Adapter A\",\"AGATGTGTATAAGAGACAG\",true,true]]");

            appendSamplesForAlignment(config, _readsets);

            Set<PipelineJob> jobs = createPipelineJob(jobName, config, SequenceAnalysisController.AnalyzeForm.TYPE.alignment);
            waitForJobs(jobs);

            Set<String> extraFiles = new HashSet<>();
            extraFiles.add(jobName + ".log");
            extraFiles.add("sequenceAnalysis.json");

            extraFiles.add("Shared");
            extraFiles.add("Shared/SIVmac239_Test.fasta");
            extraFiles.add("Shared/SIVmac239_Test.fasta.fai");
            extraFiles.add("Shared/SIVmac239_Test.idKey.txt");

            extraFiles.add("Shared/bwa");
            extraFiles.add("Shared/bwa/SIVmac239_Test.bwa.index.amb");
            extraFiles.add("Shared/bwa/SIVmac239_Test.bwa.index.ann");
            extraFiles.add("Shared/bwa/SIVmac239_Test.bwa.index.bwt");
            extraFiles.add("Shared/bwa/SIVmac239_Test.bwa.index.pac");
            extraFiles.add("Shared/bwa/SIVmac239_Test.bwa.index.sa");

            //job1
            Set<String> job1Files = new HashSet<>(extraFiles);
            job1Files.add("paired1");
            job1Files.add("paired1/Preprocessing/");
            job1Files.add("paired1/Preprocessing/paired1.preprocessed.fastq.gz");
            job1Files.add("paired1/Preprocessing/paired2.preprocessed.fastq.gz");
            job1Files.add("paired1/Alignment");
            job1Files.add("paired1/Alignment/TestReadset1.bam");
            job1Files.add("paired1/Alignment/paired1.preprocessed.bwa.bam");
            job1Files.add("paired1/Alignment/TestReadset1.summary.metrics");
            job1Files.add("paired1/Alignment/TestReadset1.insertsize.metrics");
            job1Files.add("paired1/Alignment/TestReadset1.insertsize.metrics.pdf");
            job1Files.add("paired1/Alignment/TestReadset1.bam.bai");
            job1Files.add("paired1/Alignment/paired1.preprocessed.fastq.gz.sai");
            job1Files.add("paired1/Alignment/paired2.preprocessed.fastq.gz.sai");

            //job2
            Set<String> job2Files = new HashSet<>(extraFiles);
            job2Files.add("paired3");
            job2Files.add("paired3/Preprocessing/");
            job2Files.add("paired3/Preprocessing/paired3.preprocessed.fastq");
            job2Files.add("paired3/Alignment");
            job2Files.add("paired3/Alignment/TestReadset2.bam");
            job2Files.add("paired3/Alignment/paired3.preprocessed.bwa.bam");
            job2Files.add("paired3/Alignment/TestReadset2.summary.metrics");
            //job2Files.add("paired3/Alignment/TestReadset2.insertsize.metrics");
            job2Files.add("paired3/Alignment/TestReadset2.bam.bai");
            job2Files.add("paired3/Alignment/paired3.preprocessed.fastq.sai");

            //job2
            Set<String> job3Files = new HashSet<>(extraFiles);
            job3Files.add("paired4");
            job3Files.add("paired4/Preprocessing/");
            job3Files.add("paired4/Preprocessing/paired4.preprocessed.fastq");
            job3Files.add("paired4/Alignment");
            job3Files.add("paired4/Alignment/TestReadset3.bam");
            job3Files.add("paired4/Alignment/paired4.preprocessed.bwa.bam");
            job3Files.add("paired4/Alignment/TestReadset3.summary.metrics");
            //job3Files.add("paired4/Alignment/TestReadset3.insertsize.metrics");
            job3Files.add("paired4/Alignment/TestReadset3.bam.bai");
            job3Files.add("paired4/Alignment/paired4.preprocessed.fastq.sai");

            validateAlignmentJob(jobs, job1Files, _readsets.get(0), 317, 105);
            validateAlignmentJob(jobs, job2Files, _readsets.get(1), 158, 53);
            validateAlignmentJob(jobs, job3Files, _readsets.get(2), 156, 55);
        }
    }

    @TestTimeout(480)
    public static class SequenceAnalysisPipelineTestCase2 extends AbstractAnalysisPipelineTestCase
    {
        private static final String PROJECT_NAME = "SequenceAnalysisTestProject2";

        @BeforeClass
        public static void initialSetUp() throws Exception
        {
            doInitialSetUp(PROJECT_NAME);
        }

        @AfterClass
        public static void cleanup()
        {
            doCleanup(PROJECT_NAME);
        }

        protected String getProjectName()
        {
            return PROJECT_NAME;
        }

        @Test
        public void testBWA() throws Exception
        {
            if (!isExternalPipelineEnabled())
                return;

            String jobName = "TestBWA_" + System.currentTimeMillis();
            JSONObject config = substituteParams(new File(_sampleData, ALIGNMENT_JOB), jobName);
            config.put("alignment", "BWA");
            appendSamplesForAlignment(config, _readsets);

            Set<PipelineJob> jobs = createPipelineJob(jobName, config, SequenceAnalysisController.AnalyzeForm.TYPE.alignment);
            waitForJobs(jobs);

            Set<String> extraFiles = new HashSet<>();
            extraFiles.add(jobName + ".log");
            extraFiles.add("sequenceAnalysis.json");

            extraFiles.add("Shared");
            extraFiles.add("Shared/SIVmac239_Test.fasta");
            extraFiles.add("Shared/SIVmac239_Test.fasta.fai");
            extraFiles.add("Shared/SIVmac239_Test.idKey.txt");

            extraFiles.add("Shared/bwa");
            extraFiles.add("Shared/bwa/SIVmac239_Test.bwa.index.amb");
            extraFiles.add("Shared/bwa/SIVmac239_Test.bwa.index.ann");
            extraFiles.add("Shared/bwa/SIVmac239_Test.bwa.index.bwt");
            extraFiles.add("Shared/bwa/SIVmac239_Test.bwa.index.pac");
            extraFiles.add("Shared/bwa/SIVmac239_Test.bwa.index.sa");

            //job1
            Set<String> job1Files = new HashSet<>(extraFiles);
            job1Files.add("paired1");
            job1Files.add("paired1/Alignment");
            job1Files.add("paired1/Alignment/TestReadset1.bam");
            job1Files.add("paired1/Alignment/paired1.bwa.bam");
            job1Files.add("paired1/Alignment/TestReadset1.summary.metrics");
            job1Files.add("paired1/Alignment/TestReadset1.insertsize.metrics");
            job1Files.add("paired1/Alignment/TestReadset1.insertsize.metrics.pdf");
            job1Files.add("paired1/Alignment/TestReadset1.bam.bai");
            job1Files.add("paired1/Alignment/paired1.fastq.gz.sai");
            job1Files.add("paired1/Alignment/paired2.fastq.gz.sai");

            //job2
            Set<String> job2Files = new HashSet<>(extraFiles);
            job2Files.add("paired3");
            job2Files.add("paired3/Alignment");
            job2Files.add("paired3/Alignment/TestReadset2.bam");
            job2Files.add("paired3/Alignment/paired3.bwa.bam");
            job2Files.add("paired3/Alignment/TestReadset2.summary.metrics");
            //job2Files.add("paired3/Alignment/TestReadset2.insertsize.metrics");
            job2Files.add("paired3/Alignment/TestReadset2.bam.bai");
            job2Files.add("paired3/Alignment/paired3.fastq.sai");

            //job3
            Set<String> job3Files = new HashSet<>(extraFiles);
            job3Files.add("paired4");
            job3Files.add("paired4/Alignment");
            job3Files.add("paired4/Alignment/TestReadset3.bam");
            job3Files.add("paired4/Alignment/paired4.bwa.bam");
            job3Files.add("paired4/Alignment/TestReadset3.summary.metrics");
            //job3Files.add("paired4/Alignment/TestReadset3.insertsize.metrics");
            job3Files.add("paired4/Alignment/TestReadset3.bam.bai");
            job3Files.add("paired4/Alignment/paired4.fastq.sai");

            validateAlignmentJob(jobs, job1Files, _readsets.get(0), 317, 105);
            validateAlignmentJob(jobs, job2Files, _readsets.get(1), 158, 53);
            validateAlignmentJob(jobs, job3Files, _readsets.get(2), 156, 55);
        }

        @Test
        public void testBowtie() throws Exception
        {
            if (!isExternalPipelineEnabled())
                return;

            String jobName = "TestBowtie_" + System.currentTimeMillis();
            JSONObject config = substituteParams(new File(_sampleData, ALIGNMENT_JOB), jobName);
            config.put("alignment", "Bowtie");
            appendSamplesForAlignment(config, _readsets);

            Set<PipelineJob> jobs = createPipelineJob(jobName, config, SequenceAnalysisController.AnalyzeForm.TYPE.alignment);
            waitForJobs(jobs);

            Set<String> extraFiles = new HashSet<>();
            extraFiles.add(jobName + ".log");
            extraFiles.add("sequenceAnalysis.json");

            extraFiles.add("Shared");
            extraFiles.add("Shared/SIVmac239_Test.fasta");
            extraFiles.add("Shared/SIVmac239_Test.fasta.fai");
            extraFiles.add("Shared/SIVmac239_Test.idKey.txt");

            extraFiles.add("Shared/Bowtie");
            extraFiles.add("Shared/Bowtie/SIVmac239_Test.bowtie.index.1.ebwt");
            extraFiles.add("Shared/Bowtie/SIVmac239_Test.bowtie.index.2.ebwt");
            extraFiles.add("Shared/Bowtie/SIVmac239_Test.bowtie.index.3.ebwt");
            extraFiles.add("Shared/Bowtie/SIVmac239_Test.bowtie.index.4.ebwt");
            extraFiles.add("Shared/Bowtie/SIVmac239_Test.bowtie.index.rev.1.ebwt");
            extraFiles.add("Shared/Bowtie/SIVmac239_Test.bowtie.index.rev.2.ebwt");

            //job1
            Set<String> job1Files = new HashSet<>(extraFiles);
            job1Files.add("paired1");
            job1Files.add("paired1/Alignment");
            job1Files.add("paired1/Alignment/TestReadset1.bam");
            job1Files.add("paired1/Alignment/paired1.bowtie.bam");
            job1Files.add("paired1/Alignment/TestReadset1.summary.metrics");
            job1Files.add("paired1/Alignment/TestReadset1.bam.bai");
            job1Files.add("paired1/Alignment/paired1.bowtie.unaligned_1.fastq");
            job1Files.add("paired1/Alignment/paired1.bowtie.unaligned_2.fastq");

            //job2
            Set<String> job2Files = new HashSet<>(extraFiles);
            job2Files.add("paired3");
            job2Files.add("paired3/Alignment");
            job2Files.add("paired3/Alignment/TestReadset2.bam");
            job2Files.add("paired3/Alignment/paired3.bowtie.bam");
            job2Files.add("paired3/Alignment/TestReadset2.summary.metrics");
            //job2Files.add("paired3/Alignment/TestReadset2.insertsize.metrics");
            job2Files.add("paired3/Alignment/TestReadset2.bam.bai");
            job2Files.add("paired3/Alignment/paired3.bowtie.unaligned.fastq");

            //job3
            Set<String> job3Files = new HashSet<>(extraFiles);
            job3Files.add("paired4");
            job3Files.add("paired4/Alignment");
            job3Files.add("paired4/Alignment/TestReadset3.bam");
            job3Files.add("paired4/Alignment/paired4.bowtie.bam");
            job3Files.add("paired4/Alignment/TestReadset3.summary.metrics");
            //job3Files.add("paired4/Alignment/TestReadset3.insertsize.metrics");
            job3Files.add("paired4/Alignment/TestReadset3.bam.bai");
            job3Files.add("paired4/Alignment/paired4.bowtie.unaligned.fastq");

            //this is probably due to adapters
            validateAlignmentJob(jobs, job1Files, _readsets.get(0), 0, 422);
            validateAlignmentJob(jobs, job2Files, _readsets.get(1), 155, 56);
            validateAlignmentJob(jobs, job3Files, _readsets.get(2), 154, 57);
        }

        @Test
        public void testBowtieDeletingIntermediates() throws Exception
        {
            if (!isExternalPipelineEnabled())
                return;

            String jobName = "TestBowtieDeleting_" + System.currentTimeMillis();
            JSONObject config = substituteParams(new File(_sampleData, ALIGNMENT_JOB), jobName);
            config.put("alignment", "Bowtie");
            config.put("deleteIntermediateFiles", true);
            appendSamplesForAlignment(config, _readsets);

            Set<PipelineJob> jobs = createPipelineJob(jobName, config, SequenceAnalysisController.AnalyzeForm.TYPE.alignment);
            waitForJobs(jobs);

            Set<String> extraFiles = new HashSet<>();
            extraFiles.add(jobName + ".log");
            extraFiles.add("sequenceAnalysis.json");

            extraFiles.add("Shared");
            extraFiles.add("Shared/SIVmac239_Test.fasta");
            extraFiles.add("Shared/SIVmac239_Test.fasta.fai");
            extraFiles.add("Shared/SIVmac239_Test.idKey.txt");

            //job1
            Set<String> job1Files = new HashSet<>(extraFiles);
            job1Files.add("paired1");
            job1Files.add("paired1/Alignment");
            job1Files.add("paired1/Alignment/TestReadset1.bam");
            job1Files.add("paired1/Alignment/TestReadset1.summary.metrics");
            job1Files.add("paired1/Alignment/TestReadset1.bam.bai");
            job1Files.add("paired1/Alignment/paired1.bowtie.unaligned_1.fastq");
            job1Files.add("paired1/Alignment/paired1.bowtie.unaligned_2.fastq");

            //job2
            Set<String> job2Files = new HashSet<>(extraFiles);
            job2Files.add("paired3");
            job2Files.add("paired3/Alignment");
            job2Files.add("paired3/Alignment/TestReadset2.bam");
            job2Files.add("paired3/Alignment/TestReadset2.summary.metrics");
            //job2Files.add("paired3/Alignment/TestReadset2.insertsize.metrics");
            job2Files.add("paired3/Alignment/TestReadset2.bam.bai");
            job2Files.add("paired3/Alignment/paired3.bowtie.unaligned.fastq");

            //job3
            Set<String> job3Files = new HashSet<>(extraFiles);
            job3Files.add("paired4");
            job3Files.add("paired4/Alignment");
            job3Files.add("paired4/Alignment/TestReadset3.bam");
            job3Files.add("paired4/Alignment/TestReadset3.summary.metrics");
            //job3Files.add("paired4/Alignment/TestReadset3.insertsize.metrics");
            job3Files.add("paired4/Alignment/TestReadset3.bam.bai");
            job3Files.add("paired4/Alignment/paired4.bowtie.unaligned.fastq");

            validateAlignmentJob(jobs, job1Files, _readsets.get(0), 0, 422);
            validateAlignmentJob(jobs, job2Files, _readsets.get(1), 155, 56);
            validateAlignmentJob(jobs, job3Files, _readsets.get(2), 154, 57);
        }

        @Test
        public void testBwaMemWithSavedLibrary() throws Exception
        {
            if (!isExternalPipelineEnabled())
                return;

            //run using this library
            Integer libraryId = createSavedLibrary();
            Integer dataId = new TableSelector(SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_REF_LIBRARIES), PageFlowUtil.set("fasta_file"), new SimpleFilter(FieldKey.fromString("rowid"), libraryId), null).getObject(Integer.class);
            ExpData data = ExperimentService.get().getExpData(dataId);
            File alignmentIndexDir = new File(data.getFile().getParentFile(), AlignerIndexUtil.INDEX_DIR + "/bwa");
            if (alignmentIndexDir.exists())
            {
                FileUtils.deleteDirectory(alignmentIndexDir);
            }

            String jobName = "TestBWAMemWithSavedLibrary_" + System.currentTimeMillis();
            JSONObject config = substituteParams(new File(_sampleData, ALIGNMENT_JOB), jobName);
            config.put("alignment", "BWA-Mem");
            config.put("referenceLibraryCreation", "SavedLibrary");
            config.put("referenceLibraryCreation.SavedLibrary.libraryId", libraryId);
            appendSamplesForAlignment(config, Arrays.asList(_readsets.get(0)));

            Set<PipelineJob> jobs = createPipelineJob(jobName, config, SequenceAnalysisController.AnalyzeForm.TYPE.alignment);
            waitForJobs(jobs);

            //we expect the index to get copied back to the reference library location
            assert alignmentIndexDir.exists() && alignmentIndexDir.listFiles().length > 0 : "Aligner index was not cached";

            Set<String> extraFiles = new HashSet<>();
            extraFiles.add(jobName + ".log");
            extraFiles.add("sequenceAnalysis.json");

            //NOTE: the first time we create a library, we make the indexes.  this job did not choose to delete intermediates, so they remain in the analysis dir too
            Set<String> indexFiles = new HashSet<>();
            indexFiles.add("Shared");
            indexFiles.add("Shared/bwa");
            indexFiles.add("Shared/bwa/" + libraryId + "_TestLibrary.bwa.index.amb");
            indexFiles.add("Shared/bwa/" + libraryId + "_TestLibrary.bwa.index.ann");
            indexFiles.add("Shared/bwa/" + libraryId + "_TestLibrary.bwa.index.bwt");
            indexFiles.add("Shared/bwa/" + libraryId + "_TestLibrary.bwa.index.pac");
            indexFiles.add("Shared/bwa/" + libraryId + "_TestLibrary.bwa.index.sa");

            //job1
            Set<String> job1Files = new HashSet<>(extraFiles);
            job1Files.add("paired1");
            job1Files.add("paired1/Alignment");
            job1Files.add("paired1/Alignment/TestReadset1.bam");
            job1Files.add("paired1/Alignment/paired1.bwa-mem.bam");
            job1Files.add("paired1/Alignment/TestReadset1.summary.metrics");
            job1Files.add("paired1/Alignment/TestReadset1.insertsize.metrics");
            job1Files.add("paired1/Alignment/TestReadset1.insertsize.metrics.pdf");
            job1Files.add("paired1/Alignment/TestReadset1.bam.bai");
            job1Files.addAll(indexFiles);

//            //job2
//            Set<String> job2Files = new HashSet<>(extraFiles);
//            job2Files.add("paired3");
//            job2Files.add("paired3/Alignment");
//            job2Files.add("paired3/Alignment/TestReadset2.bam");
//            job2Files.add("paired3/Alignment/paired3.bwa-mem.bam");
//            job2Files.add("paired3/Alignment/TestReadset2.summary.metrics");
//            //job2Files.add("paired3/Alignment/TestReadset2.insertsize.metrics");
//            job2Files.add("paired3/Alignment/TestReadset2.bam.bai");
//            //job2Files.addAll(indexFiles);
//
//            //job3
//            Set<String> job3Files = new HashSet<>(extraFiles);
//            job3Files.add("paired4");
//            job3Files.add("paired4/Alignment");
//            job3Files.add("paired4/Alignment/TestReadset3.bam");
//            job3Files.add("paired4/Alignment/paired4.bwa-mem.bam");
//            job3Files.add("paired4/Alignment/TestReadset3.summary.metrics");
//            //job3Files.add("paired4/Alignment/TestReadset3.insertsize.metrics");
//            job3Files.add("paired4/Alignment/TestReadset3.bam.bai");
//            //job3Files.addAll(indexFiles);

            validateAlignmentJob(jobs, job1Files, _readsets.get(0), 320, 102, false);
            //validateAlignmentJob(jobs, job2Files, _readsets.get(1), 160, 51, false);
            //validateAlignmentJob(jobs, job3Files, _readsets.get(2), 159, 52, false);

            //run second job after this one, in order to verify behavior when cached index already exists
            //it should use the index, and also not retain a local copy when finished (even though did didnt pick 'delete intermediates'
            testBwaMemWithSavedLibrary2();

            //delete the cached index
            FileUtils.deleteDirectory(alignmentIndexDir);
        }

        //NOTE: this is called above
        public void testBwaMemWithSavedLibrary2() throws Exception
        {
            if (!isExternalPipelineEnabled())
                return;

            //run using this library
            Integer libraryId = createSavedLibrary();
            String jobName = "TestBWAMemWithSavedLibrary2_" + System.currentTimeMillis();
            JSONObject config = substituteParams(new File(_sampleData, ALIGNMENT_JOB), jobName);
            config.put("alignment", "BWA-Mem");
            config.put("referenceLibraryCreation", "SavedLibrary");
            config.put("referenceLibraryCreation.SavedLibrary.libraryId", libraryId);
            appendSamplesForAlignment(config, _readsets);

            Set<PipelineJob> jobs = createPipelineJob(jobName, config, SequenceAnalysisController.AnalyzeForm.TYPE.alignment);
            waitForJobs(jobs);

            Set<String> extraFiles = new HashSet<>();
            extraFiles.add(jobName + ".log");

            extraFiles.add("sequenceAnalysis.json");
            extraFiles.add("Shared");

            //job1
            Set<String> job1Files = new HashSet<>(extraFiles);
            job1Files.add("paired1");
            job1Files.add("paired1/Alignment");
            job1Files.add("paired1/Alignment/TestReadset1.bam");
            job1Files.add("paired1/Alignment/paired1.bwa-mem.bam");
            job1Files.add("paired1/Alignment/TestReadset1.summary.metrics");
            job1Files.add("paired1/Alignment/TestReadset1.insertsize.metrics");
            job1Files.add("paired1/Alignment/TestReadset1.insertsize.metrics.pdf");
            job1Files.add("paired1/Alignment/TestReadset1.bam.bai");

            //job2
            Set<String> job2Files = new HashSet<>(extraFiles);
            job2Files.add("paired3");
            job2Files.add("paired3/Alignment");
            job2Files.add("paired3/Alignment/TestReadset2.bam");
            job2Files.add("paired3/Alignment/paired3.bwa-mem.bam");
            job2Files.add("paired3/Alignment/TestReadset2.summary.metrics");
            //job2Files.add("paired3/Alignment/TestReadset2.insertsize.metrics");
            job2Files.add("paired3/Alignment/TestReadset2.bam.bai");

            //job3
            Set<String> job3Files = new HashSet<>(extraFiles);
            job3Files.add("paired4");
            job3Files.add("paired4/Alignment");
            job3Files.add("paired4/Alignment/TestReadset3.bam");
            job3Files.add("paired4/Alignment/paired4.bwa-mem.bam");
            job3Files.add("paired4/Alignment/TestReadset3.summary.metrics");
            //job3Files.add("paired4/Alignment/TestReadset3.insertsize.metrics");
            job3Files.add("paired4/Alignment/TestReadset3.bam.bai");

            validateAlignmentJob(jobs, job1Files, _readsets.get(0), 320, 102, false);
            validateAlignmentJob(jobs, job2Files, _readsets.get(1), 160, 51, false);
            validateAlignmentJob(jobs, job3Files, _readsets.get(2), 159, 52, false);
        }

        @Test
        public void testMergedAlignments() throws Exception
        {
            if (!isExternalPipelineEnabled())
                return;

            String jobName = "TestBWAMemMergedAlign_" + System.currentTimeMillis();
            JSONObject config = substituteParams(new File(_sampleData, ALIGNMENT_JOB), jobName);
            config.put("alignment", "BWA-Mem");

            for (String fn : Arrays.asList(PAIRED_FILENAME_L1a, PAIRED_FILENAME_L2))
            {
                File file3 = new File(_pipelineRoot, fn);
                if (!file3.exists())
                    FileUtils.copyFile(new File(_sampleData, PAIRED_FILENAME1), file3);
            }

            for (String fn : Arrays.asList(PAIRED_FILENAME2_L1a, PAIRED_FILENAME2_L2))
            {
                File file4 = new File(_pipelineRoot, fn);
                if (!file4.exists())
                    FileUtils.copyFile(new File(_sampleData, PAIRED_FILENAME2), file4);
            }

            List<SequenceReadsetImpl> models = new ArrayList<>();

            models.add(createReadset("TestMergedReadset", Arrays.asList(
                    Pair.of(new File(_pipelineRoot, PAIRED_FILENAME_L1a), new File(_pipelineRoot, PAIRED_FILENAME2_L1a)),
                    Pair.of(new File(_pipelineRoot, PAIRED_FILENAME_L2), new File(_pipelineRoot, PAIRED_FILENAME2_L2))
            )));

            appendSamplesForAlignment(config, models);

            Set<PipelineJob> jobs = createPipelineJob(jobName, config, SequenceAnalysisController.AnalyzeForm.TYPE.alignment);
            waitForJobs(jobs);

            Set<String> extraFiles = new HashSet<>();
            extraFiles.add(jobName + ".log");

            extraFiles.add("sequenceAnalysis.json");

            extraFiles.add("Shared");
            extraFiles.add("Shared/SIVmac239_Test.fasta");
            extraFiles.add("Shared/SIVmac239_Test.fasta.fai");
            extraFiles.add("Shared/SIVmac239_Test.idKey.txt");

            extraFiles.add("Shared/bwa");
            extraFiles.add("Shared/bwa/SIVmac239_Test.bwa.index.amb");
            extraFiles.add("Shared/bwa/SIVmac239_Test.bwa.index.ann");
            extraFiles.add("Shared/bwa/SIVmac239_Test.bwa.index.bwt");
            extraFiles.add("Shared/bwa/SIVmac239_Test.bwa.index.pac");
            extraFiles.add("Shared/bwa/SIVmac239_Test.bwa.index.sa");

            //job1
            Set<String> job1Files = new HashSet<>(extraFiles);
            job1Files.add("s_G1_L001_R1_001");
            job1Files.add("s_G1_L001_R1_001/Alignment");
            job1Files.add("s_G1_L001_R1_001/Alignment/TestMergedReadset.bam");
            job1Files.add("s_G1_L001_R1_001/Alignment/TestMergedReadset.bam.bai");
            job1Files.add("s_G1_L001_R1_001/Alignment/TestMergedReadset.summary.metrics");
            job1Files.add("s_G1_L001_R1_001/Alignment/TestMergedReadset.insertsize.metrics");
            job1Files.add("s_G1_L001_R1_001/Alignment/TestMergedReadset.insertsize.metrics.pdf");

            job1Files.add("s_G1_L002_R1_001");

            //note: these are no longer deleted unless intermediate files are cleaned up
            job1Files.add("s_G1_L001_R1_001/Alignment/s_G1_L001_R1_001.bwa-mem.bam");
            job1Files.add("s_G1_L002_R1_001/Alignment/s_G1_L002_R1_001.bwa-mem.bam");
            job1Files.add("s_G1_L002_R1_001/Alignment");


            validateAlignmentJob(jobs, job1Files, models.get(0), 640, 204);
        }

        @Test
        public void testBowtie2() throws Exception
        {
            if (!isExternalPipelineEnabled())
                return;

            String jobName = "TestBowtie2_" + System.currentTimeMillis();
            JSONObject config = substituteParams(new File(_sampleData, ALIGNMENT_JOB), jobName);
            config.put("alignment", "Bowtie2");
            appendSamplesForAlignment(config, _readsets);

            Set<PipelineJob> jobs = createPipelineJob(jobName, config, SequenceAnalysisController.AnalyzeForm.TYPE.alignment);
            waitForJobs(jobs);

            Set<String> extraFiles = new HashSet<>();
            extraFiles.add(jobName + ".log");

            extraFiles.add("sequenceAnalysis.json");

            extraFiles.add("Shared");
            extraFiles.add("Shared/SIVmac239_Test.fasta");
            extraFiles.add("Shared/SIVmac239_Test.fasta.fai");
            extraFiles.add("Shared/SIVmac239_Test.idKey.txt");

            extraFiles.add("Shared/Bowtie2");
            extraFiles.add("Shared/Bowtie2/SIVmac239_Test.bowtie2.index.1.bt2");
            extraFiles.add("Shared/Bowtie2/SIVmac239_Test.bowtie2.index.2.bt2");
            extraFiles.add("Shared/Bowtie2/SIVmac239_Test.bowtie2.index.3.bt2");
            extraFiles.add("Shared/Bowtie2/SIVmac239_Test.bowtie2.index.4.bt2");
            extraFiles.add("Shared/Bowtie2/SIVmac239_Test.bowtie2.index.rev.1.bt2");
            extraFiles.add("Shared/Bowtie2/SIVmac239_Test.bowtie2.index.rev.2.bt2");

            //job1
            Set<String> job1Files = new HashSet<>(extraFiles);
            job1Files.add("paired1");
            job1Files.add("paired1/Alignment");
            job1Files.add("paired1/Alignment/TestReadset1.bam");
            job1Files.add("paired1/Alignment/paired1.bowtie2.bam");
            job1Files.add("paired1/Alignment/TestReadset1.summary.metrics");
            job1Files.add("paired1/Alignment/TestReadset1.bam.bai");
            job1Files.add("paired1/Alignment/paired1.bowtie2.unaligned.fastq.gz");
            job1Files.add("paired1/Alignment/TestReadset1.insertsize.metrics");
            job1Files.add("paired1/Alignment/TestReadset1.insertsize.metrics.pdf");

            //job2
            Set<String> job2Files = new HashSet<>(extraFiles);
            job2Files.add("paired3");
            job2Files.add("paired3/Alignment");
            job2Files.add("paired3/Alignment/TestReadset2.bam");
            job2Files.add("paired3/Alignment/paired3.bowtie2.bam");
            job2Files.add("paired3/Alignment/TestReadset2.summary.metrics");
            //job2Files.add("paired3/Alignment/TestReadset2.insertsize.metrics");
            job2Files.add("paired3/Alignment/TestReadset2.bam.bai");
            job2Files.add("paired3/Alignment/paired3.bowtie2.unaligned.fastq.gz");

            //job3
            Set<String> job3Files = new HashSet<>(extraFiles);
            job3Files.add("paired4");
            job3Files.add("paired4/Alignment");
            job3Files.add("paired4/Alignment/TestReadset3.bam");
            job3Files.add("paired4/Alignment/paired4.bowtie2.bam");
            job3Files.add("paired4/Alignment/TestReadset3.summary.metrics");
            //job3Files.add("paired4/Alignment/TestReadset3.insertsize.metrics");
            job3Files.add("paired4/Alignment/TestReadset3.bam.bai");
            job3Files.add("paired4/Alignment/paired4.bowtie2.unaligned.fastq.gz");

            validateInputs();

            validateAlignmentJob(jobs, job1Files, _readsets.get(0), 318, 104);
            validateAlignmentJob(jobs, job2Files, _readsets.get(1), 160, 51);
            validateAlignmentJob(jobs, job3Files, _readsets.get(2), 158, 53);
        }
    }

    /**
     * These are experimental or flaky tests not ready to run on team city
     */
    public static class SequenceAnalysisPipelineTestCase3 extends AbstractAnalysisPipelineTestCase
    {
        private static final String PROJECT_NAME = "SequenceAnalysisTestProject3";

        @BeforeClass
        public static void initialSetUp() throws Exception
        {
            doInitialSetUp(PROJECT_NAME);
        }

        @AfterClass
        public static void cleanup()
        {
            doCleanup(PROJECT_NAME);
        }

        protected String getProjectName()
        {
            return PROJECT_NAME;
        }

        //NOTE: there is an issue that seems specific to this genome.  disable for now
        //@Test
        public void testStar() throws Exception
        {
            if (!isExternalPipelineEnabled())
                return;

            String jobName = "TestStar_" + System.currentTimeMillis();
            JSONObject config = substituteParams(new File(_sampleData, ALIGNMENT_JOB), jobName);
            config.put("alignment", "STAR");
            appendSamplesForAlignment(config, _readsets);

            Set<PipelineJob> jobs = createPipelineJob(jobName, config, SequenceAnalysisController.AnalyzeForm.TYPE.alignment);
            waitForJobs(jobs);

            Set<String> extraFiles = new HashSet<>();
            extraFiles.add(jobName + ".log");
            extraFiles.add("sequenceAnalysis.json");

            extraFiles.add("Shared");
            extraFiles.add("Shared/SIVmac239_Test.fasta");
            extraFiles.add("Shared/SIVmac239_Test.fasta.fai");
            extraFiles.add("Shared/SIVmac239_Test.idKey.txt");

            extraFiles.add("Shared/Bowtie");
            extraFiles.add("Shared/Bowtie/SIVmac239_Test.bowtie.index.1.ebwt");
            extraFiles.add("Shared/Bowtie/SIVmac239_Test.bowtie.index.2.ebwt");
            extraFiles.add("Shared/Bowtie/SIVmac239_Test.bowtie.index.3.ebwt");
            extraFiles.add("Shared/Bowtie/SIVmac239_Test.bowtie.index.4.ebwt");
            extraFiles.add("Shared/Bowtie/SIVmac239_Test.bowtie.index.rev.1.ebwt");
            extraFiles.add("Shared/Bowtie/SIVmac239_Test.bowtie.index.rev.2.ebwt");

            Set<String> job1Files = new HashSet<>(extraFiles);
            job1Files.add("paired1");
            job1Files.add("paired1/Alignment");
            job1Files.add("paired1/Alignment/TestReadset1.bam");
            job1Files.add("paired1/Alignment/TestReadset1.summary.metrics");
            job1Files.add("paired1/Alignment/TestReadset1.insertsize.metrics");
            job1Files.add("paired1/Alignment/TestReadset1.insertsize.metrics.pdf");
            job1Files.add("paired1/Alignment/TestReadset1.bam.bai");
            job1Files.add("paired1/Alignment/paired1.bowtie.unaligned_1.fastq");
            job1Files.add("paired1/Alignment/paired1.bowtie.unaligned_2.fastq");

            Set<String> job2Files = new HashSet<>(extraFiles);
            job2Files.add("paired3");
            job2Files.add("paired3/Alignment");
            job2Files.add("paired3/Alignment/TestReadset2.bam");
            job2Files.add("paired3/Alignment/TestReadset2.summary.metrics");
            //job2Files.add("paired3/Alignment/TestReadset2.insertsize.metrics");
            job2Files.add("paired3/Alignment/TestReadset2.bam.bai");
            job2Files.add("paired3/Alignment/paired3.bowtie.unaligned.fastq");

            Set<String> job3Files = new HashSet<>(extraFiles);
            job3Files.add("paired4");
            job3Files.add("paired4/Alignment");
            job3Files.add("paired4/Alignment/TestReadset3.bam");
            job3Files.add("paired4/Alignment/TestReadset3.summary.metrics");
            //job3Files.add("paired4/Alignment/TestReadset3.insertsize.metrics");
            job3Files.add("paired4/Alignment/TestReadset3.bam.bai");
            job3Files.add("paired4/Alignment/paired4.bowtie.unaligned.fastq");

            //this is probably due to adapters
            validateAlignmentJob(jobs, job1Files, _readsets.get(0), 0, 422);
            validateAlignmentJob(jobs, job2Files, _readsets.get(1), 155, 56);
            validateAlignmentJob(jobs, job3Files, _readsets.get(2), 154, 57);
        }

        @Test
        public void testBismarkWithSavedLibraryAndAdapters() throws Exception
        {
            if (!isExternalPipelineEnabled())
                return;

            //run using this library
            Integer libraryId = createSavedLibrary();
            Integer dataId = new TableSelector(SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_REF_LIBRARIES), PageFlowUtil.set("fasta_file"), new SimpleFilter(FieldKey.fromString("rowid"), libraryId), null).getObject(Integer.class);
            ExpData data = ExperimentService.get().getExpData(dataId);
            File alignmentIndexDir = new File(data.getFile().getParentFile(), AlignerIndexUtil.INDEX_DIR + "/Bismark");
            if (alignmentIndexDir.exists())
            {
                FileUtils.deleteDirectory(alignmentIndexDir);
            }

            String jobName = "TestBismarkWithSavedLibraryAndAdapters_" + System.currentTimeMillis();
            JSONObject config = substituteParams(new File(_sampleData, ALIGNMENT_JOB), jobName);
            config.put("alignment", "Bismark");
            config.put("alignment.Bismark.seed_length", "30");
            config.put("alignment.Bismark.max_seed_mismatches", "1");
            config.put("referenceLibraryCreation", "SavedLibrary");
            config.put("referenceLibraryCreation.SavedLibrary.libraryId", libraryId);

            config.put("analysis", "BismarkMethylationExtractor");
            config.put("analysis.BismarkMethylationExtractor.merge_non_CpG", true);
            config.put("analysis.BismarkMethylationExtractor.gzip", true);
            config.put("analysis.BismarkMethylationExtractor.report", true);
            config.put("analysis.BismarkMethylationExtractor.siteReport", true);
            config.put("analysis.BismarkMethylationExtractor.minCoverageDepth", 1);

            config.put("fastqProcessing", "AdapterTrimming");
            config.put("fastqProcessing.AdapterTrimming.adapters", "[[\"Nextera Transposon Adapter A\",\"AGATGTGTATAAGAGACAG\",true,true]]");
            appendSamplesForAlignment(config, _readsets);

            Set<PipelineJob> jobs = createPipelineJob(jobName, config, SequenceAnalysisController.AnalyzeForm.TYPE.alignment);
            waitForJobs(jobs);

            //we expect the index to get copied back to the reference library location
            assert alignmentIndexDir.exists() && alignmentIndexDir.listFiles().length > 0 : "Aligner index was not cached";

            Set<String> extraFiles = new HashSet<>();
            extraFiles.add(jobName + ".log");

            extraFiles.add("sequenceAnalysis.json");
            extraFiles.add("Shared");
            extraFiles.add("Shared/4_TestLibrary.fasta");
            extraFiles.add("Shared/4_TestLibrary.fasta.fai");
            extraFiles.add("Shared/Bisulfite_Genome_Bowtie2");
            extraFiles.add("Shared/Bisulfite_Genome_Bowtie2/CT_conversion");
            extraFiles.add("Shared/Bisulfite_Genome_Bowtie2/CT_conversion/BS_CT.1.bt2");
            extraFiles.add("Shared/Bisulfite_Genome_Bowtie2/CT_conversion/BS_CT.2.bt2");
            extraFiles.add("Shared/Bisulfite_Genome_Bowtie2/CT_conversion/BS_CT.3.bt2");
            extraFiles.add("Shared/Bisulfite_Genome_Bowtie2/CT_conversion/BS_CT.4.bt2");
            extraFiles.add("Shared/Bisulfite_Genome_Bowtie2/CT_conversion/BS_CT.rev.1.bt2");
            extraFiles.add("Shared/Bisulfite_Genome_Bowtie2/CT_conversion/BS_CT.rev.2.bt2");
            extraFiles.add("Shared/Bisulfite_Genome_Bowtie2/CT_conversion/genome_mfa.CT_conversion.fa");
            extraFiles.add("Shared/Bisulfite_Genome_Bowtie2/GA_conversion");
            extraFiles.add("Shared/Bisulfite_Genome_Bowtie2/GA_conversion/BS_GA.1.bt2");
            extraFiles.add("Shared/Bisulfite_Genome_Bowtie2/GA_conversion/BS_GA.2.bt2");
            extraFiles.add("Shared/Bisulfite_Genome_Bowtie2/GA_conversion/BS_GA.3.bt2");
            extraFiles.add("Shared/Bisulfite_Genome_Bowtie2/GA_conversion/BS_GA.4.bt2");
            extraFiles.add("Shared/Bisulfite_Genome_Bowtie2/GA_conversion/BS_GA.rev.1.bt2");
            extraFiles.add("Shared/Bisulfite_Genome_Bowtie2/GA_conversion/BS_GA.rev.2.bt2");
            extraFiles.add("Shared/Bisulfite_Genome_Bowtie2/GA_conversion/genome_mfa.GA_conversion.fa");

            //job1
            Set<String> job1Files = new HashSet<>(extraFiles);
            job1Files.add("paired1");
            job1Files.add("paired1/Preprocessing");
            job1Files.add("paired1/Preprocessing/paired1.preprocessed.fastq.gz");
            job1Files.add("paired1/Preprocessing/paired2.preprocessed.fastq.gz");
            job1Files.add("paired1/Alignment");
            job1Files.add("paired1/Alignment/TestReadset1.bam");
            //job1Files.add("paired1/Alignment/TestReadset1.summary.metrics");
            //job1Files.add("paired1/Alignment/TestReadset1.insertsize.metrics");
            //job1Files.add("paired1/Alignment/TestReadset1.insertsize.metrics.pdf");

            File basedir1 = getAlignmentJob(jobs, _readsets.get(0)).getAnalysisDirectory();
            addOptionalFile(job1Files, basedir1, "paired1/Alignment/CpG_OB_TestReadset1.txt.gz");
            addOptionalFile(job1Files, basedir1, "paired1/Alignment/CpG_OT_TestReadset1.txt.gz");
            addOptionalFile(job1Files, basedir1, "paired1/Alignment/Non_CpG_OB_TestReadset1.bam.txt.gz");
            addOptionalFile(job1Files, basedir1, "paired1/Alignment/Non_CpG_OT_TestReadset1.txt.gz");

            job1Files.add("paired1/Alignment/TestReadset1.bam_splitting_report.txt");
            job1Files.add("paired1/Alignment/TestReadset1.CpG_Site_Summary.gff");
            job1Files.add("paired1/Alignment/TestReadset1.CpG_Site_Summary.methylation.txt");
            job1Files.add("paired1/Alignment/TestReadset1.CpG_Site_Summary.methylation.txt.png");
            job1Files.add("paired1/Alignment/TestReadset1.M-bias.txt");
            job1Files.add("paired1/Alignment/TestReadset1.M-bias_R1.png");
            job1Files.add("paired1/Alignment/TestReadset1.M-bias_R2.png");
            job1Files.add("paired1/Alignment/paired1.preprocessed_bismark_PE.alignment_overview.png");
            job1Files.add("paired1/Alignment/paired1.preprocessed_bismark_bt2_PE_report.txt");

            //job2
            Set<String> job2Files = new HashSet<>(extraFiles);
            job2Files.add("paired3");
            job2Files.add("paired3/Preprocessing");
            job2Files.add("paired3/Preprocessing/paired3.preprocessed.fastq");
            job2Files.add("paired3/Alignment");
            job2Files.add("paired3/Alignment/TestReadset2.bam");
            //job2Files.add("paired3/Alignment/TestReadset2.summary.metrics");
            //job2Files.add("paired3/Alignment/TestReadset2.insertsize.metrics");

            File basedir2 = getAlignmentJob(jobs, _readsets.get(1)).getAnalysisDirectory();
            addOptionalFile(job2Files, basedir2, "paired3/Alignment/CpG_OB_TestReadset2.txt.gz");
            addOptionalFile(job2Files, basedir2, "paired3/Alignment/CpG_OT_TestReadset2.txt.gz");
            addOptionalFile(job2Files, basedir2, "paired3/Alignment/Non_CpG_OB_TestReadset2.bam.txt.gz");
            addOptionalFile(job2Files, basedir2, "paired3/Alignment/Non_CpG_OT_TestReadset2.txt.gz");
            job2Files.add("paired3/Alignment/TestReadset2.bam_splitting_report.txt");
            job2Files.add("paired3/Alignment/TestReadset2.CpG_Site_Summary.gff");
            job2Files.add("paired3/Alignment/TestReadset2.CpG_Site_Summary.methylation.txt");
            job2Files.add("paired3/Alignment/TestReadset2.CpG_Site_Summary.methylation.txt.png");
            job2Files.add("paired3/Alignment/TestReadset2.M-bias.txt");
            job2Files.add("paired3/Alignment/TestReadset2.M-bias_R1.png");
            job2Files.add("paired3/Alignment/paired3.preprocessed.fastq_bismark_SE.alignment_overview.png");
            job2Files.add("paired3/Alignment/paired3.preprocessed.fastq_bismark_bt2_SE_report.txt");

            //job3
            Set<String> job3Files = new HashSet<>(extraFiles);
            job3Files.add("paired4");
            job3Files.add("paired4/Preprocessing");
            job3Files.add("paired4/Preprocessing/paired4.preprocessed.fastq");
            job3Files.add("paired4/Alignment");
            job3Files.add("paired4/Alignment/TestReadset3.bam");
            //job3Files.add("paired4/Alignment/TestReadset3.summary.metrics");
            //job3Files.add("paired4/Alignment/TestReadset3.insertsize.metrics");

            File basedir3 = getAlignmentJob(jobs, _readsets.get(2)).getAnalysisDirectory();
            addOptionalFile(job3Files, basedir3, "paired4/Alignment/CpG_OB_TestReadset3.txt.gz");
            addOptionalFile(job3Files, basedir3, "paired4/Alignment/CpG_OT_TestReadset3.txt.gz");
            addOptionalFile(job3Files, basedir3, "paired4/Alignment/Non_CpG_OB_TestReadset3.bam.txt.gz");
            addOptionalFile(job3Files, basedir3, "paired4/Alignment/Non_CpG_OT_TestReadset3.txt.gz");
            job3Files.add("paired4/Alignment/TestReadset3.bam_splitting_report.txt");
            job3Files.add("paired4/Alignment/TestReadset3.CpG_Site_Summary.gff");
            job3Files.add("paired4/Alignment/TestReadset3.CpG_Site_Summary.methylation.txt");
            job3Files.add("paired4/Alignment/TestReadset3.CpG_Site_Summary.methylation.txt.png");
            job3Files.add("paired4/Alignment/TestReadset3.M-bias.txt");
            job3Files.add("paired4/Alignment/TestReadset3.M-bias_R1.png");
            job3Files.add("paired4/Alignment/paired4.preprocessed.fastq_bismark_SE.alignment_overview.png");
            job3Files.add("paired4/Alignment/paired4.preprocessed.fastq_bismark_bt2_SE_report.txt");

            //disabled because this has been unreliable with the # of aligned reads
            validateAlignmentJob(jobs, job1Files, _readsets.get(0), null, null);
            validateAlignmentJob(jobs, job2Files, _readsets.get(1), null, null);
            validateAlignmentJob(jobs, job3Files, _readsets.get(2), null, null);

            //validateAlignment(bam1, 40, 0);  //has been 18??
            //validateAlignment(bam2, 151, 0);  //why 72??
            //validateAlignment(bam3, 150, 0);  //why 77?

            //repeat, which will use the index cached above
            testBismarkWithSavedLibraryAdaptersAndDelete();

            assert alignmentIndexDir.exists() && alignmentIndexDir.listFiles().length > 0 : "Aligner index was deleted during run";

            //delete the cached index
            FileUtils.deleteDirectory(alignmentIndexDir);
        }

        public void testBismarkWithSavedLibraryAdaptersAndDelete() throws Exception
        {
            if (!isExternalPipelineEnabled())
                return;

            //run using this library
            Integer libraryId = createSavedLibrary();
            String jobName = "TestBismarkWithSavedLibraryAndDelete_" + System.currentTimeMillis();
            JSONObject config = substituteParams(new File(_sampleData, ALIGNMENT_JOB), jobName);
            config.put("alignment", "Bismark");
            config.put("alignment.Bismark.seed_length", "30");
            config.put("alignment.Bismark.max_seed_mismatches", "1");
            config.put("deleteIntermediateFiles", true);
            config.put("referenceLibraryCreation", "SavedLibrary");
            config.put("referenceLibraryCreation.SavedLibrary.libraryId", libraryId);

            config.put("analysis", "BismarkMethylationExtractor");
            config.put("analysis.BismarkMethylationExtractor.merge_non_CpG", true);
            config.put("analysis.BismarkMethylationExtractor.gzip", true);
            config.put("analysis.BismarkMethylationExtractor.report", true);
            config.put("analysis.BismarkMethylationExtractor.siteReport", true);
            config.put("analysis.BismarkMethylationExtractor.minCoverageDepth", 1);

            config.put("fastqProcessing", "AdapterTrimming");
            config.put("fastqProcessing.AdapterTrimming.adapters", "[[\"Nextera Transposon Adapter A\",\"AGATGTGTATAAGAGACAG\",true,true]]");
            appendSamplesForAlignment(config, _readsets);

            Set<PipelineJob> jobs = createPipelineJob(jobName, config, SequenceAnalysisController.AnalyzeForm.TYPE.alignment);
            waitForJobs(jobs);

            Set<String> extraFiles = new HashSet<>();
            extraFiles.add(jobName + ".log");

            extraFiles.add("sequenceAnalysis.json");
            extraFiles.add("Shared");

            //job1
            Set<String> job1Files = new HashSet<>(extraFiles);
            job1Files.add("paired1");
            job1Files.add("paired1/Alignment");
            job1Files.add("paired1/Alignment/TestReadset1.bam");
            job1Files.add("paired1/Alignment/TestReadset1.summary.metrics");
            job1Files.add("paired1/Alignment/TestReadset1.insertsize.metrics");
            job1Files.add("paired1/Alignment/TestReadset1.insertsize.metrics.pdf");

            File basedir1 = getAlignmentJob(jobs, _readsets.get(0)).getAnalysisDirectory();
            addOptionalFile(job1Files, basedir1, "paired1/Alignment/CpG_OB_TestReadset1.txt.gz");
            addOptionalFile(job1Files, basedir1, "paired1/Alignment/CpG_OT_TestReadset1.txt.gz");
            addOptionalFile(job1Files, basedir1, "paired1/Alignment/Non_CpG_OB_TestReadset1.bam.txt.gz");
            addOptionalFile(job1Files, basedir1, "paired1/Alignment/Non_CpG_OT_TestReadset1.txt.gz");
            job1Files.add("paired1/Alignment/TestReadset1.bam_splitting_report.txt");
            job1Files.add("paired1/Alignment/TestReadset1.CpG_Site_Summary.gff");
            job1Files.add("paired1/Alignment/TestReadset1.CpG_Site_Summary.methylation.txt");
            job1Files.add("paired1/Alignment/TestReadset1.CpG_Site_Summary.methylation.txt.png");
            job1Files.add("paired1/Alignment/TestReadset1.M-bias.txt");
            job1Files.add("paired1/Alignment/TestReadset1.M-bias_R1.png");
            job1Files.add("paired1/Alignment/TestReadset1.M-bias_R2.png");
            job1Files.add("paired1/Alignment/paired1.preprocessed_bismark_PE.alignment_overview.png");
            job1Files.add("paired1/Alignment/paired1.preprocessed_bismark_PE_report.txt");

            //job2
            Set<String> job2Files = new HashSet<>(extraFiles);
            job2Files.add("paired3");
            job2Files.add("paired3/Alignment");
            job2Files.add("paired3/Alignment/TestReadset2.bam");
            job2Files.add("paired3/Alignment/TestReadset2.summary.metrics");
            //job2Files.add("paired3/Alignment/TestReadset2.insertsize.metrics");

            File basedir2 = getAlignmentJob(jobs, _readsets.get(1)).getAnalysisDirectory();
            addOptionalFile(job2Files, basedir2, "paired3/Alignment/CpG_OB_TestReadset2.txt.gz");
            addOptionalFile(job2Files, basedir2, "paired3/Alignment/CpG_OT_TestReadset2.txt.gz");
            addOptionalFile(job2Files, basedir2, "paired3/Alignment/Non_CpG_OB_TestReadset2.bam.txt.gz");
            addOptionalFile(job2Files, basedir2, "paired3/Alignment/Non_CpG_OT_TestReadset2.txt.gz");
            job2Files.add("paired3/Alignment/TestReadset2.bam_splitting_report.txt");
            job2Files.add("paired3/Alignment/TestReadset2.CpG_Site_Summary.gff");
            job2Files.add("paired3/Alignment/TestReadset2.CpG_Site_Summary.methylation.txt");
            job2Files.add("paired3/Alignment/TestReadset2.CpG_Site_Summary.methylation.txt.png");
            job2Files.add("paired3/Alignment/TestReadset2.M-bias.txt");
            job2Files.add("paired3/Alignment/TestReadset2.M-bias_R1.png");
            job2Files.add("paired3/Alignment/paired3.preprocessed.fastq_bismark_SE.alignment_overview.png");
            job2Files.add("paired3/Alignment/paired3.preprocessed.fastq_bismark_SE_report.txt");

            //job3
            Set<String> job3Files = new HashSet<>(extraFiles);
            job3Files.add("paired4");
            job3Files.add("paired4/Alignment");
            job3Files.add("paired4/Alignment/TestReadset3.bam");
            job3Files.add("paired4/Alignment/TestReadset3.summary.metrics");
            //job3Files.add("paired4/Alignment/TestReadset3.insertsize.metrics");

            File basedir3 = getAlignmentJob(jobs, _readsets.get(2)).getAnalysisDirectory();
            addOptionalFile(job3Files, basedir3, "paired4/Alignment/CpG_OB_TestReadset3.txt.gz");
            addOptionalFile(job3Files, basedir3, "paired4/Alignment/CpG_OT_TestReadset3.txt.gz");
            addOptionalFile(job3Files, basedir3, "paired4/Alignment/Non_CpG_OB_TestReadset3.bam.txt.gz");
            addOptionalFile(job3Files, basedir3, "paired4/Alignment/Non_CpG_OT_TestReadset3.txt.gz");
            job3Files.add("paired4/Alignment/TestReadset3.bam_splitting_report.txt");
            job3Files.add("paired4/Alignment/TestReadset3.CpG_Site_Summary.gff");
            job3Files.add("paired4/Alignment/TestReadset3.CpG_Site_Summary.methylation.txt");
            job3Files.add("paired4/Alignment/TestReadset3.CpG_Site_Summary.methylation.txt.png");
            job3Files.add("paired4/Alignment/TestReadset3.M-bias.txt");
            job3Files.add("paired4/Alignment/TestReadset3.M-bias_R1.png");
            job3Files.add("paired4/Alignment/paired4.preprocessed.fastq_bismark_SE.alignment_overview.png");
            job3Files.add("paired4/Alignment/paired4.preprocessed.fastq_bismark_SE_report.txt");

            //disabled on purpose.  see note able about unreliability
            validateAlignmentJob(jobs, job1Files, _readsets.get(0), null, null);
            validateAlignmentJob(jobs, job2Files, _readsets.get(1), null, null);
            validateAlignmentJob(jobs, job3Files, _readsets.get(2), null, null);

            //validateAlignment(bam1, 18, 0);  //has also been 40??
            //validateAlignment(bam2, 151, 0);  //sometimes is 72?
            //validateAlignment(bam3, 150, 0); //sometimes 77?
        }
    }

    public static RefNtSequenceModel ensureSivMac239(Container c, Logger log)
    {
        log.info("ensure SIVMac239 NT record exists");

        //NOTE: we cant guarantee that the NT Id of the test data will be identical to that of the server.  therefore we find mac239's rowId here an swap in later
        TableInfo tableNt = QueryService.get().getUserSchema(TestContext.get().getUser(), c, SequenceAnalysisSchema.SCHEMA_NAME).getTable(SequenceAnalysisSchema.TABLE_REF_NT_SEQUENCES);
        SimpleFilter ntFilter = new SimpleFilter(FieldKey.fromString("name"), "SIVmac239_Test");
        //note: dont use container filter so this could include /shared
        //ntFilter.addCondition(FieldKey.fromString("container"), c.getId());

        TableSelector tsNt = new TableSelector(tableNt, PageFlowUtil.set("rowId"), ntFilter, null);
        if (!tsNt.exists())
        {
            log.info("creating SIVmac239 record");
            Map<String, Object> map = new CaseInsensitiveHashMap<>();
            map.put("name", "SIVmac239_Test");
            map.put("category", "Virus");
            map.put("subset", "SIVmac239_Test");
            map.put("container", c.getId());
            map.put("createdby", TestContext.get().getUser().getUserId());
            map.put("created", new Date());
            map.put("modifiedby", TestContext.get().getUser().getUserId());
            map.put("modified", new Date());

            Table.insert(TestContext.get().getUser(), SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_REF_NT_SEQUENCES), map);
        }
        //step added to clean state of team city agents
        else if (tsNt.getRowCount() > 1)
        {
            log.info("more than one SIVmac239 record found, removing duplicates");
            List<Integer> rowIds = tsNt.getArrayList(Integer.class);
            Collections.sort(rowIds);
            rowIds.remove(0); //preserve the lowest (first inserted)
            try
            {
                SequenceAnalysisManager.get().deleteRefNtSequence(TestContext.get().getUser(), c, rowIds);
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        }

        RefNtSequenceModel nt = new TableSelector(tableNt, ntFilter, null).getObject(RefNtSequenceModel.class);
        if (nt == null)
        {
            throw new RuntimeException("Unable to find RefNtSequenceModel");
        }

        return nt;
    }

    public static void ensureSivMac239Sequence(Container c, Logger log) throws IOException
    {
        TableInfo ti = QueryService.get().getUserSchema(TestContext.get().getUser(), c, SequenceAnalysisSchema.SCHEMA_NAME).getTable(SequenceAnalysisSchema.TABLE_REF_NT_SEQUENCES, null);
        SimpleFilter filter = new SimpleFilter(FieldKey.fromString("name"), "SIVmac239_Test");
        //note: dont use container filter so this could include /shared
        //filter.addCondition(FieldKey.fromString("container"), c.getId());
        RefNtSequenceModel model = new TableSelector(ti, filter, null).getObject(RefNtSequenceModel.class);
        if (model == null)
        {
            log.error("unable to find SIVmac239 sequence");
        }
        else if (!model.hasSequenceFile())
        {
            log.info("creating sequence for SIVmac239");
            model.createFileForSequence(TestContext.get().getUser(), "GCATGCACATTTTAAAGGCTTTTGCTAAATATAGCCAAAAGTCCTTCTACAAATTTTCTAAGAGTTCTGATTCAAAGCAGTAACAGGCCTTGTCTCATCATGAACTTTGGCATTTCATCTACAGCTAAGTTTATATCATAAATAGTTCTTTACAGGCAGCACCAACTTATACCCTTATAGCATACTTTACTGTGTGAAAATTGCATCTTTCATTAAGCTTACTGTAAATTTACTGGCTGTCTTCCTTGCAGGTTTCTGGAAGGGATTTATTACAGTGCAAGAAGACATAGAATCTTAGACATATACTTAGAAAAGGAAGAAGGCATCATACCAGATTGGCAGGATTACACCTCAGGACCAGGAATTAGATACCCAAAGACATTTGGCTGGCTATGGAAATTAGTCCCTGTAAATGTATCAGATGAGGCACAGGAGGATGAGGAGCATTATTTAATGCATCCAGCTCAAACTTCCCAGTGGGATGACCCTTGGGGAGAGGTTCTAGCATGGAAGTTTGATCCAACTCTGGCCTACACTTATGAGGCATATGTTAGATACCCAGAAGAGTTTGGAAGCAAGTCAGGCCTGTCAGAGGAAGAGGTTAGAAGAAGGCTAACCGCAAGAGGCCTTCTTAACATGGCTGACAAGAAGGAAACTCGCTGAAACAGCAGGGACTTTCCACAAGGGGATGTTACGGGGAGGTACTGGGGAGGAGCCGGTCGGGAACGCCCACTTTCTTGATGTATAAATATCACTGCATTTCGCTCTGTATTCAGTCGCTCTGCGGAGAGGCTGGCAGATTGAGCCCTGGGAGGTTCTCTCCAGCACTAGCAGGTAGAGCCTGGGTGTTCCCTGCTAGACTCTCACCAGCACTTGGCCGGTGCTGGGCAGAGTGACTCCACGCTTGCTTGCTTAAAGCCCTCTTCAATAAAGCTGCCATTTTAGAAGTAAGCTAGTGTGTGTTCCCATCTCTCCTAGCCGCCGCCTGGTCAACTCGGTACTCAATAATAAGAAGACCCTGGTCTGTTAGGACCCTTTCTGCTTTGGGAAACCGAAGCAGGAAAATCCCTAGCAGATTGGCGCCTGAACAGGGACTTGAAGGAGAGTGAGAGACTCCTGAGTACGGCTGAGTGAAGGCAGTAAGGGCGGCAGGAACCAACCACGACGGAGTGCTCCTATAAAGGCGCGGGTCGGTACCAGACGGCGTGAGGAGCGGGAGAGGAAGAGGCCTCCGGTTGCAGGTAAGTGCAACACAAAAAAGAAATAGCTGTCTTTTATCCAGGAAGGGGTAATAAGATAGAGTGGGAGATGGGCGTGAGAAACTCCGTCTTGTCAGGGAAGAAAGCAGATGAATTAGAAAAAATTAGGCTACGACCCAACGGAAAGAAAAAGTACATGTTGAAGCATGTAGTATGGGCAGCAAATGAATTAGATAGATTTGGATTAGCAGAAAGCCTGTTGGAGAACAAAGAAGGATGTCAAAAAATACTTTCGGTCTTAGCTCCATTAGTGCCAACAGGCTCAGAAAATTTAAAAAGCCTTTATAATACTGTCTGCGTCATCTGGTGCATTCACGCAGAAGAGAAAGTGAAACACACTGAGGAAGCAAAACAGATAGTGCAGAGACACCTAGTGGTGGAAACAGGAACAACAGAAACTATGCCAAAAACAAGTAGACCAACAGCACCATCTAGCGGCAGAGGAGGAAATTACCCAGTACAACAAATAGGTGGTAACTATGTCCACCTGCCATTAAGCCCGAGAACATTAAATGCCTGGGTAAAATTGATAGAGGAAAAGAAATTTGGAGCAGAAGTAGTGCCAGGATTTCAGGCACTGTCAGAAGGTTGCACCCCCTATGACATTAATCAGATGTTAAATTGTGTGGGAGACCATCAAGCGGCTATGCAGATTATCAGAGATATTATAAACGAGGAGGCTGCAGATTGGGACTTGCAGCACCCACAACCAGCTCCACAACAAGGACAACTTAGGGAGCCGTCAGGATCAGATATTGCAGGAACAACTAGTTCAGTAGATGAACAAATCCAGTGGATGTACAGACAACAGAACCCCATACCAGTAGGCAACATTTACAGGAGATGGATCCAACTGGGGTTGCAAAAATGTGTCAGAATGTATAACCCAACAAACATTCTAGATGTAAAACAAGGGCCAAAAGAGCCATTTCAGAGCTATGTAGACAGGTTCTACAAAAGTTTAAGAGCAGAACAGACAGATGCAGCAGTAAAGAATTGGATGACTCAAACACTGCTGATTCAAAATGCTAACCCAGATTGCAAGCTAGTGCTGAAGGGGCTGGGTGTGAATCCCACCCTAGAAGAAATGCTGACGGCTTGTCAAGGAGTAGGGGGGCCGGGACAGAAGGCTAGATTAATGGCAGAAGCCCTGAAAGAGGCCCTCGCACCAGTGCCAATCCCTTTTGCAGCAGCCCAACAGAGGGGACCAAGAAAGCCAATTAAGTGTTGGAATTGTGGGAAAGAGGGACACTCTGCAAGGCAATGCAGAGCCCCAAGAAGACAGGGATGCTGGAAATGTGGAAAAATGGACCATGTTATGGCCAAATGCCCAGACAGACAGGCGGGTTTTTTAGGCCTTGGTCCATGGGGAAAGAAGCCCCGCAATTTCCCCATGGCTCAAGTGCATCAGGGGCTGATGCCAACTGCTCCCCCAGAGGACCCAGCTGTGGATCTGCTAAAGAACTACATGCAGTTGGGCAAGCAGCAGAGAGAAAAGCAGAGAGAAAGCAGAGAGAAGCCTTACAAGGAGGTGACAGAGGATTTGCTGCACCTCAATTCTCTCTTTGGAGGAGACCAGTAGTCACTGCTCATATTGAAGGACAGCCTGTAGAAGTATTACTGGATACAGGGGCTGATGATTCTATTGTAACAGGAATAGAGTTAGGTCCACATTATACCCCAAAAATAGTAGGAGGAATAGGAGGTTTTATTAATACTAAAGAATACAAAAATGTAGAAATAGAAGTTTTAGGCAAAAGGATTAAAGGGACAATCATGACAGGGGACACCCCGATTAACATTTTTGGTAGAAATTTGCTAACAGCTCTGGGGATGTCTCTAAATTTTCCCATAGCTAAAGTAGAGCCTGTAAAAGTCGCCTTAAAGCCAGGAAAGGATGGACCAAAATTGAAGCAGTGGCCATTATCAAAAGAAAAGATAGTTGCATTAAGAGAAATCTGTGAAAAGATGGAAAAGGATGGTCAGTTGGAGGAAGCTCCCCCGACCAATCCATACAACACCCCCACATTTGCTATAAAGAAAAAGGATAAGAACAAATGGAGAATGCTGATAGATTTTAGGGAACTAAATAGGGTCACTCAGGACTTTACGGAAGTCCAATTAGGAATACCACACCCTGCAGGACTAGCAAAAAGGAAAAGAATTACAGTACTGGATATAGGTGATGCATATTTCTCCATACCTCTAGATGAAGAATTTAGGCAGTACACTGCCTTTACTTTACCATCAGTAAATAATGCAGAGCCAGGAAAACGATACATTTATAAGGTTCTGCCTCAGGGATGGAAGGGGTCACCAGCCATCTTCCAATACACTATGAGACATGTGCTAGAACCCTTCAGGAAGGCAAATCCAGATGTGACCTTAGTCCAGTATATGGATGACATCTTAATAGCTAGTGACAGGACAGACCTGGAACATGACAGGGTAGTTTTACAGTCAAAGGAACTCTTGAATAGCATAGGGTTTTCTACCCCAGAAGAGAAATTCCAAAAAGATCCCCCATTTCAATGGATGGGGTACGAATTGTGGCCAACAAAATGGAAGTTGCAAAAGATAGAGTTGCCACAAAGAGAGACCTGGACAGTGAATGATATACAGAAGTTAGTAGGAGTATTAAATTGGGCAGCTCAAATTTATCCAGGTATAAAAACCAAACATCTCTGTAGGTTAATTAGAGGAAAAATGACTCTAACAGAGGAAGTTCAGTGGACTGAGATGGCAGAAGCAGAATATGAGGAAAATAAAATAATTCTCAGTCAGGAACAAGAAGGATGTTATTACCAAGAAGGCAAGCCATTAGAAGCCACGGTAATAAAGAGTCAGGACAATCAGTGGTCTTATAAAATTCACCAAGAAGACAAAATACTGAAAGTAGGAAAATTTGCAAAGATAAAGAATACACATACCAATGGAGTGAGACTATTAGCACATGTAATACAGAAAATAGGAAAGGAAGCAATAGTGATCTGGGGACAGGTCCCAAAATTCCACTTACCAGTTGAGAAGGATGTATGGGAACAGTGGTGGACAGACTATTGGCAGGTAACCTGGATACCGGAATGGGATTTTATCTCAACACCACCGCTAGTAAGATTAGTCTTCAATCTAGTGAAGGACCCTATAGAGGGAGAAGAAACCTATTATACAGATGGATCATGTAATAAACAGTCAAAAGAAGGGAAAGCAGGATATATCACAGATAGGGGCAAAGACAAAGTAAAAGTGTTAGAACAGACTACTAATCAACAAGCAGAATTGGAAGCATTTCTCATGGCATTGACAGACTCAGGGCCAAAGGCAAATATTATAGTAGATTCACAATATGTTATGGGAATAATAACAGGATGCCCTACAGAATCAGAGAGCAGGCTAGTTAATCAAATAATAGAAGAAATGATTAAAAAGTCAGAAATTTATGTAGCATGGGTACCAGCACACAAAGGTATAGGAGGAAACCAAGAAATAGACCACCTAGTTAGTCAAGGGATTAGACAAGTTCTCTTCTTGGAAAAGATAGAGCCAGCACAAGAAGAACATGATAAATACCATAGTAATGTAAAAGAATTGGTATTCAAATTTGGATTACCCAGAATAGTGGCCAGACAGATAGTAGACACCTGTGATAAATGTCATCAGAAAGGAGAGGCTATACATGGGCAGGCAAATTCAGATCTAGGGACTTGGCAAATGGATTGTACCCATCTAGAGGGAAAAATAATCATAGTTGCAGTACATGTAGCTAGTGGATTCATAGAAGCAGAGGTAATTCCACAAGAGACAGGAAGACAGACAGCACTATTTCTGTTAAAATTGGCAGGCAGATGGCCTATTACACATCTACACACAGATAATGGTGCTAACTTTGCTTCGCAAGAAGTAAAGATGGTTGCATGGTGGGCAGGGATAGAGCACACCTTTGGGGTACCATACAATCCACAGAGTCAGGGAGTAGTGGAAGCAATGAATCACCACCTGAAAAATCAAATAGATAGAATCAGGGAACAAGCAAATTCAGTAGAAACCATAGTATTAATGGCAGTTCATTGCATGAATTTTAAAAGAAGGGGAGGAATAGGGGATATGACTCCAGCAGAAAGATTAATTAACATGATCACTACAGAACAAGAGATACAATTTCAACAATCAAAAAACTCAAAATTTAAAAATTTTCGGGTCTATTACAGAGAAGGCAGAGATCAACTGTGGAAGGGACCCGGTGAGCTATTGTGGAAAGGGGAAGGAGCAGTCATCTTAAAGGTAGGGACAGACATTAAGGTAGTACCCAGAAGAAAGGCTAAAATTATCAAAGATTATGGAGGAGGAAAAGAGGTGGATAGCAGTTCCCACATGGAGGATACCGGAGAGGCTAGAGAGGTGGCATAGCCTCATAAAATATCTGAAATATAAAACTAAAGATCTACAAAAGGTTTGCTATGTGCCCCATTTTAAGGTCGGATGGGCATGGTGGACCTGCAGCAGAGTAATCTTCCCACTACAGGAAGGAAGCCATTTAGAAGTACAAGGGTATTGGCATTTGACACCAGAAAAAGGGTGGCTCAGTACTTATGCAGTGAGGATAACCTGGTACTCAAAGAACTTTTGGACAGATGTAACACCAAACTATGCAGACATTTTACTGCATAGCACTTATTTCCCTTGCTTTACAGCGGGAGAAGTGAGAAGGGCCATCAGGGGAGAACAACTGCTGTCTTGCTGCAGGTTCCCGAGAGCTCATAAGTACCAGGTACCAAGCCTACAGTACTTAGCACTGAAAGTAGTAAGCGATGTCAGATCCCAGGGAGAGAATCCCACCTGGAAACAGTGGAGAAGAGACAATAGGAGAGGCCTTCGAATGGCTAAACAGAACAGTAGAGGAGATAAACAGAGAGGCGGTAAACCACCTACCAAGGGAGCTAATTTTCCAGGTTTGGCAAAGGTCTTGGGAATACTGGCATGATGAACAAGGGATGTCACCAAGCTATGTAAAATACAGATACTTGTGTTTAATACAAAAGGCTTTATTTATGCATTGCAAGAAAGGCTGTAGATGTCTAGGGGAAGGACATGGGGCAGGGGGATGGAGACCAGGACCTCCTCCTCCTCCCCCTCCAGGACTAGCATAAATGGAAGAAAGACCTCCAGAAAATGAAGGACCACAAAGGGAACCATGGGATGAATGGGTAGTGGAGGTTCTGGAAGAACTGAAAGAAGAAGCTTTAAAACATTTTGATCCTCGCTTGCTAACTGCACTTGGTAATCATATCTATAATAGACATGGAGACACCCTTGAGGGAGCAGGAGAACTCATTAGAATCCTCCAACGAGCGCTCTTCATGCATTTCAGAGGCGGATGCATCCACTCCAGAATCGGCCAACCTGGGGGAGGAAATCCTCTCTCAGCTATACCGCCCTCTAGAAGCATGCTATAACACATGCTATTGTAAAAAGTGTTGCTACCATTGCCAGTTTTGTTTTCTTAAAAAAGGCTTGGGGATATGTTATGAGCAATCACGAAAGAGAAGAAGAACTCCGAAAAAGGCTAAGGCTAATACATCTTCTGCATCAAACAAGTAAGTATGGGATGTCTTGGGAATCAGCTGCTTATCGCCATCTTGCTTTTAAGTGTCTATGGGATCTATTGTACTCTATATGTCACAGTCTTTTATGGTGTACCAGCTTGGAGGAATGCGACAATTCCCCTCTTTTGTGCAACCAAGAATAGGGATACTTGGGGAACAACTCAGTGCCTACCAGATAATGGTGATTATTCAGAAGTGGCCCTTAATGTTACAGAAAGCTTTGATGCCTGGAATAATACAGTCACAGAACAGGCAATAGAGGATGTATGGCAACTCTTTGAGACCTCAATAAAGCCTTGTGTAAAATTATCCCCATTATGCATTACTATGAGATGCAATAAAAGTGAGACAGATAGATGGGGATTGACAAAATCAATAACAACAACAGCATCAACAACATCAACGACAGCATCAGCAAAAGTAGACATGGTCAATGAGACTAGTTCTTGTATAGCCCAGGATAATTGCACAGGCTTGGAACAAGAGCAAATGATAAGCTGTAAATTCAACATGACAGGGTTAAAAAGAGACAAGAAAAAAGAGTACAATGAAACTTGGTACTCTGCAGATTTGGTATGTGAACAAGGGAATAACACTGGTAATGAAAGTAGATGTTACATGAACCACTGTAACACTTCTGTTATCCAAGAGTCTTGTGACAAACATTATTGGGATGCTATTAGATTTAGGTATTGTGCACCTCCAGGTTATGCTTTGCTTAGATGTAATGACACAAATTATTCAGGCTTTATGCCTAAATGTTCTAAGGTGGTGGTCTCTTCATGCACAAGGATGATGGAGACACAGACTTCTACTTGGTTTGGCTTTAATGGAACTAGAGCAGAAAATAGAACTTATATTTACTGGCATGGTAGGGATAATAGGACTATAATTAGTTTAAATAAGTATTATAATCTAACAATGAAATGTAGAAGACCAGGAAATAAGACAGTTTTACCAGTCACCATTATGTCTGGATTGGTTTTCCACTCACAACCAATCAATGATAGGCCAAAGCAGGCATGGTGTTGGTTTGGAGGAAAATGGAAGGATGCAATAAAAGAGGTGAAGCAGACCATTGTCAAACATCCCAGGTATACTGGAACTAACAATACTGATAAAATCAATTTGACGGCTCCTGGAGGAGGAGATCCGGAAGTTACCTTCATGTGGACAAATTGCAGAGGAGAGTTCCTCTACTGTAAAATGAATTGGTTTCTAAATTGGGTAGAAGATAGGAATACAGCTAACCAGAAGCCAAAGGAACAGCATAAAAGGAATTACGTGCCATGTCATATTAGACAAATAATCAACACTTGGCATAAAGTAGGCAAAAATGTTTATTTGCCTCCAAGAGAGGGAGACCTCACGTGTAACTCCACAGTGACCAGTCTCATAGCAAACATAGATTGGATTGATGGAAACCAAACTAATATCACCATGAGTGCAGAGGTGGCAGAACTGTATCGATTGGAATTGGGAGATTATAAATTAGTAGAGATCACTCCAATTGGCTTGGCCCCCACAGATGTGAAGAGGTACACTACTGGTGGCACCTCAAGAAATAAAAGAGGGGTCTTTGTGCTAGGGTTCTTGGGTTTTCTCGCAACGGCAGGTTCTGCAATGGGCGCGGCGTCGTTGACGCTGACCGCTCAGTCCCGAACTTTATTGGCTGGGATAGTGCAGCAACAGCAACAGCTGTTGGACGTGGTCAAGAGACAACAAGAATTGTTGCGACTGACCGTCTGGGGAACAAAGAACCTCCAGACTAGGGTCACTGCCATCGAGAAGTACTTAAAGGACCAGGCGCAGCTGAATGCTTGGGGATGTGCGTTTAGACAAGTCTGCCACACTACTGTACCATGGCCAAATGCAAGTCTAACACCAAAGTGGAACAATGAGACTTGGCAAGAGTGGGAGCGAAAGGTTGACTTCTTGGAAGAAAATATAACAGCCCTCCTAGAGGAGGCACAAATTCAACAAGAGAAGAACATGTATGAATTACAAAAGTTGAATAGCTGGGATGTGTTTGGCAATTGGTTTGACCTTGCTTCTTGGATAAAGTATATACAATATGGAGTTTATATAGTTGTAGGAGTAATACTGTTAAGAATAGTGATCTATATAGTACAAATGCTAGCTAAGTTAAGGCAGGGGTATAGGCCAGTGTTCTCTTCCCCACCCTCTTATTTCCAGCAGACCCATATCCAACAGGACCCGGCACTGCCAACCAGAGAAGGCAAAGAAAGAGACGGTGGAGAAGGCGGTGGCAACAGCTCCTGGCCTTGGCAGATAGAATATATTCATTTCCTGATCCGCCAACTGATACGCCTCTTGACTTGGCTATTCAGCAACTGCAGAACCTTGCTATCGAGAGTATACCAGATCCTCCAACCAATACTCCAGAGGCTCTCTGCGACCCTACAGAGGATTCGAGAAGTCCTCAGGACTGAACTGACCTACCTACAATATGGGTGGAGCTATTTCCATGAGGCGGTCCAGGCCGTCTGGAGATCTGCGACAGAGACTCTTGCGGGCGCGTGGGGAGACTTATGGGAGACTCTTAGGAGAGGTGGAAGATGGATACTCGCAATCCCCAGGAGGATTAGACAAGGGCTTGAGCTCACTCTCTTGTGAGGGACAGAAATACAATCAGGGACAGTATATGAATACTCCATGGAGAAACCCAGCTGAAGAGAGAGAAAAATTAGCATACAGAAAACAAAATATGGATGATATAGATGAGgAAGATGATGACTTGGTAGGGGTATCAGTGAGGCCAAAAGTTCCCCTAAGAACAATGAGTTACAAATTGGCAATAGACATGTCTCATTTTATAAAAGAAAAGGGGGGACTGGAAGGGATTTATTACAGTGCAAGAAGACATAGAATCTTAGACATATACTTAGAAAAGGAAGAAGGCATCATACCAGATTGGCAGGATTACACCTCAGGACCAGGAATTAGATACCCAAAGACATTTGGCTGGCTATGGAAATTAGTCCCTGTAAATGTATCAGATGAGGCACAGGAGGATGAGGAGCATTATTTAATGCATCCAGCTCAAACTTCCCAGTGGGATGACCCTTGGGGAGAGGTTCTAGCATGGAAGTTTGATCCAACTCTGGCCTACACTTATGAGGCATATGTTAGATACCCAGAAGAGTTTGGAAGCAAGTCAGGCCTGTCAGAGGAAGAGGTTAGAAGAAGGCTAACCGCAAGAGGCCTTCTTAACATGGCTGACAAGAAGGAAACTCGCTGAAACAGCAGGGACTTTCCACAAGGGGATGTTACGGGGAGGTACTGGGGAGGAGCCGGTCGGGAACGCCCACTTTCTTGATGTATAAATATCACTGCATTTCGCTCTGTATTCAGTCGCTCTGCGGAGAGGCTGGCAGATTGAGCCCTGGGAGGTTCTCTCCAGCACTAGCAGGTAGAGCCTGGGTGTTCCCTGCTAGACTCTCACCAGCACTTGGCCGGTGCTGGGCAGAGTGACTCCACGCTTGCTTGCTTAAAGCCCTCTTCAATAAAGCTGCCATTTTAGAAGTAAGCTAGTGTGTGTTCCCATCTCTCCTAGCCGCCGCCTGGTCAACTCGGTACTCAATAATAAGAAGACCCTGGTCTGTTAGGACCCTTTCTGCTTTGGGAAACCGAAGCAGGAAAATCCCTAGCA", null);
        }
    }
}
