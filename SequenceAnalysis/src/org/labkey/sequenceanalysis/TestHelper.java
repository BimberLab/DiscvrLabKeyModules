package org.labkey.sequenceanalysis;

import net.sf.picard.fastq.FastqRecord;
import net.sf.samtools.SAMFileReader;
import net.sf.samtools.SAMRecord;
import net.sf.samtools.SAMRecordIterator;
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
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.resource.FileResource;
import org.labkey.api.resource.MergedDirectoryResource;
import org.labkey.api.resource.Resource;
import org.labkey.api.settings.AppProps;
import org.labkey.api.test.TestTimeout;
import org.labkey.api.util.Compress;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Path;
import org.labkey.api.util.TestContext;
import org.labkey.api.view.ViewServlet;
import org.labkey.sequenceanalysis.model.BarcodeModel;
import org.labkey.sequenceanalysis.model.ReadsetModel;
import org.labkey.sequenceanalysis.pipeline.SequenceTaskHelper;
import org.labkey.sequenceanalysis.util.FastqUtils;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.bind.annotation.RequestMethod;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * User: bimber
 * Date: 11/25/12
 * Time: 1:58 PM
 */
public class TestHelper
{
    private static TestHelper _instance = new TestHelper();
    public static final String PIPELINE_PROP_NAME = "sequencePipelineEnabled";

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
    public static class AbstractPipelineTestCase extends Assert
    {
        protected final String DUAL_BARCODE_FILENAME = "dualBarcodes_SIV.fastq";
        protected final String SAMPLE_SFF_FILENAME = "sample454_SIV.sff";

        protected final String PAIRED_FILENAME1 = "paired1.fastq.gz";
        protected final String PAIRED_FILENAME2 = "paired2.fastq.gz";
        protected final String UNZIPPED_PAIRED_FILENAME1 = "paired3.fastq";
        protected final String UNZIPPED_PAIRED_FILENAME2 = "paired4.fastq";

        protected final String READSET_JOB = "readsetJob.json";
        protected final String ALIGNMENT_JOB = "alignmentJob.json";
        protected final String IMPORT_TASKID = "org.labkey.api.pipeline.file.FileAnalysisTaskPipeline:sequenceImportPipeline";
        protected final String ANALYSIS_TASKID = "org.labkey.api.pipeline.file.FileAnalysisTaskPipeline:sequenceAnalysisPipeline";

        protected static final String PROJECT_NAME = "SequencePipelineTestProject";
        protected Container _project;
        protected TestContext _context;
        protected File _pipelineRoot;
        protected File _sampleData;

        protected Boolean _isExternalPipelineEnabled = null;

        protected static final Logger _log = Logger.getLogger(AbstractPipelineTestCase.class);

        @BeforeClass
        public static void initialSetUp() throws Exception
        {
            //pre-clean
            cleanup();

            Container project = ContainerManager.getForPath(PROJECT_NAME);
            if (project == null)
            {
                project = ContainerManager.createContainer(ContainerManager.getRoot(), PROJECT_NAME);
                Set<Module> modules = new HashSet<>();
                modules.addAll(project.getActiveModules());
                modules.add(ModuleLoader.getInstance().getModule(SequenceAnalysisModule.NAME));
                project.setFolderType(ModuleLoader.getInstance().getFolderType("Laboratory Folder"), TestContext.get().getUser());
                project.setActiveModules(modules);
            }
        }

