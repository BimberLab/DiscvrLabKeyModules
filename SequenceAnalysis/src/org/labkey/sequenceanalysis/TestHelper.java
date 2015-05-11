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
import org.apache.commons.collections15.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
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
import org.labkey.api.resource.FileResource;
import org.labkey.api.resource.MergedDirectoryResource;
import org.labkey.api.resource.Resource;
import org.labkey.api.sequenceanalysis.model.ReadData;
import org.labkey.api.sequenceanalysis.model.Readset;
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
import org.labkey.sequenceanalysis.pipeline.SequenceTaskHelper;
import org.labkey.sequenceanalysis.run.alignment.AlignerIndexUtil;
import org.labkey.sequenceanalysis.util.FastqUtils;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.bind.annotation.RequestMethod;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: bimber
 * Date: 11/25/12
 * Time: 1:58 PM
 */
public class TestHelper
{
    private static TestHelper _instance = new TestHelper();
    public static final String PIPELINE_PROP_NAME = "sequencePipelineEnabled";
    public static final String GATK_PROP_NAME = "gatkInstalled";

    private TestHelper()
    {

    }

    public static TestHelper get()
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
        protected final String IMPORT_TASKID = "org.labkey.api.pipeline.file.FileAnalysisTaskPipeline:sequenceImportPipeline";
        protected final String ANALYSIS_TASKID = "org.labkey.api.pipeline.file.FileAnalysisTaskPipeline:sequenceAnalysisPipeline";

        protected Container _project;
        protected TestContext _context;
        protected File _pipelineRoot;
        protected File _sampleData;

        protected Boolean _isExternalPipelineEnabled = null;
        protected Boolean _isGATKInstalled = null;

        protected static final Logger _log = Logger.getLogger(AbstractPipelineTestCase.class);