        @Before
        public void setUp() throws Exception
        {
            _context = TestContext.get();
            _sampleData = getSampleDataDir();
            if (_sampleData == null || !_sampleData.exists())
            {
                throw new Exception("sampledata folder does not exist: " + _sampleData.getPath());
            }
            _project = ContainerManager.getForPath(PROJECT_NAME);
            _pipelineRoot = PipelineService.get().getPipelineRootSetting(_project).getRootPath();
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

            File file3 = new File(_pipelineRoot, PAIRED_FILENAME1);
            if (!file3.exists())
                FileUtils.copyFile(new File(_sampleData, PAIRED_FILENAME1), file3);

            File file4 = new File(_pipelineRoot, PAIRED_FILENAME2);
            if (!file4.exists())
                FileUtils.copyFile(new File(_sampleData, PAIRED_FILENAME2), file4);

            File file5 = new File(_pipelineRoot, UNZIPPED_PAIRED_FILENAME1);
            if (!file5.exists())
                Compress.decompressGzip(new File(_sampleData, PAIRED_FILENAME1), file5);

            File file6 = new File(_pipelineRoot, UNZIPPED_PAIRED_FILENAME2);
            if (!file6.exists())
                Compress.decompressGzip(new File(_sampleData, PAIRED_FILENAME2), file6);
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

            Collection<File> files = FileUtils.listFilesAndDirs(basedir, filter, filter);
            if (expectedOutputs.size() != (files.size() - 1))
            {
                for (File f : files)
                {
                    if (!expectedOutputs.contains(f))
                    {
                        _log.error("Unexpected file found: " + f.getPath());
                    }
                }
            }
            Assert.assertEquals("Incorrect number of outputs created", expectedOutputs.size(), files.size() - 1);
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

            HttpServletRequest request = ViewServlet.mockRequest(RequestMethod.POST.name(), DetailsURL.fromString("/pipeline-analysis/startAnalysis.view").copy(_project).getActionURL(), _context.getUser(), headers, requestContent);

            MockHttpServletResponse response = ViewServlet.mockDispatch(request, null);
            JSONObject responseJson = new JSONObject(response.getContentAsString());
            if (response.getStatus() != HttpServletResponse.SC_OK)
                throw new RuntimeException("Problem creating pipeline job: " + responseJson.getString("exception"));

            String guid = responseJson.getString("jobGUID");
            Integer jobId = PipelineService.get().getJobId(_context.getUser(), _project, guid);

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

        protected void waitForJob(String guid) throws Exception
        {
            try
            {
                long start = System.currentTimeMillis();
                long timeout = 60 * 1000 * 3; //3 mins

                Thread.sleep(1000);

                while (!isJobDone(guid))
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

        private boolean isJobDone(String guid) throws Exception
        {
            TableInfo ti = PipelineService.get().getJobsTable(_context.getUser(), _project);
            TableSelector ts = new TableSelector(ti, new SimpleFilter(FieldKey.fromString("job"), guid), null);
            Map<String, Object> map = ts.getMap();
            PipelineJob job = PipelineJobService.get().getJobStore().getJob(guid);

            if (PipelineJob.ERROR_STATUS.equalsIgnoreCase((String)map.get("status")))
                throw new Exception("There was an error running job: " + (job == null ? guid : job.getDescription()));

            if (PipelineJob.COMPLETE_STATUS.equalsIgnoreCase((String)map.get("status")))
                return true;

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

        protected void appendSamples(JSONObject config, String[] filenames)
        {
            if (config.getBoolean("inputfile.merge"))
            {
                String filename = config.getString("inputfile.merge.basename");
                config.put("sample_0", "{\"platform\":\"ILLUMINA\",\"sampleid\":\"1\",\"readset\":\"\",\"instrument_run_id\":\"\",\"readsetname\":\"TestReadset\",\"fileName\":\"" + filename + "\",\"subjectid\":\"Subject\"}");
            }
            else if (config.getBoolean("inputfile.barcode"))
            {
                //NOTE: this cannot automatically be inferred based on the other info in the config, so we just skip it
            }
            else if (config.getBoolean("inputfile.pairedend"))
            {
                int i = 0;
                while (i < filenames.length)
                {
                    config.put("sample_" + i, "{\"platform\":\"ILLUMINA\",\"sampleid\":\"1\",\"readset\":\"\",\"instrument_run_id\":\"\",\"readsetname\":\"TestReadset" + i + "\",\"fileName\":\"" + filenames[i] + "\",\"fileName2\":\"" + filenames[i + 1] + "\",\"subjectid\":\"Subject\"}");
                    i++;
                    i++;
                }
            }
            else
            {
                int idx = 0;
                for (String filename : filenames)
                {
                    config.put("sample_" + idx, "{\"platform\":\"ILLUMINA\",\"sampleid\":\"1\",\"readset\":\"\",\"instrument_run_id\":\"\",\"readsetname\":\"TestReadset" + idx + "\",\"fileName\":\"" + filename + "\",\"subjectid\":\"Subject" + idx + "\"}");
                    idx++;
                }
            }
        }

        @AfterClass
        public static void cleanup()
        {
            Container project = ContainerManager.getForPath(PROJECT_NAME);
            if (project != null)
            {
                File _pipelineRoot = PipelineService.get().getPipelineRootSetting(project).getRootPath();
                try
                {
                    if (_pipelineRoot.exists())
                    {
                        for (File f : _pipelineRoot.listFiles())
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

                ContainerManager.delete(project, TestContext.get().getUser());
            }
        }
    }

    public static class SequenceImportPipelineTestCase extends AbstractPipelineTestCase
    {
//        public void doTestSteps() throws Exception
//        {
//            basicTest();
//            pairedEndTest();
//            pairedEndTestDeletingInputs();
//            pairedEndTestMovingInputs();
//
//            if (isExternalPipelineEnabled())
//            {
//                barcodeTest();
//                barcodeTestDeletingIntermediates();
//                mergeTest();
//                mergeTestDeletingIntermediates();
//            }
//        }

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
            appendSamples(config, fileNames);

            PipelineJob job = createPipelineJob(protocolName, IMPORT_TASKID, config.toString(), fileNames);
            waitForJob(job.getJobGUID());

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

            String protocolName = "MergeTest_" + System.currentTimeMillis();
            String[] fileNames = new String[]{DUAL_BARCODE_FILENAME, SAMPLE_SFF_FILENAME};
            JSONObject config = substituteParams(new File(_sampleData, READSET_JOB), protocolName, fileNames);
            config.put("inputfile.merge", true);
            String mergeName = "MergedFile";
            config.put("inputfile.merge.basename", mergeName);
            appendSamples(config, fileNames);

            PipelineJob job = createPipelineJob(protocolName, IMPORT_TASKID, config.toString(), fileNames);
            waitForJob(job.getJobGUID());

            Set<File> expectedOutputs = new HashSet<>();
            File basedir = new File(_pipelineRoot, "sequenceImport/" + protocolName);
            expectedOutputs.add(new File(basedir, "all.log"));
            expectedOutputs.add(new File(basedir, "all.pipe.xar.xml"));
            expectedOutputs.add(new File(basedir, "sequenceImport.xml"));
            expectedOutputs.add(new File(basedir, "sequencePipeline.xml"));

            expectedOutputs.add(new File(basedir, "Normalization"));
            expectedOutputs.add(new File(basedir, "Normalization/" + mergeName));
            File fq = new File(basedir, "Normalization/" + mergeName + ".fastq.gz");
            expectedOutputs.add(fq);
            expectedOutputs.add(new File(basedir, "Normalization/" + mergeName + "/" + FileUtil.getBaseName(SAMPLE_SFF_FILENAME) + ".fastq"));
            verifyFileOutputs(basedir, expectedOutputs);
            verifyFileInputs(basedir, fileNames, config);

            validateReadsets(job, config);

            int count = FastqUtils.getSequenceCount(fq);
            Assert.assertEquals("Incorrect read number", 3360, count);
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
            String[] fileNames = new String[]{DUAL_BARCODE_FILENAME, SAMPLE_SFF_FILENAME};
            JSONObject config = substituteParams(new File(_sampleData, READSET_JOB), protocolName, fileNames);
            config.put("inputfile.merge", true);
            String mergeName = "MergedFile";
            config.put("inputfile.merge.basename", mergeName);
            appendSamples(config, fileNames);
            config.put("deleteIntermediateFiles", true);
            config.put("inputfile.inputTreatment", "delete");

            PipelineJob job = createPipelineJob(protocolName, IMPORT_TASKID, config.toString(), fileNames);
            waitForJob(job.getJobGUID());

            Set<File> expectedOutputs = new HashSet<>();
            File basedir = new File(_pipelineRoot, "sequenceImport/" + protocolName);
            expectedOutputs.add(new File(basedir, "all.log"));
            expectedOutputs.add(new File(basedir, "all.pipe.xar.xml"));
            expectedOutputs.add(new File(basedir, "sequenceImport.xml"));
            expectedOutputs.add(new File(basedir, "sequencePipeline.xml"));

            expectedOutputs.add(new File(basedir, "Normalization"));
            expectedOutputs.add(new File(basedir, "Normalization/" + mergeName + ".fastq.gz"));
            verifyFileOutputs(basedir, expectedOutputs);
            verifyFileInputs(basedir, fileNames, config);

            validateReadsets(job, config);
        }

        private JSONObject getBarcodeConfig(String protocolName, String[] fileNames) throws Exception
        {
            JSONObject config = substituteParams(new File(_sampleData, READSET_JOB), protocolName, fileNames);
            config.put("inputfile.barcode", true);
            config.put("inputfile.barcodeGroups", "[\"GSMIDs\",\"Fluidigm\"]");
            config.put("sample_0", "{\"readsetname\":\"TestReadset0\",\"mid5\":\"MID001\",\"mid3\":\"MID001\",\"sampleid\":\"1\",\"readset\":\"\",\"instrument_run_id\":\"\",\"fileName\":\"" + DUAL_BARCODE_FILENAME + "\",\"platform\":\"ILLUMINA\",\"subjectid\":\"Subject\"}");
            config.put("sample_1", "{\"readsetname\":\"TestReadset1\",\"mid5\":\"MID002\",\"mid3\":\"MID001\",\"sampleid\":\"1\",\"readset\":\"\",\"instrument_run_id\":\"\",\"fileName\":\"" + DUAL_BARCODE_FILENAME + "\",\"platform\":\"LS454\",\"subjectid\":\"Subject\"}");
            config.put("sample_2", "{\"readsetname\":\"TestReadset2\",\"mid5\":\"MID003\",\"mid3\":\"MID001\",\"sampleid\":\"1\",\"readset\":\"\",\"instrument_run_id\":\"\",\"fileName\":\"" + DUAL_BARCODE_FILENAME + "\",\"platform\":\"SANGER\",\"subjectid\":\"Subject\"}");
            config.put("sample_3", "{\"readsetname\":\"TestReadset3\",\"mid5\":\"MID004\",\"mid3\":\"MID001\",\"sampleid\":\"1\",\"readset\":\"\",\"instrument_run_id\":\"\",\"fileName\":\"" + DUAL_BARCODE_FILENAME + "\",\"platform\":\"SANGER\",\"subjectid\":\"Subject\"}");
            config.put("sample_4", "{\"readsetname\":\"TestReadset4\",\"mid5\":\"MID005\",\"mid3\":\"MID001\",\"sampleid\":\"1\",\"readset\":\"\",\"instrument_run_id\":\"\",\"fileName\":\"" + DUAL_BARCODE_FILENAME + "\",\"platform\":\"SANGER\",\"subjectid\":\"Subject\"}");

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
            expectedOutputs.add(new File(basedir, "sequencePipeline.xml"));
            expectedOutputs.add(new File(basedir, FileUtil.getBaseName(DUAL_BARCODE_FILENAME) + ".pipe.xar.xml"));
            expectedOutputs.add(new File(basedir, FileUtil.getBaseName(DUAL_BARCODE_FILENAME) + ".log"));

            File normalizationDir = new File(basedir, "Normalization");
            expectedOutputs.add(new File(normalizationDir, FileUtil.getBaseName(DUAL_BARCODE_FILENAME) + "_MID001_MID001.fastq.gz"));
            expectedOutputs.add(new File(normalizationDir, FileUtil.getBaseName(DUAL_BARCODE_FILENAME) + "_MID002_MID001.fastq.gz"));
            expectedOutputs.add(new File(normalizationDir, FileUtil.getBaseName(DUAL_BARCODE_FILENAME) + "_MID003_MID001.fastq.gz"));
            expectedOutputs.add(new File(normalizationDir, FileUtil.getBaseName(DUAL_BARCODE_FILENAME) + "_MID004_MID001.fastq.gz"));
            expectedOutputs.add(new File(normalizationDir, FileUtil.getBaseName(DUAL_BARCODE_FILENAME) + "_unknowns.fastq.gz"));

            expectedOutputs.add(normalizationDir);
            expectedOutputs.add(new File(normalizationDir, FileUtil.getBaseName(DUAL_BARCODE_FILENAME)));
            expectedOutputs.add(new File(normalizationDir, FileUtil.getBaseName(DUAL_BARCODE_FILENAME) + "/" + FileUtil.getBaseName(DUAL_BARCODE_FILENAME) + ".barcode-summary.txt.gz"));

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
            waitForJob(job.getJobGUID());

            File basedir = new File(_pipelineRoot, "sequenceImport/" + protocolName);
            Set<File> expectedOutputs = getBarcodeOutputs(basedir);


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
            waitForJob(job.getJobGUID());

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
            config.put("inputfile.pairedend", true);

            appendSamples(config, fileNames);

            PipelineJob job = createPipelineJob(protocolName, IMPORT_TASKID, config.toString(), fileNames);
            waitForJob(job.getJobGUID());

            Set<File> expectedOutputs = new HashSet<>();
            File basedir = new File(_pipelineRoot, "sequenceImport/" + protocolName);
            expectedOutputs.add(new File(basedir, "sequenceImport.xml"));
            expectedOutputs.add(new File(basedir, "all.pipe.xar.xml"));
            expectedOutputs.add(new File(basedir, "all.log"));
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
            config.put("inputfile.pairedend", true);
            config.put("inputfile.inputTreatment", "compress");

            appendSamples(config, fileNames);

            PipelineJob job = createPipelineJob(protocolName, IMPORT_TASKID, config.toString(), fileNames);
            waitForJob(job.getJobGUID());

            Set<File> expectedOutputs = new HashSet<>();
            File basedir = new File(_pipelineRoot, "sequenceImport/" + protocolName);
            expectedOutputs.add(new File(basedir, "sequenceImport.xml"));
            expectedOutputs.add(new File(basedir, "all.pipe.xar.xml"));
            expectedOutputs.add(new File(basedir, "all.log"));

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
            config.put("inputfile.pairedend", true);
            config.put("inputfile.inputTreatment", "delete");

            appendSamples(config, fileNames);

            PipelineJob job = createPipelineJob(protocolName, IMPORT_TASKID, config.toString(), fileNames);
            waitForJob(job.getJobGUID());

            Set<File> expectedOutputs = new HashSet<>();
            File basedir = new File(_pipelineRoot, "sequenceImport/" + protocolName);
            expectedOutputs.add(new File(basedir, "sequenceImport.xml"));
            expectedOutputs.add(new File(basedir, "all.pipe.xar.xml"));
            expectedOutputs.add(new File(basedir, "all.log"));

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
            ReadsetModel[] models = getReadsetsForJob(SequenceTaskHelper.getExpRunIdForJob(job));
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
                if (key.startsWith("sample_"))
                    expected++;
            }

            return expected;
        }

        private void validateSamples(ReadsetModel[] models, JSONObject config)
        {
            Map<String, JSONObject> map = new HashMap<>();
            for (String key : config.keySet())
            {
                if (key.startsWith("sample_"))
                {
                    JSONObject o = new JSONObject(config.getString(key));
                    map.put(o.getString("readsetname"), o);
                }
            }

            for (ReadsetModel m : models)
            {
                JSONObject o = map.get(m.getName());
                Assert.assertNotNull("No config found for model: " + m.getName(), o);

                Assert.assertEquals("Incorrect readset name", o.getString("readsetname"), m.getName());
                Assert.assertEquals("Incorrect platform", o.getString("platform"), m.getPlatform());
                Assert.assertEquals("Incorrect sampleid", o.getInt("sampleid"), m.getSampleId().intValue());
                Assert.assertEquals("Incorrect subjectId", o.getString("subjectid"), m.getSubjectId());
                //TODO
                //Assert.assertEquals("Incorrect sampleDate", new Date(Date.parse(o.getString("sampledate"))), m.getSampleDate());
                Assert.assertNotNull(m.getFileId());
            }
        }

        private void validateQualityMetrics(ReadsetModel[] models, JSONObject config)
        {
            TableInfo ti = SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_QUALITY_METRICS);

            for (ReadsetModel m : models)
            {
                TableSelector ts = new TableSelector(ti, new SimpleFilter(FieldKey.fromString("readset"), m.getReadsetId()), null);
                Map<String, Object>[] metrics = ts.getMapArray();
                int expected = m.getFileId2() == null ? 4 : 8;
                Assert.assertEquals("Incorrect number of quality metrics created", expected, metrics.length);
            }
        }

        private ReadsetModel[] getReadsetsForJob(int runId)
        {
            TableInfo ti = SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_READSETS);
            TableSelector ts = new TableSelector(ti, new SimpleFilter(FieldKey.fromString("runid"), runId), null);
            return ts.getArray(ReadsetModel.class);
        }
    }

    public static class SequenceAnalysisPipelineTestCase extends AbstractPipelineTestCase
    {
        private List<ReadsetModel> _readsetModels;
        private boolean _hasPerformedSetup = false;

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
                    _readsetModels = createReadsets();
                    _hasPerformedSetup = true;
                }
            }
        }

        private void copyInputFiles() throws Exception
        {
            File file3 = new File(_pipelineRoot, PAIRED_FILENAME1);
            if (!file3.exists())
                FileUtils.copyFile(new File(_sampleData, PAIRED_FILENAME1), file3);

            File file4 = new File(_pipelineRoot, PAIRED_FILENAME2);
            if (!file4.exists())
                FileUtils.copyFile(new File(_sampleData, PAIRED_FILENAME2), file4);

            File file5 = new File(_pipelineRoot, UNZIPPED_PAIRED_FILENAME1);
            if (!file5.exists())
                Compress.decompressGzip(new File(_sampleData, PAIRED_FILENAME1), file5);

            File file6 = new File(_pipelineRoot, UNZIPPED_PAIRED_FILENAME2);
            if (!file6.exists())
                Compress.decompressGzip(new File(_sampleData, PAIRED_FILENAME2), file6);
        }

        private List<ReadsetModel> createReadsets() throws Exception
        {
            List<ReadsetModel> models = new ArrayList<>();
            TableInfo ti = SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_READSETS);

            File file1 = new File(_pipelineRoot, PAIRED_FILENAME1);
            File file2 = new File(_pipelineRoot, PAIRED_FILENAME2);
            ExpData d1 = createExpData(file1);
            ExpData d2 = createExpData(file2);
            ReadsetModel readset1 = new ReadsetModel();
            readset1.setFileId(d1.getRowId());
            readset1.setFileName(d1.getFile().getName());
            readset1.setFileId2(d2.getRowId());
            readset1.setFileName2(d2.getFile().getName());
            readset1.setName("TestReadset1");
            readset1.setContainer(_project.getId());
            readset1.setCreated(new Date());
            readset1.setCreatedBy(_context.getUser().getUserId());
            models.add(Table.insert(_context.getUser(), ti, readset1));