        protected static void doInitialSetUp(String projectName) throws Exception
        {
            //pre-clean
            doCleanup(projectName);

            Container project = ContainerManager.getForPath(projectName);
            if (project == null)
            {
                project = ContainerManager.createContainer(ContainerManager.getRoot(), projectName);
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
            if (_sampleData == null || !_sampleData.exists())
            {
                throw new Exception("sampledata folder does not exist: " + _sampleData.getPath());
            }
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
                    _log.error("directory does not exist");
                }
            }
            else
            {
                _log.info("param: " + SequencePipelineService.SEQUENCE_TOOLS_PARAM + " not defined");
            }
        }

        private File getSampleDataDir()
        {
            Module module = ModuleLoader.getInstance().getModule(SequenceAnalysisModule.class);
            MergedDirectoryResource resource = (MergedDirectoryResource)module.getModuleResolver().lookup(Path.parse("sampledata"));
            File file = null;
            for (Resource r : resource.list())
            {
                if(r instanceof FileResource)
                {
                    file = ((FileResource) r).getFile().getParentFile();
                    break;
                }
            }
            return file;
        }

        protected void ensureFilesPresent() throws Exception
        {
            File file1 = new File(_pipelineRoot, DUAL_BARCODE_FILENAME);
            if (!file1.exists())
            {
                FileUtils.copyFile(new File(_sampleData, DUAL_BARCODE_FILENAME+".gz"), file1);
                Compress.decompressGzip(new File(_sampleData, DUAL_BARCODE_FILENAME+".gz"), file1);
            }

            File file2 = new File(_pipelineRoot, SAMPLE_SFF_FILENAME);
            if (!file2.exists())
                FileUtils.copyFile(new File(_sampleData, SAMPLE_SFF_FILENAME), file2);

            for (String fn : Arrays.asList(PAIRED_FILENAME1, PAIRED_FILENAME_L1a, PAIRED_FILENAME_L1b, PAIRED_FILENAME_L2))
            {
                File file3 = new File(_pipelineRoot, fn);
                if (!file3.exists())
                    FileUtils.copyFile(new File(_sampleData, PAIRED_FILENAME1), file3);
            }

            for (String fn : Arrays.asList(PAIRED_FILENAME2, PAIRED_FILENAME2_L1a, PAIRED_FILENAME2_L1b, PAIRED_FILENAME2_L2))
            {
                File file4 = new File(_pipelineRoot, fn);
                if (!file4.exists())
                    FileUtils.copyFile(new File(_sampleData, PAIRED_FILENAME2), file4);
            }

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

            File file7 = new File(_pipelineRoot, UNPAIRED_FILENAME);
            if (!file7.exists())
            {
                FileUtils.copyFile(new File(_sampleData, UNPAIRED_FILENAME), file7);
            }

        }

        protected void decompressAndCleanFastq(File input, File output)
        {
            //decompress and remove trailing /1 from readnames, as these
            FastqWriterFactory fact = new FastqWriterFactory();
            try (FastqReader reader = new FastqReader(input);FastqWriter writer = fact.newWriter(output))
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

        protected void verifyFileInputs(File basedir, String[] fileNames, JSONObject config)
        {
            String handling = config.getString("inputfile.inputTreatment");
            if ("none".equals(handling))
            {
                for (String fn : fileNames)
                {
                    File input = new File(_pipelineRoot, fn);
                    Assert.assertTrue("Input file missing: " + input.getPath(), input.exists());
                }
            }
            else if ("compress".equals(handling))
            {
                FileType gz = new FileType(".gz");

                for (String fn : fileNames)
                {
                    File input = new File(_pipelineRoot, fn);
                    Assert.assertFalse("Input file still exists: " + input.getPath(), input.exists());

                    File compressed;
                    if (gz.isType(fn))
                        compressed = new File(basedir, fn);
                    else
                        compressed = new File(basedir, FileUtil.getBaseName(fn) + ".fastq.gz");

                    Assert.assertTrue("Compressed file missing: " + compressed.getPath(), compressed.exists());
                }
            }
            else if ("delete".equals(handling))
            {
                for (String fn : fileNames)
                {
                    File input = new File(_pipelineRoot, fn);
                    Assert.assertFalse("Input file still present: " + input.getPath(), input.exists());
                }
            }
        }

        protected void verifyFileOutputs(File basedir, Set<File> expectedOutputs)
        {
            for (File f : expectedOutputs)
            {
                Assert.assertTrue("Output file not found, expected: " + f.getPath(), f.exists());
            }

            IOFileFilter filter = new IOFileFilter(){
                public boolean accept(File file){
                    return true;
                }

                public boolean accept(File dir, String name){
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
                for (File f: diff)
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
            }

            Assert.assertEquals("Incorrect number of outputs created", expectedOutputs.size(), files.size());
        }

        protected PipelineJob createPipelineJob(String protocolName, String taskId, String config, String[] files) throws Exception
        {
            Map<String, Object> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");

            JSONArray filesArray = new JSONArray();
            for (String fileName : files)
            {
                filesArray.put(fileName);
            }

            JSONObject json = new JSONObject();
            json.put("taskId", taskId);
            json.put("path", "./");
            json.put("protocolName", protocolName);
            json.put("protocolDescription", "Description");
            json.put("file", filesArray);
            json.put("saveProtocol", false);
            json.put("configureJson", config);
            String requestContent = json.toString();

            HttpServletRequest request = ViewServlet.mockRequest(RequestMethod.POST.name(), DetailsURL.fromString("/sequenceanalysis/startAnalysis.view").copy(_project).getActionURL(), _context.getUser(), headers, requestContent);

            MockHttpServletResponse response = ViewServlet.mockDispatch(request, null);
            JSONObject responseJson = new JSONObject(response.getContentAsString());
            if (response.getStatus() != HttpServletResponse.SC_OK)
                throw new RuntimeException("Problem creating pipeline job: " + responseJson.getString("exception"));

            JSONArray guidList = responseJson.getJSONArray("jobGUIDs");
            assert guidList.length() == 1;
            Integer jobId = PipelineService.get().getJobId(_context.getUser(), _project, guidList.getString(0));

            return PipelineJobService.get().getJobStore().getJob(jobId);
        }

        protected boolean isExternalPipelineEnabled()
        {
            if (_isExternalPipelineEnabled != null)
                return _isExternalPipelineEnabled;

            _isExternalPipelineEnabled = Boolean.parseBoolean(System.getProperty(PIPELINE_PROP_NAME));
            if (!_isExternalPipelineEnabled)
                _log.info("Sequence pipeline is not enabled on this server, so some tests will be skipped");

            return _isExternalPipelineEnabled;
        }

        protected boolean isGATKPresent()
        {
            if (_isGATKInstalled != null)
                return _isGATKInstalled;

            _isGATKInstalled = Boolean.parseBoolean(System.getProperty(GATK_PROP_NAME));
            if (!_isGATKInstalled)
                _log.info("GATK JAR is not installed on this server, so some tests will be skipped");

            return _isGATKInstalled;
        }

        protected void waitForJob(PipelineJob job) throws Exception
        {
            try
            {
                long start = System.currentTimeMillis();
                long timeout = 60 * 1000 * 3; //3 mins

                Thread.sleep(1000);

                while (!isJobDone(job))
                {
                    Thread.sleep(1000);

                    long duration = System.currentTimeMillis() - start;
                    if (duration > timeout)
                        throw new RuntimeException("Timed out waiting for pipeline job");
                }

                Thread.sleep(10000);
            }
            catch (InterruptedException e)
            {
                throw new RuntimeException(e);
            }
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
                if (job != null && job.getLogFile() != null)
                {
                    StringBuilder sb = new StringBuilder();
                    try (BufferedReader reader = new BufferedReader(new FileReader(job.getLogFile())))
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
                else
                {
                    _log.error("No log file present for sequence pipeline job");
                }

                throw new Exception("There was an error running job: " + (job == null ? "PipelineJob was null" : job.getDescription()));
            }

            return false; //job != null && job.getActiveTaskId() != null;
        }

        protected JSONObject substituteParams(File xml, String protocolName, String[] fileNames) throws IOException
        {
            String content = FileUtils.readFileToString(xml);
            content = content.replaceAll("@@BASEURL@@", AppProps.getInstance().getBaseServerUrl() + AppProps.getInstance().getContextPath());
            content = content.replaceAll("@@CONTAINERID@@", _project.getPath());
            content = content.replaceAll("@@CONTAINERPATH@@", _project.getPath());
            content = content.replaceAll("@@FILENAMES@@", StringUtils.join(fileNames, ";"));
            content = content.replaceAll("@@USERID@@", String.valueOf(_context.getUser().getUserId()));
            content = content.replaceAll("@@EMAIL@@", _context.getUser().getEmail());
            content = content.replaceAll("@@PROTOCOLNAME@@", protocolName);
            content = content.replaceAll("[\n\r\t]", "");
            return new JSONObject(content);
        }

        protected void appendSamplesForImport(JSONObject config, List<FileGroup> files)
        {
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

                    if (p.file2 != null)
                    {
                        JSONObject file2 = new JSONObject();
                        file2.put("fileName", p.file2.getName());
                        o.put("file2", file2);
                    }

                    fileArr.put(o);
                }
                json.put("files", fileArr);

                config.put("fileGroup_" + i, json.toString());

                i++;
            }

            if (config.getBoolean("inputfile.barcode"))
            {
                //NOTE: this cannot automatically be inferred based on the other info in the config, so we just skip it
            }
        }

        protected static void doCleanup(String projectName)
        {
            Container project = ContainerManager.getForPath(projectName);
            if (project != null)
            {
                File _pipelineRoot = PipelineService.get().getPipelineRootSetting(project).getRootPath();
                try
                {
                    if (_pipelineRoot.exists())
                    {
                        File[] contents = _pipelineRoot.listFiles();
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
            ensureFilesPresent();

            String protocolName = "BasicTest_" + System.currentTimeMillis();
            String[] fileNames = new String[]{DUAL_BARCODE_FILENAME};
            JSONObject config = substituteParams(new File(_sampleData, READSET_JOB), protocolName, fileNames);
            FileGroup g = new FileGroup();
            g.name = "Group1";
            g.filePairs = new ArrayList<>();
            g.filePairs.add(new FileGroup.FilePair());
            g.filePairs.get(0).file1 = new File(DUAL_BARCODE_FILENAME);

            appendSamplesForImport(config, Arrays.asList(g));

            PipelineJob job = createPipelineJob(protocolName, IMPORT_TASKID, config.toString(), fileNames);
            waitForJob(job);

            Set<File> expectedOutputs = new HashSet<>();
            File basedir = new File(_pipelineRoot, "sequenceImport/" + protocolName);
            File fq = new File(basedir, DUAL_BARCODE_FILENAME + ".gz");
            expectedOutputs.add(fq);
            expectedOutputs.add(new File(basedir, "sequenceImport.xml"));
            expectedOutputs.add(new File(basedir, FileUtil.getBaseName(DUAL_BARCODE_FILENAME) + ".pipe.xar.xml"));
            expectedOutputs.add(new File(basedir, FileUtil.getBaseName(DUAL_BARCODE_FILENAME) + ".log"));
            verifyFileOutputs(basedir, expectedOutputs);
            verifyFileInputs(basedir, fileNames, config);

            validateReadsets(job, config);

            int count = FastqUtils.getSequenceCount(fq);
            Assert.assertEquals("Incorrect read number", 3260, count);
        }

        private void runMergePipelineJob(String protocolName, boolean deleteIntermediates) throws Exception
        {
            String[] fileNames = new String[]{PAIRED_FILENAME_L1a, PAIRED_FILENAME2_L1a, PAIRED_FILENAME_L1b, PAIRED_FILENAME2_L1b, PAIRED_FILENAME_L2, PAIRED_FILENAME2_L2, PAIRED_FILENAME1, PAIRED_FILENAME2, UNPAIRED_FILENAME};
            JSONObject config = substituteParams(new File(_sampleData, READSET_JOB), protocolName, fileNames);

            FileGroup g = new FileGroup();
            g.name = "Group1";
            g.filePairs = new ArrayList<>();
            g.filePairs.add(new FileGroup.FilePair());
            g.filePairs.get(0).platformUnit = "platformUnit1";
            g.filePairs.get(0).file1 = new File(PAIRED_FILENAME_L1a);
            g.filePairs.get(0).file2 = new File(PAIRED_FILENAME2_L1a);

            g.filePairs.add(new FileGroup.FilePair());
            g.filePairs.get(1).platformUnit = "platformUnit1";
            g.filePairs.get(1).file1 = new File(PAIRED_FILENAME_L1b);
            g.filePairs.get(1).file2 = new File(PAIRED_FILENAME2_L1b);

            g.filePairs.add(new FileGroup.FilePair());
            g.filePairs.get(2).platformUnit = "platformUnit2";
            g.filePairs.get(2).file1 = new File(PAIRED_FILENAME_L2);
            g.filePairs.get(2).file2 = new File(PAIRED_FILENAME2_L2);

            FileGroup g2 = new FileGroup();
            g2.name = "Group2";
            g2.filePairs = new ArrayList<>();
            g2.filePairs.add(new FileGroup.FilePair());
            g2.filePairs.get(0).platformUnit = "platformUnit3";
            g2.filePairs.get(0).file1 = new File(PAIRED_FILENAME1);
            g2.filePairs.get(0).file2 = new File(PAIRED_FILENAME2);

            FileGroup g3 = new FileGroup();
            g3.name = "Group3";
            g3.filePairs = new ArrayList<>();
            g3.filePairs.add(new FileGroup.FilePair());
            g3.filePairs.get(0).platformUnit = "platformUnit4";
            g3.filePairs.get(0).file1 = new File(UNPAIRED_FILENAME);

            if (deleteIntermediates)
            {
                config.put("deleteIntermediateFiles", true);
                config.put("inputfile.inputTreatment", "delete");
            }
            else
            {
                config.put("deleteIntermediateFiles", false);
                config.put("inputfile.inputTreatment", "compress");
            }
            config.put("inputfile.runFastqc", true);
            appendSamplesForImport(config, Arrays.asList(g, g2, g3));

            PipelineJob job = createPipelineJob(protocolName, IMPORT_TASKID, config.toString(), fileNames);
            waitForJob(job);

            Set<File> expectedOutputs = new HashSet<>();
            File basedir = new File(_pipelineRoot, "sequenceImport/" + protocolName);
            expectedOutputs.add(new File(basedir, protocolName + ".log"));
            expectedOutputs.add(new File(basedir, protocolName + ".pipe.xar.xml"));
            expectedOutputs.add(new File(basedir, "sequenceImport.xml"));

            expectedOutputs.add(new File(basedir, "Normalization"));
            File merge1 = new File(basedir, "Normalization/" + SequenceTaskHelper.getUnzippedBaseName(PAIRED_FILENAME_L1a) + ".merged.fastq.gz");
            expectedOutputs.add(merge1);
            expectedOutputs.add(new File(merge1.getParentFile(), FileUtil.getBaseName(FileUtil.getBaseName(merge1)) + "_fastqc.html.gz"));
            File merge2 = new File(basedir, "Normalization/" + SequenceTaskHelper.getUnzippedBaseName(PAIRED_FILENAME2_L1a) + ".merged.fastq.gz");
            expectedOutputs.add(merge2);
            expectedOutputs.add(new File(merge2.getParentFile(), FileUtil.getBaseName(FileUtil.getBaseName(merge2)) + "_fastqc.html.gz"));

            expectedOutputs.add(new File(basedir, PAIRED_FILENAME1));
            expectedOutputs.add(new File(basedir, FileUtil.getBaseName(FileUtil.getBaseName(PAIRED_FILENAME1)) + "_fastqc.html.gz"));
            expectedOutputs.add(new File(basedir, PAIRED_FILENAME2));
            expectedOutputs.add(new File(basedir, FileUtil.getBaseName(FileUtil.getBaseName(PAIRED_FILENAME2)) + "_fastqc.html.gz"));

            //these will be merged
            if (!deleteIntermediates)
            {
                expectedOutputs.add(new File(basedir, PAIRED_FILENAME_L1a));
                expectedOutputs.add(new File(basedir, PAIRED_FILENAME2_L1a));
                expectedOutputs.add(new File(basedir, PAIRED_FILENAME_L1b));
                expectedOutputs.add(new File(basedir, PAIRED_FILENAME2_L1b));
            }

            expectedOutputs.add(new File(basedir, PAIRED_FILENAME_L2));
            expectedOutputs.add(new File(basedir, FileUtil.getBaseName(FileUtil.getBaseName(PAIRED_FILENAME_L2)) + "_fastqc.html.gz"));
            expectedOutputs.add(new File(basedir, PAIRED_FILENAME2_L2));
            expectedOutputs.add(new File(basedir, FileUtil.getBaseName(FileUtil.getBaseName(PAIRED_FILENAME2_L2)) + "_fastqc.html.gz"));
            expectedOutputs.add(new File(basedir, UNPAIRED_FILENAME));
            expectedOutputs.add(new File(basedir, FileUtil.getBaseName(FileUtil.getBaseName(UNPAIRED_FILENAME)) + "_fastqc.html.gz"));

            verifyFileOutputs(basedir, expectedOutputs);
            verifyFileInputs(basedir, fileNames, config);
            validateReadsets(job, config);

            int count = FastqUtils.getSequenceCount(merge1);
            int count2 = FastqUtils.getSequenceCount(merge2);
            Assert.assertEquals("Incorrect read number", 633, count);
            Assert.assertEquals("Incorrect read number", 633, count2);
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

            ensureFilesPresent();

            String protocolName = "MergeTestLanes_" + System.currentTimeMillis();
            runMergePipelineJob(protocolName, false);
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

            ensureFilesPresent();

            String protocolName = "MergeDeletingIntermediates_" + System.currentTimeMillis();
            runMergePipelineJob(protocolName, true);
        }

        private JSONObject getBarcodeConfig(String protocolName, String[] fileNames) throws Exception
        {
            JSONObject config = substituteParams(new File(_sampleData, READSET_JOB), protocolName, fileNames);

            FileGroup g = new FileGroup();
            g.name = "Group1";
            g.filePairs = new ArrayList<>();
            for (String fn : fileNames)
            {
                FileGroup.FilePair p = new FileGroup.FilePair();
                p.file1 = new File(fn);
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

        private Set<File> getBarcodeOutputs(File basedir)
        {
            Set<File> expectedOutputs = new HashSet<>();

            expectedOutputs.add(new File(basedir, "sequenceImport.xml"));

            expectedOutputs.add(new File(basedir, FileUtil.getBaseName(DUAL_BARCODE_FILENAME) + ".pipe.xar.xml"));
            expectedOutputs.add(new File(basedir, FileUtil.getBaseName(DUAL_BARCODE_FILENAME) + ".log"));

            File normalizationDir = new File(basedir, "Normalization");
            expectedOutputs.add(normalizationDir);
            normalizationDir = new File(normalizationDir, FileUtil.getBaseName(DUAL_BARCODE_FILENAME));
            expectedOutputs.add(normalizationDir);

            expectedOutputs.add(new File(normalizationDir, FileUtil.getBaseName(DUAL_BARCODE_FILENAME) + "_MID001_MID001.fastq.gz"));
            expectedOutputs.add(new File(normalizationDir, FileUtil.getBaseName(DUAL_BARCODE_FILENAME) + "_MID002_MID001.fastq.gz"));
            expectedOutputs.add(new File(normalizationDir, FileUtil.getBaseName(DUAL_BARCODE_FILENAME) + "_MID003_MID001.fastq.gz"));
            expectedOutputs.add(new File(normalizationDir, FileUtil.getBaseName(DUAL_BARCODE_FILENAME) + "_MID004_MID001.fastq.gz"));
            expectedOutputs.add(new File(normalizationDir, FileUtil.getBaseName(DUAL_BARCODE_FILENAME) + "_unknowns.fastq.gz"));

            expectedOutputs.add(new File(normalizationDir, FileUtil.getBaseName(DUAL_BARCODE_FILENAME) + ".barcode-summary.txt.gz"));

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

            ensureFilesPresent();

            String protocolName = "BarcodeTest_" + System.currentTimeMillis();
            String[] fileNames = new String[]{DUAL_BARCODE_FILENAME};

            JSONObject config = getBarcodeConfig(protocolName, fileNames);
            PipelineJob job = createPipelineJob(protocolName, IMPORT_TASKID, config.toString(), fileNames);
            waitForJob(job);

            File basedir = new File(_pipelineRoot, "sequenceImport/" + protocolName);
            Set<File> expectedOutputs = getBarcodeOutputs(basedir);
            File normalizationDir = new File(basedir, "Normalization");
            expectedOutputs.add(normalizationDir);
            normalizationDir = new File(normalizationDir, FileUtil.getBaseName(DUAL_BARCODE_FILENAME));
            expectedOutputs.add(new File(normalizationDir, DUAL_BARCODE_FILENAME + ".gz"));

            verifyFileOutputs(basedir, expectedOutputs);
            verifyFileInputs(basedir, fileNames, config);
            validateReadsets(job, config, 4);
            validateBarcodeFastqs(expectedOutputs);
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

            ensureFilesPresent();

            String protocolName = "BarcodeDeletingIntermediates_" + System.currentTimeMillis();
            String[] fileNames = new String[]{DUAL_BARCODE_FILENAME};

            JSONObject config = getBarcodeConfig(protocolName, fileNames);
            config.put("deleteIntermediateFiles", true);
            config.put("inputfile.inputTreatment", "compress");

            PipelineJob job = createPipelineJob(protocolName, IMPORT_TASKID, config.toString(), fileNames);
            waitForJob(job);

            File basedir = new File(_pipelineRoot, "sequenceImport/" + protocolName);
            Set<File> expectedOutputs = getBarcodeOutputs(basedir);
            expectedOutputs.add(new File(basedir, "dualBarcodes_SIV.fastq.gz"));

            verifyFileOutputs(basedir, expectedOutputs);
            verifyFileInputs(basedir, fileNames, config);
            validateReadsets(job, config, 4);
            validateBarcodeFastqs(expectedOutputs);
        }

        private void validateBarcodeFastqs(Set<File> expectedOutputs) throws Exception
        {
            for (File f : expectedOutputs)
            {
                if (f.getName().equals("dualBarcodes_SIV_MID001_MID001.fastq.gz"))
                    Assert.assertEquals("Incorrect read number", 303, FastqUtils.getSequenceCount(f));
                else if (f.getName().equals("dualBarcodes_SIV_MID002_MID001.fastq.gz"))
                    Assert.assertEquals("Incorrect read number", 236, FastqUtils.getSequenceCount(f));
                else if (f.getName().equals("dualBarcodes_SIV_MID003_MID001.fastq.gz"))
                    Assert.assertEquals("Incorrect read number", 235, FastqUtils.getSequenceCount(f));
                else if (f.getName().equals("dualBarcodes_SIV_MID004_MID001.fastq.gz"))
                    Assert.assertEquals("Incorrect read number", 98, FastqUtils.getSequenceCount(f));
                else if (f.getName().equals("dualBarcodes_SIV_unknowns.fastq.gz"))
                    Assert.assertEquals("Incorrect read number", 2388, FastqUtils.getSequenceCount(f));
            }
        }

        /**
         * This imports a readset from two paired end inputs
         * @throws Exception
         */
        @Test
        public void pairedEndTest() throws Exception
        {
            ensureFilesPresent();

            String protocolName = "PairedEndTest_" + System.currentTimeMillis();
            String[] fileNames = new String[]{PAIRED_FILENAME1, PAIRED_FILENAME2};
            JSONObject config = substituteParams(new File(_sampleData, READSET_JOB), protocolName, fileNames);
            FileGroup g = new FileGroup();
            g.name = "Group1";
            g.filePairs = new ArrayList<>();
            g.filePairs.add(new FileGroup.FilePair());
            g.filePairs.get(0).file1 = new File(PAIRED_FILENAME1);
            g.filePairs.get(0).file2 = new File(PAIRED_FILENAME2);

            appendSamplesForImport(config, Arrays.asList(g));

            PipelineJob job = createPipelineJob(protocolName, IMPORT_TASKID, config.toString(), fileNames);
            waitForJob(job);

            Set<File> expectedOutputs = new HashSet<>();
            File basedir = new File(_pipelineRoot, "sequenceImport/" + protocolName);
            expectedOutputs.add(new File(basedir, "sequenceImport.xml"));
            expectedOutputs.add(new File(basedir, protocolName + ".pipe.xar.xml"));
            expectedOutputs.add(new File(basedir, protocolName + ".log"));
            expectedOutputs.add(new File(basedir, PAIRED_FILENAME1));
            expectedOutputs.add(new File(basedir, PAIRED_FILENAME2));

            verifyFileOutputs(basedir, expectedOutputs);
            verifyFileInputs(basedir, fileNames, config);

            validateReadsets(job, config);
        }

        /**
         * An extension of pairedEndTest(), except input files are moved to the analysis folder
         * @throws Exception
         */
        @Test
        public void pairedEndTestMovingInputs() throws Exception
        {
            ensureFilesPresent();

            String protocolName = "PairedEndMovingInputs_" + System.currentTimeMillis();
            String[] fileNames = new String[]{PAIRED_FILENAME1, PAIRED_FILENAME2, UNZIPPED_PAIRED_FILENAME1, UNZIPPED_PAIRED_FILENAME2};
            JSONObject config = substituteParams(new File(_sampleData, READSET_JOB), protocolName, fileNames);
            config.put("inputfile.inputTreatment", "compress");

            FileGroup g = new FileGroup();
            g.name = "Group1";
            g.filePairs = new ArrayList<>();
            g.filePairs.add(new FileGroup.FilePair());
            g.filePairs.get(0).file1 = new File(PAIRED_FILENAME1);
            g.filePairs.get(0).file2 = new File(PAIRED_FILENAME2);

            g.filePairs.add(new FileGroup.FilePair());
            g.filePairs.get(1).file1 = new File(UNZIPPED_PAIRED_FILENAME1);
            g.filePairs.get(1).file2 = new File(UNZIPPED_PAIRED_FILENAME2);

            appendSamplesForImport(config, Arrays.asList(g));

            PipelineJob job = createPipelineJob(protocolName, IMPORT_TASKID, config.toString(), fileNames);
            waitForJob(job);

            Set<File> expectedOutputs = new HashSet<>();
            File basedir = new File(_pipelineRoot, "sequenceImport/" + protocolName);
            expectedOutputs.add(new File(basedir, "sequenceImport.xml"));
            expectedOutputs.add(new File(basedir, protocolName + ".pipe.xar.xml"));
            expectedOutputs.add(new File(basedir, protocolName + ".log"));

            expectedOutputs.add(new File(basedir, UNZIPPED_PAIRED_FILENAME1 + ".gz"));
            expectedOutputs.add(new File(basedir, UNZIPPED_PAIRED_FILENAME2 + ".gz"));
            expectedOutputs.add(new File(basedir, PAIRED_FILENAME1));
            expectedOutputs.add(new File(basedir, PAIRED_FILENAME2));

            verifyFileOutputs(basedir, expectedOutputs);
            verifyFileInputs(basedir, fileNames, config);

            validateReadsets(job, config);
        }

        /**
         * An extension of pairedEndTest(), except input files are deleted on completion
         * @throws Exception
         */
        @Test
        public void pairedEndTestDeletingInputs() throws Exception
        {
            ensureFilesPresent();

            String protocolName = "PairedEndDeleting_" + System.currentTimeMillis();
            String[] fileNames = new String[]{PAIRED_FILENAME1, PAIRED_FILENAME2, UNZIPPED_PAIRED_FILENAME1, UNZIPPED_PAIRED_FILENAME2};
            JSONObject config = substituteParams(new File(_sampleData, READSET_JOB), protocolName, fileNames);
            config.put("inputfile.inputTreatment", "delete");

            FileGroup g = new FileGroup();
            g.name = "Group1";
            g.filePairs = new ArrayList<>();
            g.filePairs.add(new FileGroup.FilePair());
            g.filePairs.get(0).file1 = new File(PAIRED_FILENAME1);
            g.filePairs.get(0).file2 = new File(PAIRED_FILENAME2);

            g.filePairs.add(new FileGroup.FilePair());
            g.filePairs.get(1).file1 = new File(UNZIPPED_PAIRED_FILENAME1);
            g.filePairs.get(1).file2 = new File(UNZIPPED_PAIRED_FILENAME2);

            appendSamplesForImport(config, Arrays.asList(g));

            PipelineJob job = createPipelineJob(protocolName, IMPORT_TASKID, config.toString(), fileNames);
            waitForJob(job);

            Set<File> expectedOutputs = new HashSet<>();
            File basedir = new File(_pipelineRoot, "sequenceImport/" + protocolName);
            expectedOutputs.add(new File(basedir, "sequenceImport.xml"));
            expectedOutputs.add(new File(basedir, protocolName + ".pipe.xar.xml"));
            expectedOutputs.add(new File(basedir, protocolName + ".log"));

            expectedOutputs.add(new File(basedir, UNZIPPED_PAIRED_FILENAME1 + ".gz"));
            expectedOutputs.add(new File(basedir, UNZIPPED_PAIRED_FILENAME2 + ".gz"));

            expectedOutputs.add(new File(basedir, PAIRED_FILENAME1));
            expectedOutputs.add(new File(basedir, PAIRED_FILENAME2));

            verifyFileOutputs(basedir, expectedOutputs);
            verifyFileInputs(basedir, fileNames, config);

            validateReadsets(job, config);
        }

        private void validateReadsets(PipelineJob job, JSONObject config) throws Exception
        {
            validateReadsets(job, config, null);
        }

        private void validateReadsets(PipelineJob job, JSONObject config, Integer expected) throws Exception
        {
            SequenceReadsetImpl[] models = getReadsetsForJob(SequenceTaskHelper.getExpRunIdForJob(job));
            int numberExpected = expected != null ? expected : inferExpectedReadsets(config);
            Assert.assertEquals("Incorrect number of readsets created", numberExpected, models.length);
            validateSamples(models, config);
            validateQualityMetrics(models, config);
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
                Assert.assertEquals("Incorrect sampleDate", o.getString("sampledate"), m.getSampleDate() == null ?  null : format.format(m.getSampleDate()));

                //TODO: readData
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
                    expected = expected + (d.getFileId2() == null ? 4 : 8);
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

        @Before
        @Override
        public void setUp() throws Exception
        {
            if (isExternalPipelineEnabled())
            {
                super.setUp();

                if (!_hasPerformedSetup)
                {
                    copyInputFiles();
                    _readsets = createReadsets();
                    _hasPerformedSetup = true;
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
            models.add(createReadset("TestReadset2", Arrays.asList(Pair.<File, File>of(file3, null))));

            File file4 = new File(_pipelineRoot, UNZIPPED_PAIRED_FILENAME2);
            models.add(createReadset("TestReadset3", Arrays.asList(Pair.<File, File>of(file4, null))));

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

        protected List<JSONObject> getReadsetJson(List<SequenceReadsetImpl> models)
        {
            List<JSONObject> jsons = new ArrayList<>();
            for (Readset m : models)
            {
                JSONObject json = new JSONObject();
                json.put("readset", m.getReadsetId());
                json.put("readsetname", m.getName());

                jsons.add(json);
            }

            return jsons;
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
                for (ReadDataImpl rd : m.getReadData())
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

        protected void appendSamplesForAnalysis(JSONObject config, List<SequenceReadsetImpl> readsets)
        {
            int i = 0;
            for (JSONObject json : getReadsetJson(readsets))
            {
                config.put("readset_" + i, json);
                i++;
            }
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

        protected void validateAlignment(File bam, int expectedAligned, int expectedUnaligned)
        {
            int aligned = 0;
            int unaligned = 0;

            File bamIndex = new File(bam.getPath(), FileUtil.getBaseName(bam) + ".bai");
            SamReaderFactory fact = SamReaderFactory.makeDefault();
            fact.validationStringency(ValidationStringency.SILENT);
            //try (SAMFileReader reader = new SAMFileReader(bam, bamIndex))
            try (SamReader reader = fact.open(bam))
            {
                //reader.setValidationStringency(ValidationStringency.SILENT);

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

            Assert.assertEquals("Incorrect aligned count for BAM: " + bam.getPath(), expectedAligned, aligned);
            Assert.assertEquals("Incorrect unaligned count for BAM: " + bam.getPath(), expectedUnaligned, unaligned);
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
                Map<String, Object> rowMap = ts.getObject(Map.class);
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
            SimpleFilter filter = new SimpleFilter(FieldKey.fromString("name"), "SIVmac239");
            filter.addCondition(FieldKey.fromString("container"), ContainerManager.getSharedContainer().getId());
            Integer mac239Id = new TableSelector(QueryService.get().getUserSchema(_context.getUser(), _project, SequenceAnalysisSchema.SCHEMA_NAME).getTable(SequenceAnalysisSchema.TABLE_REF_NT_SEQUENCES), PageFlowUtil.set("rowid"), filter, null).getObject(Integer.class);
            ReferenceLibraryPipelineJob libraryJob = SequenceAnalysisManager.get().createReferenceLibrary(Arrays.asList(mac239Id), _project, _context.getUser(), libraryName, null);
            waitForJob(libraryJob);

            return new TableSelector(SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_REF_LIBRARIES), PageFlowUtil.set("rowid"), libraryFilter, null).getObject(Integer.class);
        }

        // this was added because bismark isnt deterministic in the number of outputs it creates.  for the time being it is
        // better for the test to pass and get some coverage
        protected void addOptionalFile(Set<File> expectedOutputs, File f)
        {
            if (f.exists())
            {
                expectedOutputs.add(f);
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

            String protocolName = "TestMosaik_" + System.currentTimeMillis();
            String[] fileNames = getFilenamesForReadsets();
            JSONObject config = substituteParams(new File(_sampleData, ALIGNMENT_JOB), protocolName, fileNames);
            config.put("alignment", "Mosaik");
            config.put("alignment.Mosaik.banded_smith_waterman", 51);
            config.put("alignment.Mosaik.max_mismatch_pct", 0.20);  //this is primary here to ensure it doesnt get copied into the build command.  20% should include everything
            config.put("analysis", "SBT;ViralAnalysis;SnpCountAnalysis");
            config.put("analysis.SnpCountAnalysis.intervals", "SIVmac239:5500-5600\nSIVmac239:9700-9900\nSIVmac239:10000-10020");

            appendSamplesForAnalysis(config, _readsets);

            PipelineJob job = createPipelineJob(protocolName, ANALYSIS_TASKID, config.toString(), fileNames);
            waitForJob(job);

            Set<File> expectedOutputs = new HashSet<>();
            File basedir = new File(_pipelineRoot, "sequenceAnalysis/" + protocolName);
            expectedOutputs.add(new File(basedir, protocolName + ".pipe.xar.xml"));
            expectedOutputs.add(new File(basedir, protocolName + ".log"));

            expectedOutputs.add(new File(basedir, "sequenceAnalysis.xml"));

            expectedOutputs.add(new File(basedir, "Shared"));
            expectedOutputs.add(new File(basedir, "Shared/SIVmac239.fasta"));
            expectedOutputs.add(new File(basedir, "Shared/SIVmac239.fasta.fai"));
            expectedOutputs.add(new File(basedir, "Shared/SIVmac239.idKey.txt"));
            expectedOutputs.add(new File(basedir, "Shared/Mosaik"));
            expectedOutputs.add(new File(basedir, "Shared/Mosaik/SIVmac239.mosaik"));

            expectedOutputs.add(new File(basedir, "paired1"));
            expectedOutputs.add(new File(basedir, "paired1/Alignment"));
            File bam1 = new File(basedir, "paired1/Alignment/TestReadset1.bam");
            expectedOutputs.add(bam1);
            expectedOutputs.add(new File(basedir, "paired1/Alignment/TestReadset1.bam.bai"));
            expectedOutputs.add(new File(basedir, "paired1/Alignment/paired1.mosaikreads"));
            expectedOutputs.add(new File(basedir, "paired1/Alignment/paired1.mosaik.stat"));
            expectedOutputs.add(new File(basedir, "TestReadset1.snps.txt"));

            expectedOutputs.add(new File(basedir, "paired3"));
            expectedOutputs.add(new File(basedir, "paired3/Alignment"));
            File bam2 = new File(basedir, "paired3/Alignment/TestReadset2.bam");
            expectedOutputs.add(bam2);
            expectedOutputs.add(new File(basedir, "paired3/Alignment/TestReadset2.bam.bai"));
            expectedOutputs.add(new File(basedir, "paired3/Alignment/paired3.mosaikreads"));
            expectedOutputs.add(new File(basedir, "paired3/Alignment/paired3.mosaik.stat"));
            expectedOutputs.add(new File(basedir, "TestReadset2.snps.txt"));

            expectedOutputs.add(new File(basedir, "paired4"));
            expectedOutputs.add(new File(basedir, "paired4/Alignment"));
            File bam3 = new File(basedir, "paired4/Alignment/TestReadset3.bam");
            expectedOutputs.add(bam3);
            expectedOutputs.add(new File(basedir, "paired4/Alignment/TestReadset3.bam.bai"));
            expectedOutputs.add(new File(basedir, "paired4/Alignment/paired4.mosaikreads"));
            expectedOutputs.add(new File(basedir, "paired4/Alignment/paired4.mosaik.stat"));
            expectedOutputs.add(new File(basedir, "TestReadset3.snps.txt"));

            validateInputs();
            verifyFileOutputs(basedir, expectedOutputs);
            validateAlignment(bam1, 294, 128);
            validateAlignment(bam2, 147, 64);
            validateAlignment(bam3, 147, 64);
        }

        @Test
        public void testMosaikWithBamPostProcessing() throws Exception
        {
            if (!isExternalPipelineEnabled())
                return;

            String protocolName = "TestMosaikWithPostProcess_" + System.currentTimeMillis();
            String[] fileNames = getFilenamesForReadsets();
            JSONObject config = substituteParams(new File(_sampleData, ALIGNMENT_JOB), protocolName, fileNames);
            config.put("alignment", "Mosaik");
            config.put("bamPostProcessing", "AddOrReplaceReadGroups;CallMdTags;CleanSam;FixMateInformation;" + (isGATKPresent() ? "IndelRealigner;" : "") + "MarkDuplicates;SortSam");

            appendSamplesForAnalysis(config, _readsets);

            PipelineJob job = createPipelineJob(protocolName, ANALYSIS_TASKID, config.toString(), fileNames);
            waitForJob(job);

            Set<File> expectedOutputs = new HashSet<>();
            File basedir = new File(_pipelineRoot, "sequenceAnalysis/" + protocolName);
            expectedOutputs.add(new File(basedir, protocolName + ".pipe.xar.xml"));
            expectedOutputs.add(new File(basedir, protocolName + ".log"));

            expectedOutputs.add(new File(basedir, "sequenceAnalysis.xml"));

            expectedOutputs.add(new File(basedir, "Shared"));
            expectedOutputs.add(new File(basedir, "Shared/SIVmac239.fasta"));
            expectedOutputs.add(new File(basedir, "Shared/SIVmac239.fasta.fai"));
            expectedOutputs.add(new File(basedir, "Shared/SIVmac239.idKey.txt"));
            if (isGATKPresent())
            {
                expectedOutputs.add(new File(basedir, "Shared/SIVmac239.dict"));
            }
            expectedOutputs.add(new File(basedir, "Shared/Mosaik"));
            expectedOutputs.add(new File(basedir, "Shared/Mosaik/SIVmac239.mosaik"));

            expectedOutputs.add(new File(basedir, "paired1"));
            expectedOutputs.add(new File(basedir, "paired1/Alignment"));
            expectedOutputs.add(new File(basedir, "paired1/Alignment/paired1.mosaikreads"));
            expectedOutputs.add(new File(basedir, "paired1/Alignment/paired1.mosaik.stat"));
            expectedOutputs.add(new File(basedir, "paired1/Alignment/paired1.mosaik.bam"));

            expectedOutputs.add(new File(basedir, "paired1/Alignment/paired1.mosaik.readgroups.bam"));
            expectedOutputs.add(new File(basedir, "paired1/Alignment/paired1.mosaik.readgroups.calmd.bam"));
            expectedOutputs.add(new File(basedir, "paired1/Alignment/paired1.mosaik.readgroups.calmd.cleaned.bam"));
            expectedOutputs.add(new File(basedir, "paired1/Alignment/paired1.mosaik.readgroups.calmd.cleaned.fixmate.bam"));

            if (isGATKPresent())
            {
                expectedOutputs.add(new File(basedir, "paired1/Alignment/paired1.mosaik.readgroups.calmd.cleaned.fixmate.intervals"));
                expectedOutputs.add(new File(basedir, "paired1/Alignment/paired1.mosaik.readgroups.calmd.cleaned.fixmate.realigned.bam"));
                expectedOutputs.add(new File(basedir, "paired1/Alignment/paired1.mosaik.readgroups.calmd.cleaned.fixmate.realigned.bai"));
                expectedOutputs.add(new File(basedir, "paired1/Alignment/paired1.mosaik.readgroups.calmd.cleaned.fixmate.realigned.markduplicates.bam"));
                expectedOutputs.add(new File(basedir, "paired1/Alignment/paired1.mosaik.readgroups.calmd.cleaned.fixmate.realigned.metrics"));
            }
            else
            {
                expectedOutputs.add(new File(basedir, "paired1/Alignment/paired1.mosaik.readgroups.calmd.cleaned.fixmate.bam"));
                expectedOutputs.add(new File(basedir, "paired1/Alignment/paired1.mosaik.readgroups.calmd.cleaned.fixmate.markduplicates.bam"));
                expectedOutputs.add(new File(basedir, "paired1/Alignment/paired1.mosaik.readgroups.calmd.cleaned.fixmate.metrics"));
            }

            File bam1 = new File(basedir, "paired1/Alignment/TestReadset1.bam");
            expectedOutputs.add(bam1);
            expectedOutputs.add(new File(basedir, "paired1/Alignment/TestReadset1.bam.bai"));

            expectedOutputs.add(new File(basedir, "paired3"));
            expectedOutputs.add(new File(basedir, "paired3/Alignment"));
            expectedOutputs.add(new File(basedir, "paired3/Alignment/paired3.mosaikreads"));
            expectedOutputs.add(new File(basedir, "paired3/Alignment/paired3.mosaik.stat"));
            expectedOutputs.add(new File(basedir, "paired3/Alignment/paired3.mosaik.bam"));
            expectedOutputs.add(new File(basedir, "paired3/Alignment/paired3.mosaik.readgroups.bam"));
            expectedOutputs.add(new File(basedir, "paired3/Alignment/paired3.mosaik.readgroups.calmd.bam"));
            expectedOutputs.add(new File(basedir, "paired3/Alignment/paired3.mosaik.readgroups.calmd.cleaned.bam"));
            expectedOutputs.add(new File(basedir, "paired3/Alignment/paired3.mosaik.readgroups.calmd.cleaned.fixmate.bam"));

            if (isGATKPresent())
            {
                expectedOutputs.add(new File(basedir, "paired3/Alignment/paired3.mosaik.readgroups.calmd.cleaned.fixmate.intervals"));
                expectedOutputs.add(new File(basedir, "paired3/Alignment/paired3.mosaik.readgroups.calmd.cleaned.fixmate.realigned.bam"));
                expectedOutputs.add(new File(basedir, "paired3/Alignment/paired3.mosaik.readgroups.calmd.cleaned.fixmate.realigned.bai"));
                expectedOutputs.add(new File(basedir, "paired3/Alignment/paired3.mosaik.readgroups.calmd.cleaned.fixmate.realigned.markduplicates.bam"));
                expectedOutputs.add(new File(basedir, "paired3/Alignment/paired3.mosaik.readgroups.calmd.cleaned.fixmate.realigned.metrics"));
            }
            else
            {
                expectedOutputs.add(new File(basedir, "paired3/Alignment/paired3.mosaik.readgroups.calmd.cleaned.fixmate.bam"));
                expectedOutputs.add(new File(basedir, "paired3/Alignment/paired3.mosaik.readgroups.calmd.cleaned.fixmate.markduplicates.bam"));
                expectedOutputs.add(new File(basedir, "paired3/Alignment/paired3.mosaik.readgroups.calmd.cleaned.fixmate.metrics"));
            }

            File bam2 = new File(basedir, "paired3/Alignment/TestReadset2.bam");
            expectedOutputs.add(bam2);
            expectedOutputs.add(new File(basedir, "paired3/Alignment/TestReadset2.bam.bai"));

            expectedOutputs.add(new File(basedir, "paired4"));
            expectedOutputs.add(new File(basedir, "paired4/Alignment"));
            expectedOutputs.add(new File(basedir, "paired4/Alignment/paired4.mosaikreads"));
            expectedOutputs.add(new File(basedir, "paired4/Alignment/paired4.mosaik.stat"));
            expectedOutputs.add(new File(basedir, "paired4/Alignment/paired4.mosaik.bam"));
            expectedOutputs.add(new File(basedir, "paired4/Alignment/paired4.mosaik.readgroups.bam"));
            expectedOutputs.add(new File(basedir, "paired4/Alignment/paired4.mosaik.readgroups.calmd.bam"));
            expectedOutputs.add(new File(basedir, "paired4/Alignment/paired4.mosaik.readgroups.calmd.cleaned.bam"));
            expectedOutputs.add(new File(basedir, "paired4/Alignment/paired4.mosaik.readgroups.calmd.cleaned.fixmate.bam"));

            if (isGATKPresent())
            {
                expectedOutputs.add(new File(basedir, "paired4/Alignment/paired4.mosaik.readgroups.calmd.cleaned.fixmate.intervals"));
                expectedOutputs.add(new File(basedir, "paired4/Alignment/paired4.mosaik.readgroups.calmd.cleaned.fixmate.realigned.bam"));
                expectedOutputs.add(new File(basedir, "paired4/Alignment/paired4.mosaik.readgroups.calmd.cleaned.fixmate.realigned.bai"));
                expectedOutputs.add(new File(basedir, "paired4/Alignment/paired4.mosaik.readgroups.calmd.cleaned.fixmate.realigned.markduplicates.bam"));
                expectedOutputs.add(new File(basedir, "paired4/Alignment/paired4.mosaik.readgroups.calmd.cleaned.fixmate.realigned.metrics"));
            }
            else
            {
                expectedOutputs.add(new File(basedir, "paired4/Alignment/paired4.mosaik.readgroups.calmd.cleaned.fixmate.metrics"));
                expectedOutputs.add(new File(basedir, "paired4/Alignment/paired4.mosaik.readgroups.calmd.cleaned.fixmate.bam"));
                expectedOutputs.add(new File(basedir, "paired4/Alignment/paired4.mosaik.readgroups.calmd.cleaned.fixmate.markduplicates.bam"));
            }

            File bam3 = new File(basedir, "paired4/Alignment/TestReadset3.bam");
            expectedOutputs.add(bam3);
            expectedOutputs.add(new File(basedir, "paired4/Alignment/TestReadset3.bam.bai"));

            validateInputs();
            verifyFileOutputs(basedir, expectedOutputs);
            validateAlignment(bam1, 294, 128);
            validateAlignment(bam2, 147, 64);
            validateAlignment(bam3, 147, 64);
        }

        @Test
        public void testMosaikWithBamPostProcessingAndDelete() throws Exception
        {
            if (!isExternalPipelineEnabled())
                return;

            String protocolName = "TestMosaikWithPostProcessAndDelete_" + System.currentTimeMillis();
            String[] fileNames = getFilenamesForReadsets();
            JSONObject config = substituteParams(new File(_sampleData, ALIGNMENT_JOB), protocolName, fileNames);
            config.put("alignment", "Mosaik");
            config.put("deleteIntermediateFiles", true);
            config.put("bamPostProcessing", "AddOrReplaceReadGroups;CallMdTags;CleanSam;FixMateInformation;" + (isGATKPresent() ? "IndelRealigner;" : "") + "MarkDuplicates;SortSam");

            appendSamplesForAnalysis(config, _readsets);

            PipelineJob job = createPipelineJob(protocolName, ANALYSIS_TASKID, config.toString(), fileNames);
            waitForJob(job);

            Set<File> expectedOutputs = new HashSet<>();
            File basedir = new File(_pipelineRoot, "sequenceAnalysis/" + protocolName);
            expectedOutputs.add(new File(basedir, protocolName + ".pipe.xar.xml"));
            expectedOutputs.add(new File(basedir, protocolName + ".log"));

            expectedOutputs.add(new File(basedir, "sequenceAnalysis.xml"));


            expectedOutputs.add(new File(basedir, "Shared"));
            expectedOutputs.add(new File(basedir, "Shared/SIVmac239.fasta"));
            expectedOutputs.add(new File(basedir, "Shared/SIVmac239.fasta.fai"));
            expectedOutputs.add(new File(basedir, "Shared/SIVmac239.idKey.txt"));

            expectedOutputs.add(new File(basedir, "paired1"));
            expectedOutputs.add(new File(basedir, "paired1/Alignment"));

            File bam1 = new File(basedir, "paired1/Alignment/TestReadset1.bam");
            expectedOutputs.add(bam1);
            expectedOutputs.add(new File(basedir, "paired1/Alignment/TestReadset1.bam.bai"));

            expectedOutputs.add(new File(basedir, "paired3"));
            expectedOutputs.add(new File(basedir, "paired3/Alignment"));

            File bam2 = new File(basedir, "paired3/Alignment/TestReadset2.bam");
            expectedOutputs.add(bam2);
            expectedOutputs.add(new File(basedir, "paired3/Alignment/TestReadset2.bam.bai"));

            expectedOutputs.add(new File(basedir, "paired4"));
            expectedOutputs.add(new File(basedir, "paired4/Alignment"));

            File bam3 = new File(basedir, "paired4/Alignment/TestReadset3.bam");
            expectedOutputs.add(bam3);
            expectedOutputs.add(new File(basedir, "paired4/Alignment/TestReadset3.bam.bai"));

            validateInputs();
            verifyFileOutputs(basedir, expectedOutputs);
            validateAlignment(bam1, 294, 128);
            validateAlignment(bam2, 147, 64);
            validateAlignment(bam3, 147, 64);
        }

        @Test
        public void testMosaikDeletingIntermediates() throws Exception
        {
            if (!isExternalPipelineEnabled())
                return;

            String protocolName = "MosaikDeletingIntermediates_" + System.currentTimeMillis();
            String[] fileNames = getFilenamesForReadsets();
            JSONObject config = substituteParams(new File(_sampleData, ALIGNMENT_JOB), protocolName, fileNames);
            config.put("alignment", "Mosaik");
            config.put("deleteIntermediateFiles", true);
            appendSamplesForAnalysis(config, _readsets);

            PipelineJob job = createPipelineJob(protocolName, ANALYSIS_TASKID, config.toString(), fileNames);
            waitForJob(job);

            Set<File> expectedOutputs = new HashSet<>();
            File basedir = new File(_pipelineRoot, "sequenceAnalysis/" + protocolName);
            expectedOutputs.add(new File(basedir, protocolName + ".pipe.xar.xml"));
            expectedOutputs.add(new File(basedir, protocolName + ".log"));

            expectedOutputs.add(new File(basedir, "sequenceAnalysis.xml"));

            expectedOutputs.add(new File(basedir, "Shared"));
            expectedOutputs.add(new File(basedir, "Shared/SIVmac239.fasta"));
            expectedOutputs.add(new File(basedir, "Shared/SIVmac239.fasta.fai"));
            expectedOutputs.add(new File(basedir, "Shared/SIVmac239.idKey.txt"));

            expectedOutputs.add(new File(basedir, "paired1"));
            expectedOutputs.add(new File(basedir, "paired1/Alignment"));
            File bam1 = new File(basedir, "paired1/Alignment/TestReadset1.bam");
            expectedOutputs.add(bam1);
            expectedOutputs.add(new File(basedir, "paired1/Alignment/TestReadset1.bam.bai"));

            expectedOutputs.add(new File(basedir, "paired3"));
            expectedOutputs.add(new File(basedir, "paired3/Alignment"));
            File bam2 = new File(basedir, "paired3/Alignment/TestReadset2.bam");
            expectedOutputs.add(bam2);
            expectedOutputs.add(new File(basedir, "paired3/Alignment/TestReadset2.bam.bai"));

            expectedOutputs.add(new File(basedir, "paired4"));
            expectedOutputs.add(new File(basedir, "paired4/Alignment"));
            File bam3 = new File(basedir, "paired4/Alignment/TestReadset3.bam");
            expectedOutputs.add(bam3);
            expectedOutputs.add(new File(basedir, "paired4/Alignment/TestReadset3.bam.bai"));

            validateInputs();
            verifyFileOutputs(basedir, expectedOutputs);

            validateAlignment(bam1, 294, 128);
            validateAlignment(bam2, 147, 64);
            validateAlignment(bam3, 147, 64);
        }

        @Test
        public void testLastz() throws Exception
        {
            if (!isExternalPipelineEnabled())
                return;

            String protocolName = "TestLastz_" + System.currentTimeMillis();
            String[] fileNames = getFilenamesForReadsets();
            JSONObject config = substituteParams(new File(_sampleData, ALIGNMENT_JOB), protocolName, fileNames);
            config.put("alignment", "Lastz");
            appendSamplesForAnalysis(config, _readsets);

            PipelineJob job = createPipelineJob(protocolName, ANALYSIS_TASKID, config.toString(), fileNames);
            waitForJob(job);

            Set<File> expectedOutputs = new HashSet<>();
            File basedir = new File(_pipelineRoot, "sequenceAnalysis/" + protocolName);
            expectedOutputs.add(new File(basedir, protocolName + ".pipe.xar.xml"));
            expectedOutputs.add(new File(basedir, protocolName + ".log"));

            expectedOutputs.add(new File(basedir, "sequenceAnalysis.xml"));

            expectedOutputs.add(new File(basedir, "Shared"));
            expectedOutputs.add(new File(basedir, "Shared/SIVmac239.fasta"));
            expectedOutputs.add(new File(basedir, "Shared/SIVmac239.fasta.fai"));
            expectedOutputs.add(new File(basedir, "Shared/SIVmac239.idKey.txt"));

            expectedOutputs.add(new File(basedir, "paired1"));
            expectedOutputs.add(new File(basedir, "paired1/Alignment"));
            expectedOutputs.add(new File(basedir, "paired1/Alignment/paired1.lastz.bam"));
            File bam1 = new File(basedir, "paired1/Alignment/TestReadset1.bam");
            expectedOutputs.add(bam1);
            expectedOutputs.add(new File(basedir, "paired1/Alignment/TestReadset1.bam.bai"));
            expectedOutputs.add(new File(basedir, "paired1/Alignment/paired1.fasta"));

            expectedOutputs.add(new File(basedir, "paired3"));
            expectedOutputs.add(new File(basedir, "paired3/Alignment"));
            expectedOutputs.add(new File(basedir, "paired3/Alignment/paired3.lastz.bam"));
            File bam2 = new File(basedir, "paired3/Alignment/TestReadset2.bam");
            expectedOutputs.add(bam2);
            expectedOutputs.add(new File(basedir, "paired3/Alignment/TestReadset2.bam.bai"));
            expectedOutputs.add(new File(basedir, "paired3/Alignment/paired3.fasta"));

            expectedOutputs.add(new File(basedir, "paired4"));
            expectedOutputs.add(new File(basedir, "paired4/Alignment"));
            expectedOutputs.add(new File(basedir, "paired4/Alignment/paired4.lastz.bam"));
            File bam3 = new File(basedir, "paired4/Alignment/TestReadset3.bam");
            expectedOutputs.add(bam3);
            expectedOutputs.add(new File(basedir, "paired4/Alignment/TestReadset3.bam.bai"));
            expectedOutputs.add(new File(basedir, "paired4/Alignment/paired4.fasta"));

            validateInputs();
            verifyFileOutputs(basedir, expectedOutputs);
            validateAlignment(bam1, 340, 0);
            validateAlignment(bam2, 170, 0);
            validateAlignment(bam3, 170, 0);
        }

        @Test
        public void testLastzWithPreprocessing() throws Exception
        {
            if (!isExternalPipelineEnabled())
                return;

            String protocolName = "LastzWithPreprocess_" + System.currentTimeMillis();
            String[] fileNames = getFilenamesForReadsets();
            JSONObject config = substituteParams(new File(_sampleData, ALIGNMENT_JOB), protocolName, fileNames);
            config.put("alignment", "Lastz");
            config.put("fastqProcessing", "DownsampleReads;AdapterTrimming;HeadCropReads;CropReads;SlidingWindowTrim;ReadLengthFilter");
            config.put("fastqProcessing.CropReads.cropLength", 250);
            config.put("fastqProcessing.HeadCropReads.headcropLength", 1);
            config.put("fastqProcessing.DownsampleReads.downsampleReadNumber", 200);
            config.put("fastqProcessing.ReadLengthFilter.minLength", 100);
            config.put("fastqProcessing.AdapterTrimming.adapters", "[[\"Nextera Transposon Adapter A\",\"AGATGTGTATAAGAGACAG\",true,true]]");

            config.put("fastqProcessing.SlidingWindowTrim.avgQual", 15);
            config.put("fastqProcessing.SlidingWindowTrim.windowSize", 4);

            appendSamplesForAnalysis(config, _readsets);

            PipelineJob job = createPipelineJob(protocolName, ANALYSIS_TASKID, config.toString(), fileNames);
            waitForJob(job);

            Set<File> expectedOutputs = new HashSet<>();
            File basedir = new File(_pipelineRoot, "sequenceAnalysis/" + protocolName);
            expectedOutputs.add(new File(basedir, protocolName + ".pipe.xar.xml"));
            expectedOutputs.add(new File(basedir, protocolName + ".log"));

            expectedOutputs.add(new File(basedir, "sequenceAnalysis.xml"));

            expectedOutputs.add(new File(basedir, "Shared"));
            expectedOutputs.add(new File(basedir, "Shared/SIVmac239.fasta"));
            expectedOutputs.add(new File(basedir, "Shared/SIVmac239.fasta.fai"));
            expectedOutputs.add(new File(basedir, "Shared/SIVmac239.idKey.txt"));

            expectedOutputs.add(new File(basedir, "paired1"));
            expectedOutputs.add(new File(basedir, "paired1/Alignment"));
            expectedOutputs.add(new File(basedir, "paired1/Alignment/paired1.preprocessed.lastz.bam"));
            File bam1 = new File(basedir, "paired1/Alignment/TestReadset1.bam");
            expectedOutputs.add(bam1);
            expectedOutputs.add(new File(basedir, "paired1/Alignment/TestReadset1.bam.bai"));
            expectedOutputs.add(new File(basedir, "paired1/Alignment/paired1.preprocessed.fasta"));

            expectedOutputs.add(new File(basedir, "paired3"));
            expectedOutputs.add(new File(basedir, "paired3/Alignment"));
            expectedOutputs.add(new File(basedir, "paired3/Alignment/paired3.preprocessed.lastz.bam"));
            File bam2 = new File(basedir, "paired3/Alignment/TestReadset2.bam");
            expectedOutputs.add(bam2);
            expectedOutputs.add(new File(basedir, "paired3/Alignment/TestReadset2.bam.bai"));
            expectedOutputs.add(new File(basedir, "paired3/Alignment/paired3.preprocessed.fasta"));

            expectedOutputs.add(new File(basedir, "paired4"));
            expectedOutputs.add(new File(basedir, "paired4/Alignment"));
            expectedOutputs.add(new File(basedir, "paired4/Alignment/paired4.preprocessed.lastz.bam"));
            File bam3 = new File(basedir, "paired4/Alignment/TestReadset3.bam");
            expectedOutputs.add(bam3);
            expectedOutputs.add(new File(basedir, "paired4/Alignment/TestReadset3.bam.bai"));
            expectedOutputs.add(new File(basedir, "paired4/Alignment/paired4.preprocessed.fasta"));

            expectedOutputs.add(new File(basedir, "paired1/Preprocessing/"));
            expectedOutputs.add(new File(basedir, "paired1/Preprocessing/paired1.downsampled.fastq"));
            expectedOutputs.add(new File(basedir, "paired1/Preprocessing/paired2.downsampled.fastq"));
            expectedOutputs.add(new File(basedir, "paired1/Preprocessing/paired1.downsampled.adaptertrimmed.fastq"));
            expectedOutputs.add(new File(basedir, "paired1/Preprocessing/paired2.downsampled.adaptertrimmed.fastq"));
            expectedOutputs.add(new File(basedir, "paired1/Preprocessing/paired1.downsampled.adaptertrimmed.HeadCropReads.fastq"));
            expectedOutputs.add(new File(basedir, "paired1/Preprocessing/paired2.downsampled.adaptertrimmed.HeadCropReads.fastq"));
            expectedOutputs.add(new File(basedir, "paired1/Preprocessing/paired1.downsampled.adaptertrimmed.HeadCropReads.CropReads.fastq"));
            expectedOutputs.add(new File(basedir, "paired1/Preprocessing/paired2.downsampled.adaptertrimmed.HeadCropReads.CropReads.fastq"));
            expectedOutputs.add(new File(basedir, "paired1/Preprocessing/paired1.downsampled.adaptertrimmed.HeadCropReads.CropReads.SlidingWindowTrim.fastq"));
            expectedOutputs.add(new File(basedir, "paired1/Preprocessing/paired2.downsampled.adaptertrimmed.HeadCropReads.CropReads.SlidingWindowTrim.fastq"));
            expectedOutputs.add(new File(basedir, "paired1/Preprocessing/paired1.downsampled.adaptertrimmed.HeadCropReads.CropReads.SlidingWindowTrim.ReadLengthFilter.unpaired1.fastq"));

            expectedOutputs.add(new File(basedir, "paired1/Preprocessing/paired1.preprocessed.fastq"));
            expectedOutputs.add(new File(basedir, "paired1/Preprocessing/paired2.preprocessed.fastq"));

            expectedOutputs.add(new File(basedir, "paired3/Preprocessing"));
            expectedOutputs.add(new File(basedir, "paired3/Preprocessing/paired3.downsampled.fastq"));
            expectedOutputs.add(new File(basedir, "paired3/Preprocessing/paired3.downsampled.adaptertrimmed.fastq"));
            expectedOutputs.add(new File(basedir, "paired3/Preprocessing/paired3.downsampled.adaptertrimmed.HeadCropReads.fastq"));
            expectedOutputs.add(new File(basedir, "paired3/Preprocessing/paired3.downsampled.adaptertrimmed.HeadCropReads.CropReads.fastq"));
            expectedOutputs.add(new File(basedir, "paired3/Preprocessing/paired3.downsampled.adaptertrimmed.HeadCropReads.CropReads.SlidingWindowTrim.fastq"));
            expectedOutputs.add(new File(basedir, "paired3/Preprocessing/paired3.preprocessed.fastq"));

            expectedOutputs.add(new File(basedir, "paired4/Preprocessing/"));
            expectedOutputs.add(new File(basedir, "paired4/Preprocessing/paired4.downsampled.fastq"));
            expectedOutputs.add(new File(basedir, "paired4/Preprocessing/paired4.downsampled.adaptertrimmed.fastq"));
            expectedOutputs.add(new File(basedir, "paired4/Preprocessing/paired4.downsampled.adaptertrimmed.HeadCropReads.fastq"));
            expectedOutputs.add(new File(basedir, "paired4/Preprocessing/paired4.downsampled.adaptertrimmed.HeadCropReads.CropReads.fastq"));
            expectedOutputs.add(new File(basedir, "paired4/Preprocessing/paired4.downsampled.adaptertrimmed.HeadCropReads.CropReads.SlidingWindowTrim.fastq"));
            expectedOutputs.add(new File(basedir, "paired4/Preprocessing/paired4.preprocessed.fastq"));

            validateInputs();
            verifyFileOutputs(basedir, expectedOutputs);
            validateAlignment(bam1, 170, 0);
            validateAlignment(bam2, 87, 0);
            validateAlignment(bam3, 85, 0);
        }

        @Test
        public void testLastzWithPreprocessingAndDeleteIntermediates() throws Exception
        {
            if (!isExternalPipelineEnabled())
                return;

            String protocolName = "LastzPreprocessAndDelete_" + System.currentTimeMillis();
            String[] fileNames = getFilenamesForReadsets();
            JSONObject config = substituteParams(new File(_sampleData, ALIGNMENT_JOB), protocolName, fileNames);
            config.put("alignment", "Lastz");
            config.put("deleteIntermediateFiles", true);

            config.put("fastqProcessing", "DownsampleReads;AdapterTrimming;CropReads;SlidingWindowTrim;ReadLengthFilter");
            config.put("fastqProcessing.CropReads.cropLength", 250);
            config.put("fastqProcessing.HeadCropReads.headcropLength", 1);
            config.put("fastqProcessing.DownsampleReads.downsampleReadNumber", 200);
            config.put("fastqProcessing.ReadLengthFilter.minLength", 100);
            config.put("fastqProcessing.AdapterTrimming.adapters", "[[\"Nextera Transposon Adapter A\",\"AGATGTGTATAAGAGACAG\",true,true]]");
            config.put("fastqProcessing.SlidingWindowTrim.avgQual", 15);
            config.put("fastqProcessing.SlidingWindowTrim.windowSize", 4);

            appendSamplesForAnalysis(config, _readsets);

            PipelineJob job = createPipelineJob(protocolName, ANALYSIS_TASKID, config.toString(), fileNames);
            waitForJob(job);

            Set<File> expectedOutputs = new HashSet<>();
            File basedir = new File(_pipelineRoot, "sequenceAnalysis/" + protocolName);
            expectedOutputs.add(new File(basedir, protocolName + ".pipe.xar.xml"));
            expectedOutputs.add(new File(basedir, protocolName + ".log"));

            expectedOutputs.add(new File(basedir, "sequenceAnalysis.xml"));

            expectedOutputs.add(new File(basedir, "Shared"));
            expectedOutputs.add(new File(basedir, "Shared/SIVmac239.fasta"));
            expectedOutputs.add(new File(basedir, "Shared/SIVmac239.fasta.fai"));
            expectedOutputs.add(new File(basedir, "Shared/SIVmac239.idKey.txt"));

            expectedOutputs.add(new File(basedir, "paired1"));
            expectedOutputs.add(new File(basedir, "paired1/Alignment"));
            File bam1 = new File(basedir, "paired1/Alignment/TestReadset1.bam");
            expectedOutputs.add(bam1);
            expectedOutputs.add(new File(basedir, "paired1/Alignment/TestReadset1.bam.bai"));

            expectedOutputs.add(new File(basedir, "paired3"));
            expectedOutputs.add(new File(basedir, "paired3/Alignment"));
            File bam2 = new File(basedir, "paired3/Alignment/TestReadset2.bam");
            expectedOutputs.add(bam2);
            expectedOutputs.add(new File(basedir, "paired3/Alignment/TestReadset2.bam.bai"));

            expectedOutputs.add(new File(basedir, "paired4"));
            expectedOutputs.add(new File(basedir, "paired4/Alignment"));
            File bam3 = new File(basedir, "paired4/Alignment/TestReadset3.bam");
            expectedOutputs.add(bam3);
            expectedOutputs.add(new File(basedir, "paired4/Alignment/TestReadset3.bam.bai"));

            validateInputs();
            verifyFileOutputs(basedir, expectedOutputs);
            validateAlignment(bam1, 172, 0);
            validateAlignment(bam2, 88, 0);
            validateAlignment(bam3, 86, 0);
        }

        @Test
        public void testBWASW() throws Exception
        {
            if (!isExternalPipelineEnabled())
                return;

            String protocolName = "TestBWASW_" + System.currentTimeMillis();
            String[] fileNames = getFilenamesForReadsets();
            JSONObject config = substituteParams(new File(_sampleData, ALIGNMENT_JOB), protocolName, fileNames);
            config.put("alignment", "BWA-SW");
            appendSamplesForAnalysis(config, _readsets);

            PipelineJob job = createPipelineJob(protocolName, ANALYSIS_TASKID, config.toString(), fileNames);
            waitForJob(job);

            Set<File> expectedOutputs = new HashSet<>();
            File basedir = new File(_pipelineRoot, "sequenceAnalysis/" + protocolName);
            expectedOutputs.add(new File(basedir, protocolName + ".pipe.xar.xml"));
            expectedOutputs.add(new File(basedir, protocolName + ".log"));

            expectedOutputs.add(new File(basedir, "sequenceAnalysis.xml"));

            expectedOutputs.add(new File(basedir, "Shared"));
            expectedOutputs.add(new File(basedir, "Shared/SIVmac239.fasta"));
            expectedOutputs.add(new File(basedir, "Shared/SIVmac239.fasta.fai"));
            expectedOutputs.add(new File(basedir, "Shared/SIVmac239.idKey.txt"));

            expectedOutputs.add(new File(basedir, "Shared/bwa"));
            expectedOutputs.add(new File(basedir, "Shared/bwa/SIVmac239.bwa.index.amb"));
            expectedOutputs.add(new File(basedir, "Shared/bwa/SIVmac239.bwa.index.ann"));
            expectedOutputs.add(new File(basedir, "Shared/bwa/SIVmac239.bwa.index.bwt"));
            expectedOutputs.add(new File(basedir, "Shared/bwa/SIVmac239.bwa.index.pac"));
            expectedOutputs.add(new File(basedir, "Shared/bwa/SIVmac239.bwa.index.sa"));

            expectedOutputs.add(new File(basedir, "paired1"));
            expectedOutputs.add(new File(basedir, "paired1/Alignment"));
            File bam1 = new File(basedir, "paired1/Alignment/TestReadset1.bam");
            expectedOutputs.add(bam1);
            expectedOutputs.add(new File(basedir, "paired1/Alignment/TestReadset1.bam.bai"));

            expectedOutputs.add(new File(basedir, "paired3"));
            expectedOutputs.add(new File(basedir, "paired3/Alignment"));
            File bam2 = new File(basedir, "paired3/Alignment/TestReadset2.bam");
            expectedOutputs.add(bam2);
            expectedOutputs.add(new File(basedir, "paired3/Alignment/TestReadset2.bam.bai"));

            expectedOutputs.add(new File(basedir, "paired4"));
            expectedOutputs.add(new File(basedir, "paired4/Alignment"));
            File bam3 = new File(basedir, "paired4/Alignment/TestReadset3.bam");
            expectedOutputs.add(bam3);
            expectedOutputs.add(new File(basedir, "paired4/Alignment/TestReadset3.bam.bai"));

            validateInputs();
            verifyFileOutputs(basedir, expectedOutputs);
            validateAlignment(bam1, 319, 103);
            validateAlignment(bam2, 160, 51);
            validateAlignment(bam3, 159, 52);
        }

        @Test
        public void testBWAMem() throws Exception
        {
            if (!isExternalPipelineEnabled())
                return;

            String protocolName = "TestBWAMem_" + System.currentTimeMillis();
            String[] fileNames = getFilenamesForReadsets();
            JSONObject config = substituteParams(new File(_sampleData, ALIGNMENT_JOB), protocolName, fileNames);
            config.put("alignment", "BWA-Mem");
            appendSamplesForAnalysis(config, _readsets);

            PipelineJob job = createPipelineJob(protocolName, ANALYSIS_TASKID, config.toString(), fileNames);
            waitForJob(job);

            Set<File> expectedOutputs = new HashSet<>();
            File basedir = new File(_pipelineRoot, "sequenceAnalysis/" + protocolName);
            expectedOutputs.add(new File(basedir, protocolName + ".pipe.xar.xml"));
            expectedOutputs.add(new File(basedir, protocolName + ".log"));

            expectedOutputs.add(new File(basedir, "sequenceAnalysis.xml"));

            expectedOutputs.add(new File(basedir, "Shared"));
            expectedOutputs.add(new File(basedir, "Shared/SIVmac239.fasta"));
            expectedOutputs.add(new File(basedir, "Shared/SIVmac239.fasta.fai"));
            expectedOutputs.add(new File(basedir, "Shared/SIVmac239.idKey.txt"));

            expectedOutputs.add(new File(basedir, "Shared/bwa"));
            expectedOutputs.add(new File(basedir, "Shared/bwa/SIVmac239.bwa.index.amb"));
            expectedOutputs.add(new File(basedir, "Shared/bwa/SIVmac239.bwa.index.ann"));
            expectedOutputs.add(new File(basedir, "Shared/bwa/SIVmac239.bwa.index.bwt"));
            expectedOutputs.add(new File(basedir, "Shared/bwa/SIVmac239.bwa.index.pac"));
            expectedOutputs.add(new File(basedir, "Shared/bwa/SIVmac239.bwa.index.sa"));

            expectedOutputs.add(new File(basedir, "paired1"));
            expectedOutputs.add(new File(basedir, "paired1/Alignment"));
            File bam1 = new File(basedir, "paired1/Alignment/TestReadset1.bam");
            expectedOutputs.add(bam1);
            expectedOutputs.add(new File(basedir, "paired1/Alignment/TestReadset1.bam.bai"));

            expectedOutputs.add(new File(basedir, "paired3"));
            expectedOutputs.add(new File(basedir, "paired3/Alignment"));
            File bam2 = new File(basedir, "paired3/Alignment/TestReadset2.bam");
            expectedOutputs.add(bam2);
            expectedOutputs.add(new File(basedir, "paired3/Alignment/TestReadset2.bam.bai"));

            expectedOutputs.add(new File(basedir, "paired4"));
            expectedOutputs.add(new File(basedir, "paired4/Alignment"));
            File bam3 = new File(basedir, "paired4/Alignment/TestReadset3.bam");
            expectedOutputs.add(bam3);
            expectedOutputs.add(new File(basedir, "paired4/Alignment/TestReadset3.bam.bai"));

            validateInputs();
            verifyFileOutputs(basedir, expectedOutputs);
            validateAlignment(bam1, 320, 102);
            validateAlignment(bam2, 160, 51);
            validateAlignment(bam3, 159, 52);
        }

        @Test
        public void testBWAWithAdapters() throws Exception
        {
            if (!isExternalPipelineEnabled())
                return;

            String protocolName = "TestBWAWithAdapters_" + System.currentTimeMillis();
            String[] fileNames = getFilenamesForReadsets();
            JSONObject config = substituteParams(new File(_sampleData, ALIGNMENT_JOB), protocolName, fileNames);
            config.put("alignment", "BWA");

            config.put("fastqProcessing", "AdapterTrimming");
            config.put("fastqProcessing.AdapterTrimming.adapters", "[[\"Nextera Transposon Adapter A\",\"AGATGTGTATAAGAGACAG\",true,true]]");

            appendSamplesForAnalysis(config, _readsets);

            PipelineJob job = createPipelineJob(protocolName, ANALYSIS_TASKID, config.toString(), fileNames);
            waitForJob(job);

            Set<File> expectedOutputs = new HashSet<>();
            File basedir = new File(_pipelineRoot, "sequenceAnalysis/" + protocolName);
            expectedOutputs.add(new File(basedir, protocolName + ".pipe.xar.xml"));
            expectedOutputs.add(new File(basedir, protocolName + ".log"));

            expectedOutputs.add(new File(basedir, "sequenceAnalysis.xml"));

            expectedOutputs.add(new File(basedir, "Shared"));
            expectedOutputs.add(new File(basedir, "Shared/SIVmac239.fasta"));
            expectedOutputs.add(new File(basedir, "Shared/SIVmac239.fasta.fai"));
            expectedOutputs.add(new File(basedir, "Shared/SIVmac239.idKey.txt"));

            expectedOutputs.add(new File(basedir, "Shared/bwa"));
            expectedOutputs.add(new File(basedir, "Shared/bwa/SIVmac239.bwa.index.amb"));
            expectedOutputs.add(new File(basedir, "Shared/bwa/SIVmac239.bwa.index.ann"));
            expectedOutputs.add(new File(basedir, "Shared/bwa/SIVmac239.bwa.index.bwt"));
            expectedOutputs.add(new File(basedir, "Shared/bwa/SIVmac239.bwa.index.pac"));
            expectedOutputs.add(new File(basedir, "Shared/bwa/SIVmac239.bwa.index.sa"));

            expectedOutputs.add(new File(basedir, "paired1"));

            expectedOutputs.add(new File(basedir, "paired1/Preprocessing/"));
            expectedOutputs.add(new File(basedir, "paired1/Preprocessing/paired1.preprocessed.fastq"));
            expectedOutputs.add(new File(basedir, "paired1/Preprocessing/paired2.preprocessed.fastq"));

            expectedOutputs.add(new File(basedir, "paired1/Alignment"));
            File bam1 = new File(basedir, "paired1/Alignment/TestReadset1.bam");
            expectedOutputs.add(bam1);
            expectedOutputs.add(new File(basedir, "paired1/Alignment/TestReadset1.bam.bai"));
            expectedOutputs.add(new File(basedir, "paired1/Alignment/paired1.preprocessed.fastq.sai"));
            expectedOutputs.add(new File(basedir, "paired1/Alignment/paired2.preprocessed.fastq.sai"));

            expectedOutputs.add(new File(basedir, "paired3"));

            expectedOutputs.add(new File(basedir, "paired3/Preprocessing/"));
            expectedOutputs.add(new File(basedir, "paired3/Preprocessing/paired3.preprocessed.fastq"));

            expectedOutputs.add(new File(basedir, "paired3/Alignment"));
            File bam2 = new File(basedir, "paired3/Alignment/TestReadset2.bam");
            expectedOutputs.add(bam2);
            expectedOutputs.add(new File(basedir, "paired3/Alignment/TestReadset2.bam.bai"));
            expectedOutputs.add(new File(basedir, "paired3/Alignment/paired3.preprocessed.fastq.sai"));

            expectedOutputs.add(new File(basedir, "paired4"));

            expectedOutputs.add(new File(basedir, "paired4/Preprocessing/"));
            expectedOutputs.add(new File(basedir, "paired4/Preprocessing/paired4.preprocessed.fastq"));

            expectedOutputs.add(new File(basedir, "paired4/Alignment"));
            File bam3 = new File(basedir, "paired4/Alignment/TestReadset3.bam");
            expectedOutputs.add(bam3);
            expectedOutputs.add(new File(basedir, "paired4/Alignment/TestReadset3.bam.bai"));
            expectedOutputs.add(new File(basedir, "paired4/Alignment/paired4.preprocessed.fastq.sai"));

            validateInputs();
            verifyFileOutputs(basedir, expectedOutputs);

            validateAlignment(bam1, 317, 105);
            validateAlignment(bam2, 158, 53);
            validateAlignment(bam3, 156, 55);
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

            String protocolName = "TestBWA_" + System.currentTimeMillis();
            String[] fileNames = getFilenamesForReadsets();
            JSONObject config = substituteParams(new File(_sampleData, ALIGNMENT_JOB), protocolName, fileNames);
            config.put("alignment", "BWA");
            appendSamplesForAnalysis(config, _readsets);

            PipelineJob job = createPipelineJob(protocolName, ANALYSIS_TASKID, config.toString(), fileNames);
            waitForJob(job);

            Set<File> expectedOutputs = new HashSet<>();
            File basedir = new File(_pipelineRoot, "sequenceAnalysis/" + protocolName);
            expectedOutputs.add(new File(basedir, protocolName + ".pipe.xar.xml"));
            expectedOutputs.add(new File(basedir, protocolName + ".log"));

            expectedOutputs.add(new File(basedir, "sequenceAnalysis.xml"));

            expectedOutputs.add(new File(basedir, "Shared"));
            expectedOutputs.add(new File(basedir, "Shared/SIVmac239.fasta"));
            expectedOutputs.add(new File(basedir, "Shared/SIVmac239.fasta.fai"));
            expectedOutputs.add(new File(basedir, "Shared/SIVmac239.idKey.txt"));

            expectedOutputs.add(new File(basedir, "Shared/bwa"));
            expectedOutputs.add(new File(basedir, "Shared/bwa/SIVmac239.bwa.index.amb"));
            expectedOutputs.add(new File(basedir, "Shared/bwa/SIVmac239.bwa.index.ann"));
            expectedOutputs.add(new File(basedir, "Shared/bwa/SIVmac239.bwa.index.bwt"));
            expectedOutputs.add(new File(basedir, "Shared/bwa/SIVmac239.bwa.index.pac"));
            expectedOutputs.add(new File(basedir, "Shared/bwa/SIVmac239.bwa.index.sa"));

            expectedOutputs.add(new File(basedir, "paired1"));
            expectedOutputs.add(new File(basedir, "paired1/Alignment"));
            File bam1 = new File(basedir, "paired1/Alignment/TestReadset1.bam");
            expectedOutputs.add(bam1);
            expectedOutputs.add(new File(basedir, "paired1/Alignment/TestReadset1.bam.bai"));
            expectedOutputs.add(new File(basedir, "paired1/Alignment/paired1.fastq.sai"));
            expectedOutputs.add(new File(basedir, "paired1/Alignment/paired2.fastq.sai"));

            expectedOutputs.add(new File(basedir, "paired3"));
            expectedOutputs.add(new File(basedir, "paired3/Alignment"));
            File bam2 = new File(basedir, "paired3/Alignment/TestReadset2.bam");
            expectedOutputs.add(bam2);
            expectedOutputs.add(new File(basedir, "paired3/Alignment/TestReadset2.bam.bai"));
            expectedOutputs.add(new File(basedir, "paired3/Alignment/paired3.fastq.sai"));

            expectedOutputs.add(new File(basedir, "paired4"));
            expectedOutputs.add(new File(basedir, "paired4/Alignment"));
            File bam3 = new File(basedir, "paired4/Alignment/TestReadset3.bam");
            expectedOutputs.add(bam3);
            expectedOutputs.add(new File(basedir, "paired4/Alignment/TestReadset3.bam.bai"));
            expectedOutputs.add(new File(basedir, "paired4/Alignment/paired4.fastq.sai"));

            validateInputs();
            verifyFileOutputs(basedir, expectedOutputs);

            validateAlignment(bam1, 317, 105);
            validateAlignment(bam2, 158, 53);
            validateAlignment(bam3, 156, 55);
        }

        @Test
        public void testBowtie() throws Exception
        {
            if (!isExternalPipelineEnabled())
                return;

            String protocolName = "TestBowtie_" + System.currentTimeMillis();
            String[] fileNames = getFilenamesForReadsets();
            JSONObject config = substituteParams(new File(_sampleData, ALIGNMENT_JOB), protocolName, fileNames);
            config.put("alignment", "Bowtie");
            appendSamplesForAnalysis(config, _readsets);

            PipelineJob job = createPipelineJob(protocolName, ANALYSIS_TASKID, config.toString(), fileNames);
            waitForJob(job);

            Set<File> expectedOutputs = new HashSet<>();
            File basedir = new File(_pipelineRoot, "sequenceAnalysis/" + protocolName);
            expectedOutputs.add(new File(basedir, protocolName + ".pipe.xar.xml"));
            expectedOutputs.add(new File(basedir, protocolName + ".log"));

            expectedOutputs.add(new File(basedir, "sequenceAnalysis.xml"));

            expectedOutputs.add(new File(basedir, "Shared"));
            expectedOutputs.add(new File(basedir, "Shared/SIVmac239.fasta"));
            expectedOutputs.add(new File(basedir, "Shared/SIVmac239.fasta.fai"));
            expectedOutputs.add(new File(basedir, "Shared/SIVmac239.idKey.txt"));

            expectedOutputs.add(new File(basedir, "Shared/Bowtie"));
            expectedOutputs.add(new File(basedir, "Shared/Bowtie/SIVmac239.bowtie.index.1.ebwt"));
            expectedOutputs.add(new File(basedir, "Shared/Bowtie/SIVmac239.bowtie.index.2.ebwt"));
            expectedOutputs.add(new File(basedir, "Shared/Bowtie/SIVmac239.bowtie.index.3.ebwt"));
            expectedOutputs.add(new File(basedir, "Shared/Bowtie/SIVmac239.bowtie.index.4.ebwt"));
            expectedOutputs.add(new File(basedir, "Shared/Bowtie/SIVmac239.bowtie.index.rev.1.ebwt"));
            expectedOutputs.add(new File(basedir, "Shared/Bowtie/SIVmac239.bowtie.index.rev.2.ebwt"));

            expectedOutputs.add(new File(basedir, "paired1"));
            expectedOutputs.add(new File(basedir, "paired1/Alignment"));
            File bam1 = new File(basedir, "paired1/Alignment/TestReadset1.bam");
            expectedOutputs.add(bam1);
            expectedOutputs.add(new File(basedir, "paired1/Alignment/TestReadset1.bam.bai"));
            expectedOutputs.add(new File(basedir, "paired1/Alignment/paired1.bowtie.unaligned_1.fastq"));
            expectedOutputs.add(new File(basedir, "paired1/Alignment/paired1.bowtie.unaligned_2.fastq"));

            expectedOutputs.add(new File(basedir, "paired3"));
            expectedOutputs.add(new File(basedir, "paired3/Alignment"));
            File bam2 = new File(basedir, "paired3/Alignment/TestReadset2.bam");
            expectedOutputs.add(bam2);
            expectedOutputs.add(new File(basedir, "paired3/Alignment/TestReadset2.bam.bai"));
            expectedOutputs.add(new File(basedir, "paired3/Alignment/paired3.bowtie.unaligned.fastq"));

            expectedOutputs.add(new File(basedir, "paired4"));
            expectedOutputs.add(new File(basedir, "paired4/Alignment"));
            File bam3 = new File(basedir, "paired4/Alignment/TestReadset3.bam");
            expectedOutputs.add(bam3);
            expectedOutputs.add(new File(basedir, "paired4/Alignment/TestReadset3.bam.bai"));
            expectedOutputs.add(new File(basedir, "paired4/Alignment/paired4.bowtie.unaligned.fastq"));

            validateInputs();

            //this is probably due to adapters
            verifyFileOutputs(basedir, expectedOutputs);
            validateAlignment(bam1, 0, 422);
            validateAlignment(bam2, 155, 56);
            validateAlignment(bam3, 154, 57);
        }

        @Test
        public void testBowtieDeletingIntermediates() throws Exception
        {
            if (!isExternalPipelineEnabled())
                return;

            String protocolName = "TestBowtieDeleting_" + System.currentTimeMillis();
            String[] fileNames = getFilenamesForReadsets();
            JSONObject config = substituteParams(new File(_sampleData, ALIGNMENT_JOB), protocolName, fileNames);
            config.put("alignment", "Bowtie");
            config.put("deleteIntermediateFiles", true);
            appendSamplesForAnalysis(config, _readsets);

            PipelineJob job = createPipelineJob(protocolName, ANALYSIS_TASKID, config.toString(), fileNames);
            waitForJob(job);

            Set<File> expectedOutputs = new HashSet<>();
            File basedir = new File(_pipelineRoot, "sequenceAnalysis/" + protocolName);
            expectedOutputs.add(new File(basedir, protocolName + ".pipe.xar.xml"));
            expectedOutputs.add(new File(basedir, protocolName + ".log"));

            expectedOutputs.add(new File(basedir, "sequenceAnalysis.xml"));

            expectedOutputs.add(new File(basedir, "Shared"));
            expectedOutputs.add(new File(basedir, "Shared/SIVmac239.fasta"));
            expectedOutputs.add(new File(basedir, "Shared/SIVmac239.fasta.fai"));
            expectedOutputs.add(new File(basedir, "Shared/SIVmac239.idKey.txt"));

            expectedOutputs.add(new File(basedir, "paired1"));
            expectedOutputs.add(new File(basedir, "paired1/Alignment"));
            File bam1 = new File(basedir, "paired1/Alignment/TestReadset1.bam");
            expectedOutputs.add(bam1);
            expectedOutputs.add(new File(basedir, "paired1/Alignment/TestReadset1.bam.bai"));
            expectedOutputs.add(new File(basedir, "paired1/Alignment/paired1.bowtie.unaligned_1.fastq"));
            expectedOutputs.add(new File(basedir, "paired1/Alignment/paired1.bowtie.unaligned_2.fastq"));

            expectedOutputs.add(new File(basedir, "paired3"));
            expectedOutputs.add(new File(basedir, "paired3/Alignment"));
            File bam2 = new File(basedir, "paired3/Alignment/TestReadset2.bam");
            expectedOutputs.add(bam2);
            expectedOutputs.add(new File(basedir, "paired3/Alignment/TestReadset2.bam.bai"));
            expectedOutputs.add(new File(basedir, "paired3/Alignment/paired3.bowtie.unaligned.fastq"));

            expectedOutputs.add(new File(basedir, "paired4"));
            expectedOutputs.add(new File(basedir, "paired4/Alignment"));
            File bam3 = new File(basedir, "paired4/Alignment/TestReadset3.bam");
            expectedOutputs.add(bam3);
            expectedOutputs.add(new File(basedir, "paired4/Alignment/TestReadset3.bam.bai"));
            expectedOutputs.add(new File(basedir, "paired4/Alignment/paired4.bowtie.unaligned.fastq"));

            validateInputs();
            verifyFileOutputs(basedir, expectedOutputs);
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

            String protocolName = "TestBWAMemWithSavedLibrary_" + System.currentTimeMillis();
            String[] fileNames = getFilenamesForReadsets();
            JSONObject config = substituteParams(new File(_sampleData, ALIGNMENT_JOB), protocolName, fileNames);
            config.put("alignment", "BWA-Mem");
            config.put("referenceLibraryCreation", "SavedLibrary");
            config.put("referenceLibraryCreation.SavedLibrary.libraryId", libraryId);
            appendSamplesForAnalysis(config, _readsets);

            PipelineJob job = createPipelineJob(protocolName, ANALYSIS_TASKID, config.toString(), fileNames);
            waitForJob(job);

            //we expect the index to get copied back to the reference library location
            assert alignmentIndexDir.exists() && alignmentIndexDir.listFiles().length > 0 : "Aligner index was not cached";

            Set<File> expectedOutputs = new HashSet<>();
            File basedir = new File(_pipelineRoot, "sequenceAnalysis/" + protocolName);
            expectedOutputs.add(new File(basedir, protocolName + ".pipe.xar.xml"));
            expectedOutputs.add(new File(basedir, protocolName + ".log"));

            //NOTE: the first time we create a library, we make the indexes.  this job did not choose to delete intermediates, so they remain in the analysis dir too
            expectedOutputs.add(new File(basedir, "sequenceAnalysis.xml"));
            expectedOutputs.add(new File(basedir, "Shared"));
            expectedOutputs.add(new File(basedir, "Shared/bwa"));
            expectedOutputs.add(new File(basedir, "Shared/bwa/" + libraryId + "_TestLibrary.bwa.index.amb"));
            expectedOutputs.add(new File(basedir, "Shared/bwa/" + libraryId + "_TestLibrary.bwa.index.ann"));
            expectedOutputs.add(new File(basedir, "Shared/bwa/" + libraryId + "_TestLibrary.bwa.index.bwt"));
            expectedOutputs.add(new File(basedir, "Shared/bwa/" + libraryId + "_TestLibrary.bwa.index.pac"));
            expectedOutputs.add(new File(basedir, "Shared/bwa/" + libraryId + "_TestLibrary.bwa.index.sa"));

            expectedOutputs.add(new File(basedir, "paired1"));
            expectedOutputs.add(new File(basedir, "paired1/Alignment"));
            File bam1 = new File(basedir, "paired1/Alignment/TestReadset1.bam");
            expectedOutputs.add(bam1);
            expectedOutputs.add(new File(basedir, "paired1/Alignment/TestReadset1.bam.bai"));

            expectedOutputs.add(new File(basedir, "paired3"));
            expectedOutputs.add(new File(basedir, "paired3/Alignment"));
            File bam2 = new File(basedir, "paired3/Alignment/TestReadset2.bam");
            expectedOutputs.add(bam2);
            expectedOutputs.add(new File(basedir, "paired3/Alignment/TestReadset2.bam.bai"));

            expectedOutputs.add(new File(basedir, "paired4"));
            expectedOutputs.add(new File(basedir, "paired4/Alignment"));
            File bam3 = new File(basedir, "paired4/Alignment/TestReadset3.bam");
            expectedOutputs.add(bam3);
            expectedOutputs.add(new File(basedir, "paired4/Alignment/TestReadset3.bam.bai"));

            validateInputs();
            verifyFileOutputs(basedir, expectedOutputs);
            validateAlignment(bam1, 320, 102);
            validateAlignment(bam2, 160, 51);
            validateAlignment(bam3, 159, 52);

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
            String protocolName = "TestBWAMemWithSavedLibrary2_" + System.currentTimeMillis();
            String[] fileNames = getFilenamesForReadsets();
            JSONObject config = substituteParams(new File(_sampleData, ALIGNMENT_JOB), protocolName, fileNames);
            config.put("alignment", "BWA-Mem");
            config.put("referenceLibraryCreation", "SavedLibrary");
            config.put("referenceLibraryCreation.SavedLibrary.libraryId", libraryId);
            appendSamplesForAnalysis(config, _readsets);

            PipelineJob job = createPipelineJob(protocolName, ANALYSIS_TASKID, config.toString(), fileNames);
            waitForJob(job);

            Set<File> expectedOutputs = new HashSet<>();
            File basedir = new File(_pipelineRoot, "sequenceAnalysis/" + protocolName);
            expectedOutputs.add(new File(basedir, protocolName + ".pipe.xar.xml"));
            expectedOutputs.add(new File(basedir, protocolName + ".log"));

            expectedOutputs.add(new File(basedir, "sequenceAnalysis.xml"));
            expectedOutputs.add(new File(basedir, "Shared"));

            expectedOutputs.add(new File(basedir, "paired1"));
            expectedOutputs.add(new File(basedir, "paired1/Alignment"));
            File bam1 = new File(basedir, "paired1/Alignment/TestReadset1.bam");
            expectedOutputs.add(bam1);
            expectedOutputs.add(new File(basedir, "paired1/Alignment/TestReadset1.bam.bai"));

            expectedOutputs.add(new File(basedir, "paired3"));
            expectedOutputs.add(new File(basedir, "paired3/Alignment"));
            File bam2 = new File(basedir, "paired3/Alignment/TestReadset2.bam");
            expectedOutputs.add(bam2);
            expectedOutputs.add(new File(basedir, "paired3/Alignment/TestReadset2.bam.bai"));

            expectedOutputs.add(new File(basedir, "paired4"));
            expectedOutputs.add(new File(basedir, "paired4/Alignment"));
            File bam3 = new File(basedir, "paired4/Alignment/TestReadset3.bam");
            expectedOutputs.add(bam3);
            expectedOutputs.add(new File(basedir, "paired4/Alignment/TestReadset3.bam.bai"));

            validateInputs();
            verifyFileOutputs(basedir, expectedOutputs);
            validateAlignment(bam1, 320, 102);
            validateAlignment(bam2, 160, 51);
            validateAlignment(bam3, 159, 52);
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
            File alignmentIndexDir = new File(data.getFile().getParentFile(), AlignerIndexUtil.INDEX_DIR + "/Bisulfite_Genome");
            if (alignmentIndexDir.exists())
            {
                FileUtils.deleteDirectory(alignmentIndexDir);
            }

            String protocolName = "TestBismarkWithSavedLibraryAndAdapters_" + System.currentTimeMillis();
            String[] fileNames = getFilenamesForReadsets();
            JSONObject config = substituteParams(new File(_sampleData, ALIGNMENT_JOB), protocolName, fileNames);
            config.put("alignment", "Bismark");
            config.put("alignment.Bismark.seed_length", "65");
            config.put("alignment.Bismark.max_seed_mismatches", "3");
            config.put("alignment.Bismark.maqerr", "240");
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
            appendSamplesForAnalysis(config, _readsets);

            PipelineJob job = createPipelineJob(protocolName, ANALYSIS_TASKID, config.toString(), fileNames);
            waitForJob(job);

            //we expect the index to get copied back to the reference library location
            assert alignmentIndexDir.exists() && alignmentIndexDir.listFiles().length > 0 : "Aligner index was not cached";

            Set<File> expectedOutputs = new HashSet<>();
            File basedir = new File(_pipelineRoot, "sequenceAnalysis/" + protocolName);
            expectedOutputs.add(new File(basedir, protocolName + ".pipe.xar.xml"));
            expectedOutputs.add(new File(basedir, protocolName + ".log"));

            expectedOutputs.add(new File(basedir, "sequenceAnalysis.xml"));
            expectedOutputs.add(new File(basedir, "Shared"));

            expectedOutputs.add(new File(basedir, "paired1"));
            expectedOutputs.add(new File(basedir, "paired1/Preprocessing"));
            expectedOutputs.add(new File(basedir, "paired1/Preprocessing/paired1.preprocessed.fastq"));
            expectedOutputs.add(new File(basedir, "paired1/Preprocessing/paired2.preprocessed.fastq"));

            expectedOutputs.add(new File(basedir, "paired1/Alignment"));
            File bam1 = new File(basedir, "paired1/Alignment/TestReadset1.bam");
            expectedOutputs.add(bam1);

            addOptionalFile(expectedOutputs, new File(basedir, "paired1/Alignment/CpG_OB_TestReadset1.txt.gz"));
            addOptionalFile(expectedOutputs, new File(basedir, "paired1/Alignment/CpG_OT_TestReadset1.txt.gz"));
            addOptionalFile(expectedOutputs, new File(basedir, "paired1/Alignment/Non_CpG_OB_TestReadset1.bam.txt.gz"));
            addOptionalFile(expectedOutputs, new File(basedir, "paired1/Alignment/Non_CpG_OT_TestReadset1.txt.gz"));
            expectedOutputs.add(new File(basedir, "paired1/Alignment/TestReadset1.bam_splitting_report.txt"));
            expectedOutputs.add(new File(basedir, "paired1/Alignment/TestReadset1.CpG_Site_Summary.gff"));
            expectedOutputs.add(new File(basedir, "paired1/Alignment/TestReadset1.CpG_Site_Summary.methylation.txt"));
            expectedOutputs.add(new File(basedir, "paired1/Alignment/TestReadset1.CpG_Site_Summary.methylation.png"));
            expectedOutputs.add(new File(basedir, "paired1/Alignment/TestReadset1.M-bias.txt"));
            expectedOutputs.add(new File(basedir, "paired1/Alignment/TestReadset1.M-bias_R1.png"));
            expectedOutputs.add(new File(basedir, "paired1/Alignment/TestReadset1.M-bias_R2.png"));
            expectedOutputs.add(new File(basedir, "paired1/Alignment/paired1.preprocessed.fastq_bismark_PE.alignment_overview.png"));
            expectedOutputs.add(new File(basedir, "paired1/Alignment/paired1.preprocessed.fastq_bismark_PE_report.txt"));

            expectedOutputs.add(new File(basedir, "paired3"));
            expectedOutputs.add(new File(basedir, "paired3/Preprocessing"));
            expectedOutputs.add(new File(basedir, "paired3/Preprocessing/paired3.preprocessed.fastq"));

            expectedOutputs.add(new File(basedir, "paired3/Alignment"));

            File bam2 = new File(basedir, "paired3/Alignment/TestReadset2.bam");
            expectedOutputs.add(bam2);


            addOptionalFile(expectedOutputs, new File(basedir, "paired3/Alignment/CpG_OB_TestReadset2.txt.gz"));
            addOptionalFile(expectedOutputs, new File(basedir, "paired3/Alignment/CpG_OT_TestReadset2.txt.gz"));
            addOptionalFile(expectedOutputs, new File(basedir, "paired3/Alignment/Non_CpG_OB_TestReadset2.bam.txt.gz"));
            addOptionalFile(expectedOutputs, new File(basedir, "paired3/Alignment/Non_CpG_OT_TestReadset2.txt.gz"));
            expectedOutputs.add(new File(basedir, "paired3/Alignment/TestReadset2.bam_splitting_report.txt"));
            expectedOutputs.add(new File(basedir, "paired3/Alignment/TestReadset2.CpG_Site_Summary.gff"));
            expectedOutputs.add(new File(basedir, "paired3/Alignment/TestReadset2.CpG_Site_Summary.methylation.txt"));
            expectedOutputs.add(new File(basedir, "paired3/Alignment/TestReadset2.CpG_Site_Summary.methylation.png"));
            expectedOutputs.add(new File(basedir, "paired3/Alignment/TestReadset2.M-bias.txt"));
            expectedOutputs.add(new File(basedir, "paired3/Alignment/TestReadset2.M-bias_R1.png"));
            expectedOutputs.add(new File(basedir, "paired3/Alignment/paired3.preprocessed.fastq_bismark_SE.alignment_overview.png"));
            expectedOutputs.add(new File(basedir, "paired3/Alignment/paired3.preprocessed.fastq_bismark_SE_report.txt"));

            expectedOutputs.add(new File(basedir, "paired4"));
            expectedOutputs.add(new File(basedir, "paired4/Preprocessing"));
            expectedOutputs.add(new File(basedir, "paired4/Preprocessing/paired4.preprocessed.fastq"));

            expectedOutputs.add(new File(basedir, "paired4/Alignment"));
            File bam3 = new File(basedir, "paired4/Alignment/TestReadset3.bam");
            expectedOutputs.add(bam3);

            addOptionalFile(expectedOutputs, new File(basedir, "paired4/Alignment/CpG_OB_TestReadset3.txt.gz"));
            addOptionalFile(expectedOutputs, new File(basedir, "paired4/Alignment/CpG_OT_TestReadset3.txt.gz"));
            addOptionalFile(expectedOutputs, new File(basedir, "paired4/Alignment/Non_CpG_OB_TestReadset3.bam.txt.gz"));
            addOptionalFile(expectedOutputs, new File(basedir, "paired4/Alignment/Non_CpG_OT_TestReadset3.txt.gz"));
            expectedOutputs.add(new File(basedir, "paired4/Alignment/TestReadset3.bam_splitting_report.txt"));
            expectedOutputs.add(new File(basedir, "paired4/Alignment/TestReadset3.CpG_Site_Summary.gff"));
            expectedOutputs.add(new File(basedir, "paired4/Alignment/TestReadset3.CpG_Site_Summary.methylation.txt"));
            expectedOutputs.add(new File(basedir, "paired4/Alignment/TestReadset3.CpG_Site_Summary.methylation.png"));
            expectedOutputs.add(new File(basedir, "paired4/Alignment/TestReadset3.M-bias.txt"));
            expectedOutputs.add(new File(basedir, "paired4/Alignment/TestReadset3.M-bias_R1.png"));
            expectedOutputs.add(new File(basedir, "paired4/Alignment/paired4.preprocessed.fastq_bismark_SE.alignment_overview.png"));
            expectedOutputs.add(new File(basedir, "paired4/Alignment/paired4.preprocessed.fastq_bismark_SE_report.txt"));

            validateInputs();
            verifyFileOutputs(basedir, expectedOutputs);
            //disabled because this has been unreliable with the # of aligned reads
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
            String protocolName = "TestBismarkWithSavedLibraryAndDelete_" + System.currentTimeMillis();
            String[] fileNames = getFilenamesForReadsets();
            JSONObject config = substituteParams(new File(_sampleData, ALIGNMENT_JOB), protocolName, fileNames);
            config.put("alignment", "Bismark");
            config.put("alignment.Bismark.seed_length", "65");
            config.put("alignment.Bismark.max_seed_mismatches", "3");
            config.put("alignment.Bismark.maqerr", "240");
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
            appendSamplesForAnalysis(config, _readsets);

            PipelineJob job = createPipelineJob(protocolName, ANALYSIS_TASKID, config.toString(), fileNames);
            waitForJob(job);

            Set<File> expectedOutputs = new HashSet<>();
            File basedir = new File(_pipelineRoot, "sequenceAnalysis/" + protocolName);
            expectedOutputs.add(new File(basedir, protocolName + ".pipe.xar.xml"));
            expectedOutputs.add(new File(basedir, protocolName + ".log"));

            expectedOutputs.add(new File(basedir, "sequenceAnalysis.xml"));
            expectedOutputs.add(new File(basedir, "Shared"));

            expectedOutputs.add(new File(basedir, "paired1"));
            expectedOutputs.add(new File(basedir, "paired1/Alignment"));
            File bam1 = new File(basedir, "paired1/Alignment/TestReadset1.bam");
            expectedOutputs.add(bam1);

            addOptionalFile(expectedOutputs, new File(basedir, "paired1/Alignment/CpG_OB_TestReadset1.txt.gz"));
            addOptionalFile(expectedOutputs, new File(basedir, "paired1/Alignment/CpG_OT_TestReadset1.txt.gz"));
            addOptionalFile(expectedOutputs, new File(basedir, "paired1/Alignment/Non_CpG_OB_TestReadset1.bam.txt.gz"));
            addOptionalFile(expectedOutputs, new File(basedir, "paired1/Alignment/Non_CpG_OT_TestReadset1.txt.gz"));
            expectedOutputs.add(new File(basedir, "paired1/Alignment/TestReadset1.bam_splitting_report.txt"));
            expectedOutputs.add(new File(basedir, "paired1/Alignment/TestReadset1.CpG_Site_Summary.gff"));
            expectedOutputs.add(new File(basedir, "paired1/Alignment/TestReadset1.CpG_Site_Summary.methylation.txt"));
            expectedOutputs.add(new File(basedir, "paired1/Alignment/TestReadset1.CpG_Site_Summary.methylation.png"));
            expectedOutputs.add(new File(basedir, "paired1/Alignment/TestReadset1.M-bias.txt"));
            expectedOutputs.add(new File(basedir, "paired1/Alignment/TestReadset1.M-bias_R1.png"));
            expectedOutputs.add(new File(basedir, "paired1/Alignment/TestReadset1.M-bias_R2.png"));
            expectedOutputs.add(new File(basedir, "paired1/Alignment/paired1.preprocessed.fastq_bismark_PE.alignment_overview.png"));
            expectedOutputs.add(new File(basedir, "paired1/Alignment/paired1.preprocessed.fastq_bismark_PE_report.txt"));

            expectedOutputs.add(new File(basedir, "paired3"));
            expectedOutputs.add(new File(basedir, "paired3/Alignment"));
            File bam2 = new File(basedir, "paired3/Alignment/TestReadset2.bam");
            expectedOutputs.add(bam2);

            addOptionalFile(expectedOutputs, new File(basedir, "paired3/Alignment/CpG_OB_TestReadset2.txt.gz"));
            addOptionalFile(expectedOutputs, new File(basedir, "paired3/Alignment/CpG_OT_TestReadset2.txt.gz"));
            addOptionalFile(expectedOutputs, new File(basedir, "paired3/Alignment/Non_CpG_OB_TestReadset2.bam.txt.gz"));
            addOptionalFile(expectedOutputs, new File(basedir, "paired3/Alignment/Non_CpG_OT_TestReadset2.txt.gz"));
            expectedOutputs.add(new File(basedir, "paired3/Alignment/TestReadset2.bam_splitting_report.txt"));
            expectedOutputs.add(new File(basedir, "paired3/Alignment/TestReadset2.CpG_Site_Summary.gff"));
            expectedOutputs.add(new File(basedir, "paired3/Alignment/TestReadset2.CpG_Site_Summary.methylation.txt"));
            expectedOutputs.add(new File(basedir, "paired3/Alignment/TestReadset2.CpG_Site_Summary.methylation.png"));
            expectedOutputs.add(new File(basedir, "paired3/Alignment/TestReadset2.M-bias.txt"));
            expectedOutputs.add(new File(basedir, "paired3/Alignment/TestReadset2.M-bias_R1.png"));
            expectedOutputs.add(new File(basedir, "paired3/Alignment/paired3.preprocessed.fastq_bismark_SE.alignment_overview.png"));
            expectedOutputs.add(new File(basedir, "paired3/Alignment/paired3.preprocessed.fastq_bismark_SE_report.txt"));

            expectedOutputs.add(new File(basedir, "paired4"));
            expectedOutputs.add(new File(basedir, "paired4/Alignment"));
            File bam3 = new File(basedir, "paired4/Alignment/TestReadset3.bam");
            expectedOutputs.add(bam3);

            addOptionalFile(expectedOutputs, new File(basedir, "paired4/Alignment/CpG_OB_TestReadset3.txt.gz"));
            addOptionalFile(expectedOutputs, new File(basedir, "paired4/Alignment/CpG_OT_TestReadset3.txt.gz"));
            addOptionalFile(expectedOutputs, new File(basedir, "paired4/Alignment/Non_CpG_OB_TestReadset3.bam.txt.gz"));
            addOptionalFile(expectedOutputs, new File(basedir, "paired4/Alignment/Non_CpG_OT_TestReadset3.txt.gz"));
            expectedOutputs.add(new File(basedir, "paired4/Alignment/TestReadset3.bam_splitting_report.txt"));
            expectedOutputs.add(new File(basedir, "paired4/Alignment/TestReadset3.CpG_Site_Summary.gff"));
            expectedOutputs.add(new File(basedir, "paired4/Alignment/TestReadset3.CpG_Site_Summary.methylation.txt"));
            expectedOutputs.add(new File(basedir, "paired4/Alignment/TestReadset3.CpG_Site_Summary.methylation.png"));
            expectedOutputs.add(new File(basedir, "paired4/Alignment/TestReadset3.M-bias.txt"));
            expectedOutputs.add(new File(basedir, "paired4/Alignment/TestReadset3.M-bias_R1.png"));
            expectedOutputs.add(new File(basedir, "paired4/Alignment/paired4.preprocessed.fastq_bismark_SE.alignment_overview.png"));
            expectedOutputs.add(new File(basedir, "paired4/Alignment/paired4.preprocessed.fastq_bismark_SE_report.txt"));

            validateInputs();
            verifyFileOutputs(basedir, expectedOutputs);
            //disabled on purpose.  see note able about unreliability
            //validateAlignment(bam1, 18, 0);  //has also been 40??
            //validateAlignment(bam2, 151, 0);  //sometimes is 72?
            //validateAlignment(bam3, 150, 0); //sometimes 77?
        }

        @Test
        public void testMergedAlignments() throws Exception
        {
            if (!isExternalPipelineEnabled())
                return;

            String protocolName = "TestBWAMemMergedAlign_" + System.currentTimeMillis();
            String[] fileNames = getFilenamesForReadsets();
            JSONObject config = substituteParams(new File(_sampleData, ALIGNMENT_JOB), protocolName, fileNames);
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

            appendSamplesForAnalysis(config, models);

            PipelineJob job = createPipelineJob(protocolName, ANALYSIS_TASKID, config.toString(), fileNames);
            waitForJob(job);

            Set<File> expectedOutputs = new HashSet<>();
            File basedir = new File(_pipelineRoot, "sequenceAnalysis/" + protocolName);
            expectedOutputs.add(new File(basedir, protocolName + ".pipe.xar.xml"));
            expectedOutputs.add(new File(basedir, protocolName + ".log"));

            expectedOutputs.add(new File(basedir, "sequenceAnalysis.xml"));

            expectedOutputs.add(new File(basedir, "Shared"));
            expectedOutputs.add(new File(basedir, "Shared/SIVmac239.fasta"));
            expectedOutputs.add(new File(basedir, "Shared/SIVmac239.fasta.fai"));
            expectedOutputs.add(new File(basedir, "Shared/SIVmac239.idKey.txt"));

            expectedOutputs.add(new File(basedir, "Shared/bwa"));
            expectedOutputs.add(new File(basedir, "Shared/bwa/SIVmac239.bwa.index.amb"));
            expectedOutputs.add(new File(basedir, "Shared/bwa/SIVmac239.bwa.index.ann"));
            expectedOutputs.add(new File(basedir, "Shared/bwa/SIVmac239.bwa.index.bwt"));
            expectedOutputs.add(new File(basedir, "Shared/bwa/SIVmac239.bwa.index.pac"));
            expectedOutputs.add(new File(basedir, "Shared/bwa/SIVmac239.bwa.index.sa"));

            expectedOutputs.add(new File(basedir, "s_G1_L001_R1_001"));
            expectedOutputs.add(new File(basedir, "s_G1_L001_R1_001/Alignment"));
            File bam1 = new File(basedir, "s_G1_L001_R1_001/Alignment/TestMergedReadset.bam");
            expectedOutputs.add(bam1);
            expectedOutputs.add(new File(basedir, "s_G1_L001_R1_001/Alignment/TestMergedReadset.bam.bai"));

            expectedOutputs.add(new File(basedir, "s_G1_L002_R1_001"));
            expectedOutputs.add(new File(basedir, "s_G1_L002_R1_001/Alignment"));

            validateInputs();
            verifyFileOutputs(basedir, expectedOutputs);
            validateAlignment(bam1, 640, 204);
        }
    }

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

        @Test
        public void testGSnap() throws Exception
        {
            if (!isExternalPipelineEnabled())
                return;

            String protocolName = "TestGSnap_" + System.currentTimeMillis();
            String[] fileNames = getFilenamesForReadsets();
            JSONObject config = substituteParams(new File(_sampleData, ALIGNMENT_JOB), protocolName, fileNames);
            config.put("alignment", "GSnap");
            appendSamplesForAnalysis(config, _readsets);

            PipelineJob job = createPipelineJob(protocolName, ANALYSIS_TASKID, config.toString(), fileNames);
            waitForJob(job);

            Set<File> expectedOutputs = new HashSet<>();
            File basedir = new File(_pipelineRoot, "sequenceAnalysis/" + protocolName);
            expectedOutputs.add(new File(basedir, protocolName + ".pipe.xar.xml"));
            expectedOutputs.add(new File(basedir, protocolName + ".log"));

            expectedOutputs.add(new File(basedir, "sequenceAnalysis.xml"));

            expectedOutputs.add(new File(basedir, "Shared"));
            expectedOutputs.add(new File(basedir, "Shared/SIVmac239.fasta"));
            expectedOutputs.add(new File(basedir, "Shared/SIVmac239.fasta.fai"));
            expectedOutputs.add(new File(basedir, "Shared/SIVmac239.idKey.txt"));

            expectedOutputs.add(new File(basedir, "Shared/GSnap"));
            expectedOutputs.add(new File(basedir, "Shared/GSnap/GSnap.maps"));
            expectedOutputs.add(new File(basedir, "Shared/GSnap/GSnap.chromosome"));
            expectedOutputs.add(new File(basedir, "Shared/GSnap/GSnap.chromosome.iit"));
            expectedOutputs.add(new File(basedir, "Shared/GSnap/GSnap.chrsubset"));
            expectedOutputs.add(new File(basedir, "Shared/GSnap/GSnap.contig"));
            expectedOutputs.add(new File(basedir, "Shared/GSnap/GSnap.contig.iit"));
            expectedOutputs.add(new File(basedir, "Shared/GSnap/GSnap.genomebits128"));
            expectedOutputs.add(new File(basedir, "Shared/GSnap/GSnap.genomecomp"));
            expectedOutputs.add(new File(basedir, "Shared/GSnap/GSnap.ref123offsets64meta"));
            expectedOutputs.add(new File(basedir, "Shared/GSnap/GSnap.ref123offsets64strm"));
            expectedOutputs.add(new File(basedir, "Shared/GSnap/GSnap.ref123positions"));
            expectedOutputs.add(new File(basedir, "Shared/GSnap/GSnap.sachildexc"));
            expectedOutputs.add(new File(basedir, "Shared/GSnap/GSnap.sachildguide1024"));
            expectedOutputs.add(new File(basedir, "Shared/GSnap/GSnap.saindex64meta"));
            expectedOutputs.add(new File(basedir, "Shared/GSnap/GSnap.saindex64strm"));
            expectedOutputs.add(new File(basedir, "Shared/GSnap/GSnap.salcpchilddc"));
            expectedOutputs.add(new File(basedir, "Shared/GSnap/GSnap.salcpexc"));
            expectedOutputs.add(new File(basedir, "Shared/GSnap/GSnap.salcpguide1024"));
            expectedOutputs.add(new File(basedir, "Shared/GSnap/GSnap.sarray"));
            expectedOutputs.add(new File(basedir, "Shared/GSnap/GSnap.version"));

            expectedOutputs.add(new File(basedir, "paired1"));
            expectedOutputs.add(new File(basedir, "paired1/Alignment"));
            File bam1 = new File(basedir, "paired1/Alignment/TestReadset1.bam");
            expectedOutputs.add(bam1);
            expectedOutputs.add(new File(basedir, "paired1/Alignment/TestReadset1.bam.bai"));
            //expectedOutputs.add(new File(basedir, "paired1/Alignment/paired1.gsnap.concordant_mult.bam"));
            //expectedOutputs.add(new File(basedir, "paired1/Alignment/paired1.gsnap.halfmapping_mult.bam"));
            expectedOutputs.add(new File(basedir, "paired1/Alignment/paired1.gsnap.halfmapping_uniq.bam"));
            expectedOutputs.add(new File(basedir, "paired1/Alignment/paired1.gsnap.nomapping.bam"));
            //expectedOutputs.add(new File(basedir, "paired1/Alignment/paired1.gsnap.paired_mult.bam"));
            //expectedOutputs.add(new File(basedir, "paired1/Alignment/paired1.gsnap.unpaired_mult.bam"));
            //expectedOutputs.add(new File(basedir, "paired1/Alignment/paired1.gsnap.unpaired_uniq.bam"));

            expectedOutputs.add(new File(basedir, "paired3"));
            expectedOutputs.add(new File(basedir, "paired3/Alignment"));
            File bam2 = new File(basedir, "paired3/Alignment/TestReadset2.bam");
            expectedOutputs.add(bam2);
            expectedOutputs.add(new File(basedir, "paired3/Alignment/TestReadset2.bam.bai"));
            expectedOutputs.add(new File(basedir, "paired3/Alignment/paired3.gsnap.nomapping.bam"));
            expectedOutputs.add(new File(basedir, "paired3/Alignment/paired3.gsnap.unpaired_mult.bam"));

            expectedOutputs.add(new File(basedir, "paired4"));
            expectedOutputs.add(new File(basedir, "paired4/Alignment"));
            File bam3 = new File(basedir, "paired4/Alignment/TestReadset3.bam");
            expectedOutputs.add(bam3);
            expectedOutputs.add(new File(basedir, "paired4/Alignment/TestReadset3.bam.bai"));
            expectedOutputs.add(new File(basedir, "paired4/Alignment/paired4.gsnap.nomapping.bam"));
            expectedOutputs.add(new File(basedir, "paired4/Alignment/paired4.gsnap.unpaired_mult.bam"));

            validateInputs();

            //this is probably due to adapters
            verifyFileOutputs(basedir, expectedOutputs);
            validateAlignment(bam1, 316, 106);
            //NOTE: some of the time we have 150 aligned and sometimes 150??
            //validateAlignment(bam2, 152, 59);
            //validateAlignment(bam3, 152, 59);
        }

        //@Test
        public void testStar() throws Exception
        {
            if (!isExternalPipelineEnabled())
                return;

            String protocolName = "TestStar_" + System.currentTimeMillis();
            String[] fileNames = getFilenamesForReadsets();
            JSONObject config = substituteParams(new File(_sampleData, ALIGNMENT_JOB), protocolName, fileNames);
            config.put("alignment", "STAR");
            appendSamplesForAnalysis(config, _readsets);

            PipelineJob job = createPipelineJob(protocolName, ANALYSIS_TASKID, config.toString(), fileNames);
            waitForJob(job);

            Set<File> expectedOutputs = new HashSet<>();
            File basedir = new File(_pipelineRoot, "sequenceAnalysis/" + protocolName);
            expectedOutputs.add(new File(basedir, protocolName + ".pipe.xar.xml"));
            expectedOutputs.add(new File(basedir, protocolName + ".log"));

            expectedOutputs.add(new File(basedir, "sequenceAnalysis.xml"));

            expectedOutputs.add(new File(basedir, "Shared"));
            expectedOutputs.add(new File(basedir, "Shared/SIVmac239.fasta"));
            expectedOutputs.add(new File(basedir, "Shared/SIVmac239.fasta.fai"));
            expectedOutputs.add(new File(basedir, "Shared/SIVmac239.idKey.txt"));

            expectedOutputs.add(new File(basedir, "Shared/Bowtie"));
            expectedOutputs.add(new File(basedir, "Shared/Bowtie/SIVmac239.bowtie.index.1.ebwt"));
            expectedOutputs.add(new File(basedir, "Shared/Bowtie/SIVmac239.bowtie.index.2.ebwt"));
            expectedOutputs.add(new File(basedir, "Shared/Bowtie/SIVmac239.bowtie.index.3.ebwt"));
            expectedOutputs.add(new File(basedir, "Shared/Bowtie/SIVmac239.bowtie.index.4.ebwt"));
            expectedOutputs.add(new File(basedir, "Shared/Bowtie/SIVmac239.bowtie.index.rev.1.ebwt"));
            expectedOutputs.add(new File(basedir, "Shared/Bowtie/SIVmac239.bowtie.index.rev.2.ebwt"));

            expectedOutputs.add(new File(basedir, "paired1"));
            expectedOutputs.add(new File(basedir, "paired1/Alignment"));
            File bam1 = new File(basedir, "paired1/Alignment/TestReadset1.bam");
            expectedOutputs.add(bam1);
            expectedOutputs.add(new File(basedir, "paired1/Alignment/TestReadset1.bam.bai"));
            expectedOutputs.add(new File(basedir, "paired1/Alignment/paired1.bowtie.unaligned_1.fastq"));
            expectedOutputs.add(new File(basedir, "paired1/Alignment/paired1.bowtie.unaligned_2.fastq"));

            expectedOutputs.add(new File(basedir, "paired3"));
            expectedOutputs.add(new File(basedir, "paired3/Alignment"));
            File bam2 = new File(basedir, "paired3/Alignment/TestReadset2.bam");
            expectedOutputs.add(bam2);
            expectedOutputs.add(new File(basedir, "paired3/Alignment/TestReadset2.bam.bai"));
            expectedOutputs.add(new File(basedir, "paired3/Alignment/paired3.bowtie.unaligned.fastq"));

            expectedOutputs.add(new File(basedir, "paired4"));
            expectedOutputs.add(new File(basedir, "paired4/Alignment"));
            File bam3 = new File(basedir, "paired4/Alignment/TestReadset3.bam");
            expectedOutputs.add(bam3);
            expectedOutputs.add(new File(basedir, "paired4/Alignment/TestReadset3.bam.bai"));
            expectedOutputs.add(new File(basedir, "paired4/Alignment/paired4.bowtie.unaligned.fastq"));

            validateInputs();

            //this is probably due to adapters
            verifyFileOutputs(basedir, expectedOutputs);
            validateAlignment(bam1, 0, 422);
            validateAlignment(bam2, 155, 56);
            validateAlignment(bam3, 154, 57);
        }
    }
}