            File file3 = new File(_pipelineRoot, UNZIPPED_PAIRED_FILENAME1);
            ExpData d3 = createExpData(file3);
            ReadsetModel readset2 = new ReadsetModel();
            readset2.setFileId(d3.getRowId());
            readset2.setFileName(d3.getFile().getName());
            readset2.setName("TestReadset2");
            readset2.setContainer(_project.getId());
            readset2.setCreated(new Date());
            readset2.setCreatedBy(_context.getUser().getUserId());
            models.add(Table.insert(_context.getUser(), ti, readset2));

            File file4 = new File(_pipelineRoot, UNZIPPED_PAIRED_FILENAME2);
            ExpData d4 = createExpData(file4);
            ReadsetModel readset3 = new ReadsetModel();
            readset3.setFileId(d4.getRowId());
            readset3.setFileName(d4.getFile().getName());
            readset3.setName("TestReadset3");
            readset3.setContainer(_project.getId());
            readset3.setCreated(new Date());
            readset3.setCreatedBy(_context.getUser().getUserId());
            models.add(Table.insert(_context.getUser(), ti, readset3));

            return models;
        }

        private List<JSONObject> getReadsetJson(List<ReadsetModel> models)
        {
            List<JSONObject> jsons = new ArrayList<>();
            for (ReadsetModel m : models)
            {
                JSONObject json = new JSONObject();
                json.put("fileId", m.getFileId());
                json.put("fileName", m.getFileName());
                json.put("fileId2", m.getFileId2());
                json.put("fileName2", m.getFileName2());
                json.put("platform", m.getPlatform());
                json.put("sampleid", m.getSampleId());
                json.put("readset", m.getRowId());
                json.put("readsetname", m.getName());
                jsons.add(json);
            }

            return jsons;
        }

        private ExpData createExpData(File f)
        {
            ExpData d = ExperimentService.get().createData(_project, new DataType("SequenceFile"));
            d.setName(f.getName());
            d.setDataFileURI(f.toURI());
            d.save(_context.getUser());
            return d;
        }

        private String[] getFilenamesForReadsets()
        {
            List<String> files = new ArrayList<>();
            for (ReadsetModel m : _readsetModels)
            {
                files.add(m.getFileName());

                if (m.getFileName2() != null)
                    files.add(m.getFileName2());
            }

            return files.toArray(new String[files.size()]);
        }

        private void appendSamples(JSONObject config, List<ReadsetModel> readsetModels)
        {
            int i = 0;
            for (JSONObject json : getReadsetJson(readsetModels))
            {
                config.put("sample_" + i, json);
                i++;
            }
        }

        //we expect inputs to be unaltered
        private void validateInputs() throws PipelineJobException
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

        @Test
        public void testMosaik() throws Exception
        {
            if (!isExternalPipelineEnabled())
                return;

            String protocolName = "TestMosaik_" + System.currentTimeMillis();
            String[] fileNames = getFilenamesForReadsets();
            JSONObject config = substituteParams(new File(_sampleData, ALIGNMENT_JOB), protocolName, fileNames);
            config.put("aligner", "mosaik");
            appendSamples(config, _readsetModels);

            PipelineJob job = createPipelineJob(protocolName, ANALYSIS_TASKID, config.toString(), fileNames);
            waitForJob(job.getJobGUID());

            Set<File> expectedOutputs = new HashSet<>();
            File basedir = new File(_pipelineRoot, "sequenceAnalysis/" + protocolName);
            expectedOutputs.add(new File(basedir, "all.pipe.xar.xml"));
            expectedOutputs.add(new File(basedir, "all.log"));
            expectedOutputs.add(new File(basedir, "sequenceAnalysis.xml"));
            expectedOutputs.add(new File(basedir, "sequencePipeline.xml"));

            expectedOutputs.add(new File(basedir, "Shared"));
            expectedOutputs.add(new File(basedir, "Shared/Ref_DB.fasta"));
            expectedOutputs.add(new File(basedir, "Shared/Ref_DB.fasta.fai"));
            expectedOutputs.add(new File(basedir, "Shared/Ref_DB.mosaik"));

            expectedOutputs.add(new File(basedir, PAIRED_FILENAME1));
            expectedOutputs.add(new File(basedir, PAIRED_FILENAME2));

            expectedOutputs.add(new File(basedir, "paired1"));
            expectedOutputs.add(new File(basedir, "paired1/Alignment"));
            File bam1 = new File(basedir, "paired1/Alignment/paired1.bam");
            expectedOutputs.add(bam1);
            expectedOutputs.add(new File(basedir, "paired1/Alignment/paired1.bam.bai"));

            expectedOutputs.add(new File(basedir, "paired3"));
            expectedOutputs.add(new File(basedir, "paired3/Alignment"));
            File bam2 = new File(basedir, "paired3/Alignment/paired3.bam");
            expectedOutputs.add(bam2);
            expectedOutputs.add(new File(basedir, "paired3/Alignment/paired3.bam.bai"));

            expectedOutputs.add(new File(basedir, "paired4"));
            expectedOutputs.add(new File(basedir, "paired4/Alignment"));
            File bam3 = new File(basedir, "paired4/Alignment/paired4.bam");
            expectedOutputs.add(bam3);
            expectedOutputs.add(new File(basedir, "paired4/Alignment/paired4.bam.bai"));

            validateInputs();
            verifyFileOutputs(basedir, expectedOutputs);
            validateAlignment(bam1, 40, 0);
            validateAlignment(bam2, 20, 0);
            validateAlignment(bam3, 20, 0);
        }

        private void validateAlignment(File bam, int expectedAligned, int expectedUnaligned)
        {
            SAMFileReader reader = null;
            try
            {
                reader = new SAMFileReader(bam);

                SAMRecordIterator it = reader.iterator();
                int aligned = 0;
                int unaligned = 0;

                while (it.hasNext())
                {
                    SAMRecord rec = it.next();
                    if (rec.getReadUnmappedFlag())
                        unaligned++;
                    else
                        aligned++;
                }

                Assert.assertEquals("Incorrect aligned count for BAM: " + bam.getPath(), expectedAligned, aligned);
                Assert.assertEquals("Incorrect unaligned count for BAM: " + bam.getPath(), expectedUnaligned, unaligned);
            }
            finally
            {
                if (reader != null)
                    reader.close();
            }

        }

        @Test
        public void testMosaikDeletingIntermediates() throws Exception
        {
            if (!isExternalPipelineEnabled())
                return;

            String protocolName = "MosaikDeletingIntermediates_" + System.currentTimeMillis();
            String[] fileNames = getFilenamesForReadsets();
            JSONObject config = substituteParams(new File(_sampleData, ALIGNMENT_JOB), protocolName, fileNames);
            config.put("aligner", "mosaik");
            config.put("deleteIntermediateFiles", true);
            appendSamples(config, _readsetModels);

            PipelineJob job = createPipelineJob(protocolName, ANALYSIS_TASKID, config.toString(), fileNames);
            waitForJob(job.getJobGUID());

            Set<File> expectedOutputs = new HashSet<>();
            File basedir = new File(_pipelineRoot, "sequenceAnalysis/" + protocolName);
            expectedOutputs.add(new File(basedir, "all.pipe.xar.xml"));
            expectedOutputs.add(new File(basedir, "all.log"));
            expectedOutputs.add(new File(basedir, "sequenceAnalysis.xml"));
            expectedOutputs.add(new File(basedir, "sequencePipeline.xml"));

            expectedOutputs.add(new File(basedir, "Shared"));
            expectedOutputs.add(new File(basedir, "Shared/Ref_DB.fasta"));
            expectedOutputs.add(new File(basedir, "Shared/Ref_DB.fasta.fai"));

            expectedOutputs.add(new File(basedir, "paired1"));
            expectedOutputs.add(new File(basedir, "paired1/Alignment"));
            expectedOutputs.add(new File(basedir, "paired1/Alignment/paired1.bam"));
            expectedOutputs.add(new File(basedir, "paired1/Alignment/paired1.bam.bai"));

            expectedOutputs.add(new File(basedir, "paired3"));
            expectedOutputs.add(new File(basedir, "paired3/Alignment"));
            expectedOutputs.add(new File(basedir, "paired3/Alignment/paired3.bam"));
            expectedOutputs.add(new File(basedir, "paired3/Alignment/paired3.bam.bai"));

            expectedOutputs.add(new File(basedir, "paired4"));
            expectedOutputs.add(new File(basedir, "paired4/Alignment"));
            expectedOutputs.add(new File(basedir, "paired4/Alignment/paired4.bam"));
            expectedOutputs.add(new File(basedir, "paired4/Alignment/paired4.bam.bai"));

            validateInputs();
            verifyFileOutputs(basedir, expectedOutputs);
        }

        @Test
        public void testLastz() throws Exception
        {
            if (!isExternalPipelineEnabled())
                return;

            String protocolName = "TestLastz_" + System.currentTimeMillis();
            String[] fileNames = getFilenamesForReadsets();
            JSONObject config = substituteParams(new File(_sampleData, ALIGNMENT_JOB), protocolName, fileNames);
            config.put("aligner", "lastz");
            appendSamples(config, _readsetModels);

            PipelineJob job = createPipelineJob(protocolName, ANALYSIS_TASKID, config.toString(), fileNames);
            waitForJob(job.getJobGUID());

            Set<File> expectedOutputs = new HashSet<>();
            File basedir = new File(_pipelineRoot, "sequenceAnalysis/" + protocolName);
            expectedOutputs.add(new File(basedir, "all.pipe.xar.xml"));
            expectedOutputs.add(new File(basedir, "all.log"));
            expectedOutputs.add(new File(basedir, "sequenceAnalysis.xml"));
            expectedOutputs.add(new File(basedir, "sequencePipeline.xml"));

            expectedOutputs.add(new File(basedir, "Shared"));
            expectedOutputs.add(new File(basedir, "Shared/Ref_DB.fasta"));
            expectedOutputs.add(new File(basedir, "Shared/Ref_DB.fasta.fai"));

            expectedOutputs.add(new File(basedir, PAIRED_FILENAME1));
            expectedOutputs.add(new File(basedir, PAIRED_FILENAME2));

            expectedOutputs.add(new File(basedir, "paired1"));
            expectedOutputs.add(new File(basedir, "paired1/Alignment"));
            File bam1 = new File(basedir, "paired1/Alignment/paired1.bam");
            expectedOutputs.add(bam1);
            expectedOutputs.add(new File(basedir, "paired1/Alignment/paired1.bam.bai"));

            expectedOutputs.add(new File(basedir, "paired3"));
            expectedOutputs.add(new File(basedir, "paired3/Alignment"));
            File bam2 = new File(basedir, "paired3/Alignment/paired3.bam");
            expectedOutputs.add(bam2);
            expectedOutputs.add(new File(basedir, "paired3/Alignment/paired3.bam.bai"));

            expectedOutputs.add(new File(basedir, "paired4"));
            expectedOutputs.add(new File(basedir, "paired4/Alignment"));
            File bam3 = new File(basedir, "paired4/Alignment/paired4.bam");
            expectedOutputs.add(bam3);
            expectedOutputs.add(new File(basedir, "paired4/Alignment/paired4.bam.bai"));

            validateInputs();
            verifyFileOutputs(basedir, expectedOutputs);
            validateAlignment(bam1, 354, 0);
            validateAlignment(bam2, 177, 0);
            validateAlignment(bam3, 177, 0);
        }

        @Test
        public void testLastzWithPreprocessing() throws Exception
        {
            if (!isExternalPipelineEnabled())
                return;

            String protocolName = "LastzWithPreprocess_" + System.currentTimeMillis();
            String[] fileNames = getFilenamesForReadsets();
            JSONObject config = substituteParams(new File(_sampleData, ALIGNMENT_JOB), protocolName, fileNames);
            config.put("aligner", "lastz");
            config.put("preprocessing.crop", true);
            config.put("preprocessing.crop_cropLength", 250);
            config.put("preprocessing.crop_headcropLength", 1);
            config.put("preprocessing.downsample", true);
            config.put("preprocessing.downsampleReadNumber", 200);
            config.put("preprocessing.minLength", 100);
            config.put("preprocessing.palindromeClipThreshold", 30);
            config.put("preprocessing.qual2", true);
            config.put("preprocessing.qual2_avgQual", 15);
            config.put("preprocessing.qual2_windowSize", 4);
            config.put("preprocessing.seedMismatches", 2);
            config.put("preprocessing.simpleClipThreshold", 12);
            config.put("preprocessing.trimAdapters", true);

            appendSamples(config, _readsetModels);

            PipelineJob job = createPipelineJob(protocolName, ANALYSIS_TASKID, config.toString(), fileNames);
            waitForJob(job.getJobGUID());

            Set<File> expectedOutputs = new HashSet<>();
            File basedir = new File(_pipelineRoot, "sequenceAnalysis/" + protocolName);
            expectedOutputs.add(new File(basedir, "all.pipe.xar.xml"));
            expectedOutputs.add(new File(basedir, "all.log"));
            expectedOutputs.add(new File(basedir, "sequenceAnalysis.xml"));
            expectedOutputs.add(new File(basedir, "sequencePipeline.xml"));

            expectedOutputs.add(new File(basedir, "Shared"));
            expectedOutputs.add(new File(basedir, "Shared/Ref_DB.fasta"));
            expectedOutputs.add(new File(basedir, "Shared/Ref_DB.fasta.fai"));

            expectedOutputs.add(new File(basedir, "paired1"));
            expectedOutputs.add(new File(basedir, "paired1/Alignment"));
            File bam1 = new File(basedir, "paired1/Alignment/paired1.bam");
            expectedOutputs.add(bam1);
            expectedOutputs.add(new File(basedir, "paired1/Alignment/paired1.bam.bai"));

            expectedOutputs.add(new File(basedir, "paired3"));
            expectedOutputs.add(new File(basedir, "paired3/Alignment"));
            File bam2 = new File(basedir, "paired3/Alignment/paired3.bam");
            expectedOutputs.add(bam2);
            expectedOutputs.add(new File(basedir, "paired3/Alignment/paired3.bam.bai"));

            expectedOutputs.add(new File(basedir, "paired4"));
            expectedOutputs.add(new File(basedir, "paired4/Alignment"));
            File bam3 = new File(basedir, "paired4/Alignment/paired4.bam");
            expectedOutputs.add(bam3);
            expectedOutputs.add(new File(basedir, "paired4/Alignment/paired4.bam.bai"));

            expectedOutputs.add(new File(basedir, "Preprocessing"));
            expectedOutputs.add(new File(basedir, "Preprocessing/paired1/"));
            expectedOutputs.add(new File(basedir, "Preprocessing/paired1/" + PAIRED_FILENAME1));
            expectedOutputs.add(new File(basedir, "Preprocessing/paired1/" + PAIRED_FILENAME2));

            expectedOutputs.add(new File(basedir, "Preprocessing/paired3"));
            expectedOutputs.add(new File(basedir, "Preprocessing/paired3/" + UNZIPPED_PAIRED_FILENAME1 + ".gz"));

            expectedOutputs.add(new File(basedir, "Preprocessing/paired4/"));
            expectedOutputs.add(new File(basedir, "Preprocessing/paired4/" + UNZIPPED_PAIRED_FILENAME2 + ".gz"));

            validateInputs();
            verifyFileOutputs(basedir, expectedOutputs);
            validateAlignment(bam1, 278, 0);
            validateAlignment(bam2, 139, 0);
            validateAlignment(bam3, 139, 0);
        }

        @Test
        public void testLastzWithPreprocessingAndDeleteIntermediates() throws Exception
        {
            if (!isExternalPipelineEnabled())
                return;

            String protocolName = "LastzPreprocessAndDelete_" + System.currentTimeMillis();
            String[] fileNames = getFilenamesForReadsets();
            JSONObject config = substituteParams(new File(_sampleData, ALIGNMENT_JOB), protocolName, fileNames);
            config.put("aligner", "lastz");
            config.put("deleteIntermediateFiles", true);

            config.put("preprocessing.crop", true);
            config.put("preprocessing.crop_cropLength", 250);
            config.put("preprocessing.crop_headcropLength", 1);
            config.put("preprocessing.downsample", true);
            config.put("preprocessing.downsampleReadNumber", 200);
            config.put("preprocessing.minLength", 100);
            config.put("preprocessing.palindromeClipThreshold", 30);
            config.put("preprocessing.qual2", true);
            config.put("preprocessing.qual2_avgQual", 15);
            config.put("preprocessing.qual2_windowSize", 4);
            config.put("preprocessing.seedMismatches", 2);
            config.put("preprocessing.simpleClipThreshold", 12);
            config.put("preprocessing.trimAdapters", true);

            appendSamples(config, _readsetModels);

            PipelineJob job = createPipelineJob(protocolName, ANALYSIS_TASKID, config.toString(), fileNames);
            waitForJob(job.getJobGUID());

            Set<File> expectedOutputs = new HashSet<>();
            File basedir = new File(_pipelineRoot, "sequenceAnalysis/" + protocolName);
            expectedOutputs.add(new File(basedir, "all.pipe.xar.xml"));
            expectedOutputs.add(new File(basedir, "all.log"));
            expectedOutputs.add(new File(basedir, "sequenceAnalysis.xml"));
            expectedOutputs.add(new File(basedir, "sequencePipeline.xml"));

            expectedOutputs.add(new File(basedir, "Shared"));
            expectedOutputs.add(new File(basedir, "Shared/Ref_DB.fasta"));
            expectedOutputs.add(new File(basedir, "Shared/Ref_DB.fasta.fai"));

            expectedOutputs.add(new File(basedir, "paired1"));
            expectedOutputs.add(new File(basedir, "paired1/Alignment"));
            File bam1 = new File(basedir, "paired1/Alignment/paired1.bam");
            expectedOutputs.add(bam1);
            expectedOutputs.add(new File(basedir, "paired1/Alignment/paired1.bam.bai"));

            expectedOutputs.add(new File(basedir, "paired3"));
            expectedOutputs.add(new File(basedir, "paired3/Alignment"));
            File bam2 = new File(basedir, "paired3/Alignment/paired3.bam");
            expectedOutputs.add(bam2);
            expectedOutputs.add(new File(basedir, "paired3/Alignment/paired3.bam.bai"));

            expectedOutputs.add(new File(basedir, "paired4"));
            expectedOutputs.add(new File(basedir, "paired4/Alignment"));
            File bam3 = new File(basedir, "paired4/Alignment/paired4.bam");
            expectedOutputs.add(bam3);
            expectedOutputs.add(new File(basedir, "paired4/Alignment/paired4.bam.bai"));

            expectedOutputs.add(new File(basedir, "Preprocessing"));

            validateInputs();
            verifyFileOutputs(basedir, expectedOutputs);
            validateAlignment(bam1, 278, 0);
            validateAlignment(bam2, 139, 0);
            validateAlignment(bam3, 139, 0);
        }

        @Test
        public void testBWASW() throws Exception
        {
            if (!isExternalPipelineEnabled())
                return;

            String protocolName = "TestBWASW_" + System.currentTimeMillis();
            String[] fileNames = getFilenamesForReadsets();
            JSONObject config = substituteParams(new File(_sampleData, ALIGNMENT_JOB), protocolName, fileNames);
            config.put("aligner", "bwasw");
            appendSamples(config, _readsetModels);

            PipelineJob job = createPipelineJob(protocolName, ANALYSIS_TASKID, config.toString(), fileNames);
            waitForJob(job.getJobGUID());

            Set<File> expectedOutputs = new HashSet<>();
            File basedir = new File(_pipelineRoot, "sequenceAnalysis/" + protocolName);
            expectedOutputs.add(new File(basedir, "all.pipe.xar.xml"));
            expectedOutputs.add(new File(basedir, "all.log"));
            expectedOutputs.add(new File(basedir, "sequenceAnalysis.xml"));
            expectedOutputs.add(new File(basedir, "sequencePipeline.xml"));

            expectedOutputs.add(new File(basedir, "Shared"));
            expectedOutputs.add(new File(basedir, "Shared/Ref_DB.fasta"));
            expectedOutputs.add(new File(basedir, "Shared/Ref_DB.fasta.fai"));
            expectedOutputs.add(new File(basedir, "Shared/Ref_DB.bwa.index.amb"));
            expectedOutputs.add(new File(basedir, "Shared/Ref_DB.bwa.index.ann"));
            expectedOutputs.add(new File(basedir, "Shared/Ref_DB.bwa.index.bwt"));
            expectedOutputs.add(new File(basedir, "Shared/Ref_DB.bwa.index.pac"));
            expectedOutputs.add(new File(basedir, "Shared/Ref_DB.bwa.index.sa"));

            expectedOutputs.add(new File(basedir, PAIRED_FILENAME1));
            expectedOutputs.add(new File(basedir, PAIRED_FILENAME2));

            expectedOutputs.add(new File(basedir, "paired1"));
            expectedOutputs.add(new File(basedir, "paired1/Alignment"));
            File bam1 = new File(basedir, "paired1/Alignment/paired1.bam");
            expectedOutputs.add(bam1);
            expectedOutputs.add(new File(basedir, "paired1/Alignment/paired1.bam.bai"));

            expectedOutputs.add(new File(basedir, "paired3"));
            expectedOutputs.add(new File(basedir, "paired3/Alignment"));
            File bam2 = new File(basedir, "paired3/Alignment/paired3.bam");
            expectedOutputs.add(bam2);
            expectedOutputs.add(new File(basedir, "paired3/Alignment/paired3.bam.bai"));

            expectedOutputs.add(new File(basedir, "paired4"));
            expectedOutputs.add(new File(basedir, "paired4/Alignment"));
            File bam3 = new File(basedir, "paired4/Alignment/paired4.bam");
            expectedOutputs.add(bam3);
            expectedOutputs.add(new File(basedir, "paired4/Alignment/paired4.bam.bai"));

            validateInputs();
            verifyFileOutputs(basedir, expectedOutputs);
            validateAlignment(bam1, 400, 12);
            validateAlignment(bam2, 200, 6);
            validateAlignment(bam3, 200, 6);
        }

        @Test
        public void testBWAWithAdapters() throws Exception
        {
            if (!isExternalPipelineEnabled())
                return;

            //	"adapter_0":"[\"Nextera Transposon Adapter A\",\"AGATGTGTATAAGAGACAG\",true,true,false]",
        }

        @Test
        public void testBWA() throws Exception
        {
            if (!isExternalPipelineEnabled())
                return;

            String protocolName = "TestBWA_" + System.currentTimeMillis();
            String[] fileNames = getFilenamesForReadsets();
            JSONObject config = substituteParams(new File(_sampleData, ALIGNMENT_JOB), protocolName, fileNames);
            config.put("aligner", "bwa");
            appendSamples(config, _readsetModels);

            PipelineJob job = createPipelineJob(protocolName, ANALYSIS_TASKID, config.toString(), fileNames);
            waitForJob(job.getJobGUID());

            Set<File> expectedOutputs = new HashSet<>();
            File basedir = new File(_pipelineRoot, "sequenceAnalysis/" + protocolName);
            expectedOutputs.add(new File(basedir, "all.pipe.xar.xml"));
            expectedOutputs.add(new File(basedir, "all.log"));
            expectedOutputs.add(new File(basedir, "sequenceAnalysis.xml"));
            expectedOutputs.add(new File(basedir, "sequencePipeline.xml"));

            expectedOutputs.add(new File(basedir, "Shared"));
            expectedOutputs.add(new File(basedir, "Shared/Ref_DB.fasta"));
            expectedOutputs.add(new File(basedir, "Shared/Ref_DB.fasta.fai"));
            expectedOutputs.add(new File(basedir, "Shared/Ref_DB.bwa.index.amb"));
            expectedOutputs.add(new File(basedir, "Shared/Ref_DB.bwa.index.ann"));
            expectedOutputs.add(new File(basedir, "Shared/Ref_DB.bwa.index.bwt"));
            expectedOutputs.add(new File(basedir, "Shared/Ref_DB.bwa.index.pac"));
            expectedOutputs.add(new File(basedir, "Shared/Ref_DB.bwa.index.sa"));

            expectedOutputs.add(new File(basedir, PAIRED_FILENAME1));
            expectedOutputs.add(new File(basedir, PAIRED_FILENAME2));

            expectedOutputs.add(new File(basedir, "paired1"));
            expectedOutputs.add(new File(basedir, "paired1/Alignment"));
            File bam1 = new File(basedir, "paired1/Alignment/paired1.bam");
            expectedOutputs.add(bam1);
            expectedOutputs.add(new File(basedir, "paired1/Alignment/paired1.bam.bai"));

            expectedOutputs.add(new File(basedir, "paired3"));
            expectedOutputs.add(new File(basedir, "paired3/Alignment"));
            File bam2 = new File(basedir, "paired3/Alignment/paired3.bam");
            expectedOutputs.add(bam2);
            expectedOutputs.add(new File(basedir, "paired3/Alignment/paired3.bam.bai"));

            expectedOutputs.add(new File(basedir, "paired4"));
            expectedOutputs.add(new File(basedir, "paired4/Alignment"));
            File bam3 = new File(basedir, "paired4/Alignment/paired4.bam");
            expectedOutputs.add(bam3);
            expectedOutputs.add(new File(basedir, "paired4/Alignment/paired4.bam.bai"));

            validateInputs();
            verifyFileOutputs(basedir, expectedOutputs);
            //TODO: this is fishy
            validateAlignment(bam1, 0, 408);
            validateAlignment(bam2, 0, 204);
            validateAlignment(bam3, 0, 204);
        }

        @Test
        public void testBowtie() throws Exception
        {
            if (!isExternalPipelineEnabled())
                return;

            String protocolName = "TestBowtie_" + System.currentTimeMillis();
            String[] fileNames = getFilenamesForReadsets();
            JSONObject config = substituteParams(new File(_sampleData, ALIGNMENT_JOB), protocolName, fileNames);
            config.put("aligner", "bowtie");
            appendSamples(config, _readsetModels);

            PipelineJob job = createPipelineJob(protocolName, ANALYSIS_TASKID, config.toString(), fileNames);
            waitForJob(job.getJobGUID());

            Set<File> expectedOutputs = new HashSet<>();
            File basedir = new File(_pipelineRoot, "sequenceAnalysis/" + protocolName);
            expectedOutputs.add(new File(basedir, "all.pipe.xar.xml"));
            expectedOutputs.add(new File(basedir, "all.log"));
            expectedOutputs.add(new File(basedir, "sequenceAnalysis.xml"));
            expectedOutputs.add(new File(basedir, "sequencePipeline.xml"));

            expectedOutputs.add(new File(basedir, "Shared"));
            expectedOutputs.add(new File(basedir, "Shared/Ref_DB.fasta"));
            expectedOutputs.add(new File(basedir, "Shared/Ref_DB.fasta.fai"));
            expectedOutputs.add(new File(basedir, "Shared/Ref_DB.bowtie.index.1.ebwt"));
            expectedOutputs.add(new File(basedir, "Shared/Ref_DB.bowtie.index.2.ebwt"));
            expectedOutputs.add(new File(basedir, "Shared/Ref_DB.bowtie.index.3.ebwt"));
            expectedOutputs.add(new File(basedir, "Shared/Ref_DB.bowtie.index.4.ebwt"));
            expectedOutputs.add(new File(basedir, "Shared/Ref_DB.bowtie.index.rev.1.ebwt"));
            expectedOutputs.add(new File(basedir, "Shared/Ref_DB.bowtie.index.rev.2.ebwt"));

            expectedOutputs.add(new File(basedir, PAIRED_FILENAME1));
            expectedOutputs.add(new File(basedir, PAIRED_FILENAME2));

            expectedOutputs.add(new File(basedir, "paired1"));
            expectedOutputs.add(new File(basedir, "paired1/Alignment"));
            File bam1 = new File(basedir, "paired1/Alignment/paired1.bam");
            expectedOutputs.add(bam1);
            expectedOutputs.add(new File(basedir, "paired1/Alignment/paired1.bam.bai"));
            expectedOutputs.add(new File(basedir, "paired1/Alignment/paired1.bowtie.unaligned.fastq"));

            expectedOutputs.add(new File(basedir, "paired3"));
            expectedOutputs.add(new File(basedir, "paired3/Alignment"));
            File bam2 = new File(basedir, "paired3/Alignment/paired3.bam");
            expectedOutputs.add(bam2);
            expectedOutputs.add(new File(basedir, "paired3/Alignment/paired3.bam.bai"));
            expectedOutputs.add(new File(basedir, "paired3/Alignment/paired3.bowtie.unaligned.fastq"));

            expectedOutputs.add(new File(basedir, "paired4"));
            expectedOutputs.add(new File(basedir, "paired4/Alignment"));
            File bam3 = new File(basedir, "paired4/Alignment/paired4.bam");
            expectedOutputs.add(bam3);
            expectedOutputs.add(new File(basedir, "paired4/Alignment/paired4.bam.bai"));
            expectedOutputs.add(new File(basedir, "paired4/Alignment/paired4.bowtie.unaligned.fastq"));

            validateInputs();
            //TODO: this is fishy
            verifyFileOutputs(basedir, expectedOutputs);
            validateAlignment(bam1, 0, 204);
            validateAlignment(bam2, 0, 204);
            validateAlignment(bam3, 0, 204);
        }

        @Test
        public void testBowtieDeletingIntermediates() throws Exception
        {
            if (!isExternalPipelineEnabled())
                return;

            String protocolName = "TestBowtieDeleting_" + System.currentTimeMillis();
            String[] fileNames = getFilenamesForReadsets();
            JSONObject config = substituteParams(new File(_sampleData, ALIGNMENT_JOB), protocolName, fileNames);
            config.put("aligner", "bowtie");
            config.put("deleteIntermediateFiles", true);
            appendSamples(config, _readsetModels);

            PipelineJob job = createPipelineJob(protocolName, ANALYSIS_TASKID, config.toString(), fileNames);
            waitForJob(job.getJobGUID());

            Set<File> expectedOutputs = new HashSet<>();
            File basedir = new File(_pipelineRoot, "sequenceAnalysis/" + protocolName);
            expectedOutputs.add(new File(basedir, "all.pipe.xar.xml"));
            expectedOutputs.add(new File(basedir, "all.log"));
            expectedOutputs.add(new File(basedir, "sequenceAnalysis.xml"));
            expectedOutputs.add(new File(basedir, "sequencePipeline.xml"));

            expectedOutputs.add(new File(basedir, "Shared"));
            expectedOutputs.add(new File(basedir, "Shared/Ref_DB.fasta"));
            expectedOutputs.add(new File(basedir, "Shared/Ref_DB.fasta.fai"));

            expectedOutputs.add(new File(basedir, "paired1"));
            expectedOutputs.add(new File(basedir, "paired1/Alignment"));
            File bam1 = new File(basedir, "paired1/Alignment/paired1.bam");
            expectedOutputs.add(bam1);
            expectedOutputs.add(new File(basedir, "paired1/Alignment/paired1.bam.bai"));
            expectedOutputs.add(new File(basedir, "paired1/Alignment/paired1.bowtie.unaligned.fastq"));

            expectedOutputs.add(new File(basedir, "paired3"));
            expectedOutputs.add(new File(basedir, "paired3/Alignment"));
            File bam2 = new File(basedir, "paired3/Alignment/paired3.bam");
            expectedOutputs.add(bam2);
            expectedOutputs.add(new File(basedir, "paired3/Alignment/paired3.bam.bai"));
            expectedOutputs.add(new File(basedir, "paired3/Alignment/paired3.bowtie.unaligned.fastq"));

            expectedOutputs.add(new File(basedir, "paired4"));
            expectedOutputs.add(new File(basedir, "paired4/Alignment"));
            File bam3 = new File(basedir, "paired4/Alignment/paired4.bam");
            expectedOutputs.add(bam3);
            expectedOutputs.add(new File(basedir, "paired4/Alignment/paired4.bam.bai"));
            expectedOutputs.add(new File(basedir, "paired4/Alignment/paired4.bowtie.unaligned.fastq"));

            validateInputs();
            verifyFileOutputs(basedir, expectedOutputs);
        }

        //NOTE: this test is deliberately disabled
        private void testBfast() throws Exception
        {
            String protocolName = "TestBfast_" + System.currentTimeMillis();
            String[] fileNames = getFilenamesForReadsets();
            JSONObject config = substituteParams(new File(_sampleData, ALIGNMENT_JOB), protocolName, fileNames);
            config.put("aligner", "bfast");
            appendSamples(config, _readsetModels);

            PipelineJob job = createPipelineJob(protocolName, ANALYSIS_TASKID, config.toString(), fileNames);
            waitForJob(job.getJobGUID());

            Set<File> expectedOutputs = new HashSet<>();
            File basedir = new File(_pipelineRoot, "sequenceAnalysis/" + protocolName);
            expectedOutputs.add(new File(basedir, "all.pipe.xar.xml"));
            expectedOutputs.add(new File(basedir, "all.log"));
            expectedOutputs.add(new File(basedir, "sequenceAnalysis.xml"));
            expectedOutputs.add(new File(basedir, "sequencePipeline.xml"));

            expectedOutputs.add(new File(basedir, "Shared"));
            expectedOutputs.add(new File(basedir, "Shared/Ref_DB.fasta"));
            expectedOutputs.add(new File(basedir, "Shared/Ref_DB.fasta.fai"));
            expectedOutputs.add(new File(basedir, "Shared/Ref_DB.mosaik"));

            expectedOutputs.add(new File(basedir, PAIRED_FILENAME1));
            expectedOutputs.add(new File(basedir, PAIRED_FILENAME2));

            expectedOutputs.add(new File(basedir, "paired1"));
            expectedOutputs.add(new File(basedir, "paired1/Alignment"));
            File bam1 = new File(basedir, "paired1/Alignment/paired1.bam");
            expectedOutputs.add(bam1);
            expectedOutputs.add(new File(basedir, "paired1/Alignment/paired1.bam.bai"));

            expectedOutputs.add(new File(basedir, "paired3"));
            expectedOutputs.add(new File(basedir, "paired3/Alignment"));
            File bam2 = new File(basedir, "paired3/Alignment/paired3.bam");
            expectedOutputs.add(bam2);
            expectedOutputs.add(new File(basedir, "paired3/Alignment/paired3.bam.bai"));

            expectedOutputs.add(new File(basedir, "paired4"));
            expectedOutputs.add(new File(basedir, "paired4/Alignment"));
            File bam3 = new File(basedir, "paired4/Alignment/paired4.bam");
            expectedOutputs.add(bam3);
            expectedOutputs.add(new File(basedir, "paired4/Alignment/paired4.bam.bai"));

            validateInputs();
            verifyFileOutputs(basedir, expectedOutputs);
            validateAlignment(bam1, 40, 0);
            validateAlignment(bam2, 20, 0);
            validateAlignment(bam3, 20, 0);
        }
    }
}
