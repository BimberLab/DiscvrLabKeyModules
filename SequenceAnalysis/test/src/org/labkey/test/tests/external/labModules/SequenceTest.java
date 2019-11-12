/*
 * Copyright (c) 2012-2016 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.test.tests.external.labModules;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.remoteapi.CommandException;
import org.labkey.remoteapi.Connection;
import org.labkey.remoteapi.query.Filter;
import org.labkey.remoteapi.query.InsertRowsCommand;
import org.labkey.remoteapi.query.SelectRowsCommand;
import org.labkey.remoteapi.query.SelectRowsResponse;
import org.labkey.remoteapi.query.Sort;
import org.labkey.serverapi.reader.Readers;
import org.labkey.serverapi.writer.PrintWriters;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.TestFileUtils;
import org.labkey.test.TestTimeoutException;
import org.labkey.test.WebTestHelper;
import org.labkey.test.categories.External;
import org.labkey.test.categories.LabModule;
import org.labkey.test.util.DataRegionTable;
import org.labkey.test.util.Ext4Helper;
import org.labkey.test.util.PasswordUtil;
import org.labkey.test.util.SimpleHttpResponse;
import org.labkey.test.util.TextSearcher;
import org.labkey.test.util.ext4cmp.Ext4CmpRef;
import org.labkey.test.util.ext4cmp.Ext4ComboRef;
import org.labkey.test.util.ext4cmp.Ext4FieldRef;
import org.labkey.test.util.ext4cmp.Ext4FileFieldRef;
import org.labkey.test.util.ext4cmp.Ext4GridRef;
import org.labkey.test.util.external.labModules.LabModuleHelper;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.zip.GZIPInputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@Category({External.class, LabModule.class})
public class SequenceTest extends BaseWebDriverTest
{
    protected LabModuleHelper _helper = new LabModuleHelper(this);
    protected String _pipelineRoot = null;
    public static final File _sampleData = new File(TestFileUtils.getLabKeyRoot(), "server/modules/DiscvrLabKeyModules/SequenceAnalysis/resources/sampleData");
    protected final String _readsetPipelineName = "Import sequence data";
    protected final String _alignmentImportPipelineName = "Import Alignment(s)";

    private final String TEMPLATE_NAME = "SequenceTest Saved Template";
    private Integer _readsetCt = 0;
    private int _startedPipelineJobs = 0;
    private final String ILLUMINA_CSV = "SequenceImport.csv";

    @Override
    protected String getProjectName()
    {
        return "SequenceVerifyProject";// + TRICKY_CHARACTERS_FOR_PROJECT_NAMES;
    }

    private Integer _createdGenomeId = null;

    private boolean isExternalPipelineEnabled()
    {
        SimpleHttpResponse httpResponse = WebTestHelper.getHttpResponse(getBaseURL() + "/sequenceanalysis/" + getProjectName() + "/getSequencePipelineEnabled.view");
        Boolean sequencePipelineEnabled;
        try
        {
            JSONObject json = (JSONObject) new JSONParser().parse(httpResponse.getResponseBody());
            sequencePipelineEnabled = (Boolean) json.get("isPipelineEnabled");
        }
        catch (ParseException | ClassCastException e)
        {
            sequencePipelineEnabled = false;
        }
        log("sequencePipelineEnabled: " + sequencePipelineEnabled);

        goToProjectHome();

        return sequencePipelineEnabled;
    }

    @Test
    public void testSteps() throws Exception
    {
        setUpTest();

        if (_createdGenomeId == null)
        {
            _createdGenomeId = createReferenceGenome(this, _startedPipelineJobs);
            _startedPipelineJobs++;
        }

        importReadsetMetadata();
        createIlluminaSampleSheet();
        importIlluminaTest();
        readsetFeaturesTest();
        analysisPanelTest();
        readsetPanelTest();

        boolean sequencePipelineEnabled = isExternalPipelineEnabled();
        alignmentImportPanelTest(sequencePipelineEnabled);
        readsetImportTest();

        //will also verify UI + pipeline jobs
        _startedPipelineJobs = addReferenceGenomeTracks(this, getProjectName(), TEST_GENOME_NAME, _createdGenomeId, _startedPipelineJobs);

        basicOutputFilesTest(sequencePipelineEnabled);
    }

    protected void setUpTest()
    {
        _containerHelper.createProject(getProjectName(), "Sequence Analysis");

        // populate initial data.  these rows should already be present.
        // NOTE: it should work to create all these sequences in the current folder, rather than shared
        // it wont exercise the UserSchema, but will make cleanup easier
        // _containerHelper.enableModule("Shared", "SequenceAnalysis");
        beginAt(getBaseURL() + "/sequenceanalysis/" + getProjectName() + "/populateSequences.view");

        waitAndClick(Ext4Helper.Locators.ext4Button("Populate Viral Sequences"));
        waitForElement(Locator.tagContainingText("div", "Populate Complete"), 200000);

        goToProjectHome();
    }


    /**
     * This method is designed to import an initial set of readset records, which will be used for
     * illumina import
     */
    private void importReadsetMetadata()
    {
        //create readset records for illumina run
        goToProjectHome();
        waitAndClick(Locator.linkWithText("Plan Sequence Run (Create Readsets)"));
        waitForElement(Ext4Helper.Locators.window("Create Readsets"));
        waitAndClickAndWait(Ext4Helper.Locators.ext4Button("Submit"));

        _helper.waitForField("Sample Id", WAIT_FOR_PAGE);
        _ext4Helper.clickTabContainingText("Import Spreadsheet");
        waitForText("Copy/Paste Data");

        setFormElementJS(Locator.name("text"), getIlluminaNames());

        waitAndClick(Ext4Helper.Locators.ext4Button("Upload"));
        waitForElement(Ext4Helper.Locators.window("Success"));
        _readsetCt += 14;
        assertTextPresent("Success!");
        waitAndClickAndWait(Ext4Helper.Locators.ext4Button("OK"));

        log("verifying readset count correct");
        waitForText("Sequence Readsets");
        waitForElement(LabModuleHelper.getNavPanelItem("Sequence Readsets:", _readsetCt.toString()));
    }

    /**
     * This method is designed to exercise the illumina template UI and create a CSV import template.  This template
     * is later used by importIlluminaTest()
     */
    private void createIlluminaSampleSheet()
    {
        goToProjectHome();
        _helper.clickNavPanelItemAndWait("Readsets:", 1);
        DataRegionTable.DataRegion(getDriver()).withName("query").waitFor();

        //verify CSV file creation
        DataRegionTable.DataRegion(getDriver()).find().goToView( "All");
        DataRegionTable dr = new DataRegionTable("query", this);
        dr.checkAllOnPage();
        dr.clickHeaderMenu("More Actions", "Create Illumina Sample Sheet");

        waitForText("You have chosen to export " + _readsetCt + " samples");
        _helper.waitForField("Investigator Name");

        String url = getCurrentRelativeURL();
        beginAt(url);
        waitForText("You have chosen to export " + _readsetCt + " samples");
        _helper.waitForField("Investigator Name");

        Ext4FieldRef.getForLabel(this, "Reagent Cassette Id").setValue("FlowCell");

        String[][] fieldPairs = {
                {"Investigator Name", "Investigator"},
                {"Experiment Name", "Experiment"},
                {"Project Name", "Project"},
                {"Description", "Description"},
                {"Application", "FASTQ Only"},
                {"Sample Kit", "Nextera XT", "Assay"},
        };

        for (String[] a : fieldPairs)
        {
            Ext4FieldRef.waitForField(this, a[0]);
            Ext4FieldRef field = Ext4FieldRef.getForLabel(this, a[0]);
            field.waitForEnabled();
            field.setValue(a[1]);
            assertNotNull("Field not set", Ext4FieldRef.getForLabel(this, a[0]).getValue());
        }

        _ext4Helper.clickTabContainingText("Preview Header");
        waitForText("Edit Sheet");
        for (String[] a : fieldPairs)
        {
            String propName = a.length == 3 ? a[2] : a[0];
            assertEquals(a[1], Ext4FieldRef.getForLabel(this, propName).getValue());
        }

        clickButton("Edit Sheet", 0);
        waitForText("Done Editing");
        for (String[] a : fieldPairs)
        {
            String propName = a.length == 3 ? a[2] : a[0];
            assertTextPresent(propName + "," + a[1]);
        }

        //add new values
        String prop_name = "NewProperty";
        String prop_value = "NewValue";
        Ext4FieldRef textarea = _ext4Helper.queryOne("textarea[itemId='sourceField']", Ext4FieldRef.class);
        String newValue = prop_name + "," + prop_value;
        String val = (String) textarea.getValue();
        val += "\n" + newValue;
        textarea.setValue(val);
        clickButton("Done Editing", 0);

        //verify template has changed
        _ext4Helper.clickTabContainingText("General Info");
        assertEquals("Custom", Ext4FieldRef.getForLabel(this, "Application").getValue());

        //verify samples present
        _ext4Helper.clickTabContainingText("Preview Samples");
        waitForText("Sample_ID");

        int expectRows = (11 * (14 + 1));  //11 cols, 14 rows, plus header
        assertEquals(expectRows, getElementCount(Locator.xpath("//td[contains(@class, 'x4-table-layout-cell')]")));

        //NOTE: hitting download will download the file.
        Ext4CmpRef panel = _ext4Helper.queryOne("#illuminaPanel", Ext4CmpRef.class);
        String outputTable = panel.getEval("getTableOutput().join(\"<>\")").toString();
        outputTable = outputTable.replaceAll("<>", System.getProperty("line.separator"));

        //download the file and validate the contents
        File downloadCSV = doAndWaitForDownload(() -> clickButton("Download For Instrument", 0));
        TextSearcher fileSearcher = new TextSearcher(downloadCSV);
        for (String[] a : fieldPairs)
        {
            String propName = a.length == 3 ? a[2] : a[0];
            String line = propName + "," + a[1];
            assertTextPresent(fileSearcher, line);
        }
        assertTextPresent(fileSearcher, prop_name + "," + prop_value);

        File fileRoot = TestFileUtils.getDefaultFileRoot(getProjectName());
        File importTemplate = new File(fileRoot, ILLUMINA_CSV);
        if (importTemplate.exists())
            importTemplate.delete();


        //NOTE: use the text generated directly using JS
        TestFileUtils.saveFile(importTemplate.getParentFile(), importTemplate.getName(), outputTable);
        goToProjectHome();
    }

    private String getIlluminaNames()
    {
        String[] barcodes5 = {"N701", "N702", "N703", "N704", "N705", "N706", "N701", "N702", "N701", "N702", "N703", "N704", "N705", "N706"};
        String[] barcodes3 = {"N502", "N502", "N502", "N502", "N502", "N502", "N503", "N503", "N501", "N501", "N501", "N501", "N501", "N501"};

        StringBuilder sb = new StringBuilder("Name\tPlatform\tBarcode5\tBarcode3\n");
        int i = 0;
        while (i < barcodes5.length)
        {
            sb.append("Illumina" + (i + 1) + "\tILLUMINA\t" + barcodes5[i] + "\t" + barcodes3[i] + "\n");
            i++;
        }
        return sb.toString();
    }

    /**
     * This test will kick off a pipeline import using the illumina pipeline.  Verification of the result
     * is performed by readsetFeaturesTest()
     */
    private void importIlluminaTest() throws Exception
    {
        //copy files to local root
        File fileRoot = TestFileUtils.getDefaultFileRoot(getProjectName());
        for (String fn : Arrays.asList("Illumina-F.fastq.gz", "Illumina-R.fastq.gz", "SkipMe.fastq.gz"))
        {
            File target = new File(fileRoot, fn);
            if (target.exists())
            {
                target.delete();
            }

            File source = TestFileUtils.getSampleData("sequenceAnalysis/" + fn);
            FileUtils.copyFile(source, target);
        }

        _helper.initiatePipelineJob("Import Illumina data", Arrays.asList(ILLUMINA_CSV), getProjectName());

        setFormElement(Locator.name("jobName"), "TestIlluminaRun" + _helper.getRandomInt());
        setFormElement(Locator.name("runDate"), "08/25/2011");
        setFormElement(Locator.name("fastqPrefix"), "Il");

        waitAndClick(Ext4Helper.Locators.ext4ButtonEnabled("Import Data"));
        waitAndClickAndWait(Ext4Helper.Locators.ext4Button("OK"));

        waitAndClickAndWait(Locator.linkWithText("All"));
        _startedPipelineJobs++;
        waitForElement(Locator.tagContainingText("span", "Data Pipeline"));
        waitForPipelineJobsToComplete(_startedPipelineJobs, "Import Illumina", false);
        assertTextPresent("COMPLETE");
    }

    /**
     * This method has several puposes.  It will verify that the records from illuminaImportTest() were
     * created properly.  It also exercises various features associated with the readset grid, including
     * the FASTQC report and downloading of results
     *
     * @throws Exception
     */
    private void readsetFeaturesTest() throws IOException
    {
        //verify import and instrument run creation
        beginAt("/query/" + getProjectName() + "/executeQuery.view?schemaName=sequenceanalysis&query.queryName=readdata");

        DataRegionTable dr = new DataRegionTable("query", this);

        for (int i = 0; i < dr.getDataRowCount(); i++)
        {
            String rowId = dr.getDataAsText(i, "Readset Id");
            String file1 = dr.getDataAsText(i, "Input File");
            assertEquals("Incorrect or no filename associated with readset", "Illumina-R1-" + rowId + ".fastq.gz", file1);

            String file2 = dr.getDataAsText(i, "Input File2");
            assertEquals("Incorrect or no filename associated with readset", "Illumina-R2-" + rowId + ".fastq.gz", file2);
        }

        goToProjectHome();
        _helper.clickNavPanelItemAndWait("Readsets:", 1);

        dr = new DataRegionTable("query", this);

        log("Verifying instrument run and details page");
        clickAndWait(dr.link(2, "Instrument Run"));

        waitForText("Instrument Run Details");
        waitForText("Run Id"); //crude proxy for loading of the details panel
        waitForText("Readsets");
        DataRegionTable rs = DataRegionTable.findDataRegionWithinWebpart(this, "Readsets");
        assertEquals("Incorrect readset count found", 14, rs.getDataRowCount());

        waitForText("Quality Metrics");
        DataRegionTable qm = DataRegionTable.findDataRegionWithinWebpart(this, "Quality Metrics");
        assertEquals("Incorrect quality metric count found", 30, qm.getDataRowCount());
        String totalSequences = qm.getDataAsText(qm.getRow("File Id", "Illumina-R1-Control.fastq.gz"), "Metric Value");
        assertEquals("Incorrect value for total sequences", "9.0", totalSequences);

        log("Verifying readset details page");
        goToProjectHome();
        _helper.clickNavPanelItemAndWait("Readsets:", 1);
        dr = new DataRegionTable("query", this);
        dr.clickRowDetails(1);

        waitForText("Readset Details");
        waitForText("Readset Id:"); //crude proxy for details panel

        waitForText("Analyses Using This Readset");
        DataRegionTable dr1 = DataRegionTable.findDataRegionWithinWebpart(this, "Analyses Using This Readset");
        assertEquals("Incorrect analysis count", 0, dr1.getDataRowCount());

        waitForText("Quality Metrics");
        DataRegionTable dr2 = DataRegionTable.findDataRegionWithinWebpart(this, "Quality Metrics");
        assertEquals("Incorrect analysis count", 2, dr2.getDataRowCount());

        //verify export
        log("Verifying FASTQ Export");
        goToProjectHome();
        _helper.clickNavPanelItemAndWait("Readsets:", 1);
        dr = new DataRegionTable("query", this);
        dr.checkAllOnPage();
        dr.clickHeaderMenu("More Actions", false, "Download Sequence Files");
        waitForElement(Ext4Helper.Locators.window("Export Files"));
        waitForText("Export Files As");
        Ext4CmpRef window = _ext4Helper.queryOne("#exportFilesWin", Ext4CmpRef.class);
        String fileName = "MyFile";
        Ext4FieldRef.getForLabel(this, "File Prefix").setValue(fileName);
        String url = window.getEval("getURL()").toString();
        assertTrue("Improper URL to download sequences", url.contains("zipFileName=" + fileName));
        assertTrue("Improper URL to download sequences", url.contains("exportSequenceFiles.view?"));

        _ext4Helper.queryOne("field[boxLabel='Forward Reads']", Ext4FieldRef.class).setValue("false");
        _ext4Helper.queryOne("field[boxLabel='Merge into Single FASTQ File']", Ext4FieldRef.class).setChecked(true);

        url = window.getEval("getURL()").toString();
        assertTrue("Improper URL to download sequences", url.contains("mergeFastqFiles.view?"));

        File exportFile = clickAndWaitForDownload(Ext4Helper.Locators.ext4Button("Submit"));
        validateFastqDownload(exportFile);

        log("Verifying FASTQC Report");
        refresh();

        dr = new DataRegionTable.DataRegionFinder(this.getDriver()).withName("query").waitFor();
        dr.uncheckAllOnPage();

        assertEquals("Rows were already selected", 0, dr.getCheckedCount(this));

        dr.checkCheckbox(2);
        assertEquals("Incorrect number of rows selected", 1, dr.getCheckedCount(this));

        dr.clickHeaderMenu("More Actions", false, "View FASTQC Report");
        waitForElement(Ext4Helper.Locators.window("FastQC"));
        waitAndClickAndWait(Ext4Helper.Locators.ext4Button("OK"));

        waitForText("File Summary");
        assertTextPresent("Per base sequence quality");

        log("Verifying Readset Edit");
        goToProjectHome();
        _helper.clickNavPanelItemAndWait("Readsets:", 1);

        waitForText("Instrument Run"); //proxy for dataRegion loading
        dr.clickEditRow(1);

        waitForText("3-Barcode:");
        waitForText("Run:");
        String newName = "ChangedSample";
        Ext4FieldRef.getForLabel(this, "Name").setValue(newName);
        sleep(250); //wait for value to save
        clickButton("Submit", 0);
        waitForElement(Ext4Helper.Locators.window("Success"));
        assertTextPresent("Your upload was successful!");
        clickButton("OK");

        dr = new DataRegionTable("query", this);
        assertEquals("Changed sample name not applied", newName, dr.getDataAsText(1, "Name"));

        //note: 'Analyze Selected' option is verified separately
    }

    /**
     * The intent of this method is to test the Sequence Analysis Panel,
     * with the goal of exercising all UI options.  It directly calls getJsonParams() on the panel,
     * in order to inspect the JSON it would send to the server, but does not initiate a pipeline job.
     */
    private void analysisPanelTest() throws Exception
    {
        checkErrors(); //try to track down when error occurring
        log("Verifying Analysis Panel UI");

        goToProjectHome();
        _helper.clickNavPanelItemAndWait("Readsets:", 1);
        DataRegionTable dr = new DataRegionTable("query", this);
        dr.uncheckAllOnPage();
        assertEquals("incorrect number of rows selected", 0, dr.getCheckedCount(this));
        dr.checkCheckbox(2);
        List<String> rowIds = new ArrayList<>();
        rowIds.add(dr.getDataAsText(2, "Readset Id"));
        dr.checkCheckbox(6);
        rowIds.add(dr.getDataAsText(6, "Readset Id"));

        dr.clickHeaderMenu("More Actions", false, "Align/Analyze Selected");
        waitForElement(Ext4Helper.Locators.window("Import Data"));
        waitForText("Description");
        waitAndClickAndWait(Ext4Helper.Locators.ext4Button("Submit"));

        log("Verifying analysis UI");

        //setup local variables
        String minReadLength = "68";
        String qualWindowSize = "8";
        String qualAvgQual = "19";
        String jobName = "TestAnalysisJob";
        String analysisDescription = "This is the description for my analysis";
        String strain = "HXB2";
        String[][] rocheAdapters = {{"Roche-454 FLX Amplicon A", "GCCTCCCTCGCGCCATCAG"}, {"Roche-454 FLX Amplicon B", "GCCTTGCCAGCCCGCTCAG"}};

        waitForText("Readset Name");
        waitForElement(Ext4Helper.Locators.window("Copy Previous Run?"));
        waitAndClick(Ext4Helper.Locators.window("Copy Previous Run?").append(Ext4Helper.Locators.ext4Button("Cancel")));
        waitForElementToDisappear(Ext4Helper.Locators.window("Copy Previous Run?"));

        for (String rowId : rowIds)
        {
            assertTextPresent("Illumina-R1-" + rowId + ".fastq.gz", "Illumina-R2-" + rowId + ".fastq.gz");
        }

        Ext4FieldRef.getForLabel(this, "Job Name").setValue(jobName);
        Ext4FieldRef.getForLabel(this, "Description").setValue(analysisDescription);

        log("Verifying Pre-processing section");
        waitAndClick(Ext4Helper.Locators.ext4Button("Add Step").index(0));
        Locator.XPathLocator win = Ext4Helper.Locators.window("Add Steps");
        waitForElement(win);

        waitAndClick(Locator.id(_ext4Helper.queryOne("window ldk-linkbutton[text='View Description']", Ext4CmpRef.class).getId()).append(Locator.tag("a")));
        waitForElement(Ext4Helper.Locators.window("Tool Details"));
        waitAndClick(Ext4Helper.Locators.window("Tool Details").append(Ext4Helper.Locators.ext4Button("Done")));

        List<Ext4CmpRef> btns = _ext4Helper.componentQuery("window ldk-linkbutton[text='Add']", Ext4CmpRef.class);
        for (Ext4CmpRef btn : btns)
        {
            waitAndClick(Locator.id(btn.getId()).append(Locator.tag("a")));
        }

        waitAndClick(Ext4Helper.Locators.ext4Button("Done"));
        waitForElementToDisappear(Ext4Helper.Locators.window("Add Steps"));

        Map<String, Ext4CmpRef> fieldsetMap = new HashMap<>();
        String[] setNames = {"Adapter Trimming (Trimmomatic)", "Average Quality Filter", "Crop Reads", "Downsample Reads", "Head Crop", "Quality Trimming (Adaptive)", "Quality Trimming (Sliding Window)", "Read Length Filter"};
        isPresentInThisOrder(setNames);

        for (String name : setNames)
        {
            fieldsetMap.put(name, _ext4Helper.queryOne("fieldset[title='" + name + "']", Ext4CmpRef.class));
        }

        waitAndClick(Locator.id(fieldsetMap.get("Downsample Reads").down("ldk-linkbutton[text='Move Down']", Ext4CmpRef.class).getId()).append(Locator.tag("a")));
        isTextBefore("Head Crop", "Downsample Reads");

        waitAndClick(Locator.id(fieldsetMap.get("Head Crop").down("ldk-linkbutton[text='Remove']", Ext4CmpRef.class).getId()).append(Locator.tag("a")));
        waitForElementToDisappear(Locator.id(fieldsetMap.get("Head Crop").getId()));

        Integer overlapLength = 6;
        Double errorRate = 0.2;
        Integer cropLength = 500;
        Integer totalReadsVal = 100;

        //now set params
        Ext4FieldRef.getForLabel(this, "Overlap Length").setValue(overlapLength);
        Ext4FieldRef.getForLabel(this, "Error Rate").setValue(errorRate);
        Ext4FieldRef.getForLabel(this, "Min Length").setValue(minReadLength);
        Ext4FieldRef.getForLabel(this, "Crop Length").setValue(cropLength);
        Ext4FieldRef.getForLabel(this, "Total Reads").setValue(totalReadsVal);
        Ext4FieldRef.getForLabel(this, "Window Size").setValue(qualWindowSize);
        Ext4FieldRef.getForLabel(this, "Avg Qual").setValue(qualAvgQual);
        Ext4FieldRef.getForLabel(this, "Minimum Read Length").setValue(minReadLength);

        waitForText("Adapters");
        waitAndClick(Ext4Helper.Locators.ext4Button("Common Adapters").index(1));
        waitForElement(Ext4Helper.Locators.window("Choose Adapters"));
        waitForText("Choose Adapter Group");
        Ext4FieldRef.getForLabel(this, "Choose Adapter Group").setValue("Roche-454 FLX Amplicon");
        waitAndClick(Ext4Helper.Locators.ext4Button("Submit"));
        Ext4GridRef grid = _ext4Helper.componentQuery("sequenceanalysis-adapterpanel grid", Ext4GridRef.class).get(1);
        grid.setClicksToEdit(1);
        grid.waitForRowCount(2);
        Assert.assertEquals("Incorrect cell value", rocheAdapters[0][0], grid.getFieldValue(1, "adapterName"));
        Assert.assertEquals("Incorrect cell value", rocheAdapters[1][0], grid.getFieldValue(2, "adapterName"));
        Assert.assertEquals("Incorrect cell value", rocheAdapters[0][1], grid.getFieldValue(1, "adapterSequence"));
        Assert.assertEquals("Incorrect cell value", rocheAdapters[1][1], grid.getFieldValue(2, "adapterSequence"));

        grid.eval("getSelectionModel().select(0)");
        grid.clickTbarButton("Move Down");
        sleep(500);
        assertTextBefore(rocheAdapters[1][0], rocheAdapters[0][0]);

        grid.eval("getSelectionModel().select(1)");
        grid.clickTbarButton("Move Up");
        sleep(500);
        assertTextBefore(rocheAdapters[0][0], rocheAdapters[1][0]);

        grid.clickTbarButton("Remove");
        sleep(500);
        assertTextNotPresent(rocheAdapters[0][0]);

        log("Testing Alignment Section");

        log("Testing whether sections are disabled when alignment unchecked");
        Ext4FieldRef.getForLabel(this, "Perform Alignment").setChecked(false);
        assertEquals("Field should be hidden", false, Ext4FieldRef.isFieldPresent(this, "Reference Genome Type"));
        assertEquals("Field should be hidden", false, Ext4FieldRef.isFieldPresent(this, "Choose Aligner"));

        Ext4FieldRef.getForLabel(this, "Perform Alignment").setChecked(true);
        waitForText("Reference Genome Type");
        sleep(500);

        log("Testing aligner field and descriptions");
        Ext4ComboRef refLibraryField = Ext4ComboRef.getForLabel(this, "Reference Genome Type");
        Ext4ComboRef alignerField = Ext4ComboRef.getForLabel(this, "Choose Aligner");

        refLibraryField.setComboByDisplayValue("Custom Library");
        Ext4FieldRef.isFieldPresent(this, "Reference Name");
        Ext4FieldRef.isFieldPresent(this, "Sequence");

        refLibraryField.setComboByDisplayValue("DNA Sequences");
        Ext4FieldRef.isFieldPresent(this, "Species");
        Ext4FieldRef.isFieldPresent(this, "Loci");

        //verify virus_strains query
        SelectRowsCommand  sr = new SelectRowsCommand("sequenceanalysis", "virus_strains");
        sr.addFilter(new Filter("name", "SIVmac239_Test", Filter.Operator.EQUAL));
        SelectRowsResponse resp = sr.execute(WebTestHelper.getRemoteApiConnection(), getContainerId());
        assertTrue(resp.getRowCount().intValue() > 0);
        log("total viral sequences: " + resp.getRowCount() + " in container: " + getCurrentContainerPath());

        log("set combo 1");
        refLibraryField.setComboByDisplayValue("Viral Sequences");
        Ext4ComboRef.waitForField(this, "Virus Strain");
        Ext4ComboRef strainField = Ext4ComboRef.getForLabel(this, "Virus Strain");
        strainField.waitForEnabled();
        log("wait for store load");
        strainField.waitForStoreLoad();
        log("set value");
        strainField.setValue(strain);


        log("set by display value");
        alignerField.waitForStoreLoad();

        alignerField.setComboByDisplayValue("BWA-SW");
        waitForText("BWA-SW uses a different algorithm than BWA that is better suited for longer reads.");
        log("set combo 2");
        alignerField.setComboByDisplayValue("BWA");
        Ext4FieldRef.isFieldPresent(this, "Max Mismatches");
        log("set combo 3");
        alignerField.setComboByDisplayValue("Mosaik");
        log("get field");
        Ext4FieldRef.getForLabel(this, "Alignment Threshold").setValue(60);

        Ext4CmpRef panel = _ext4Helper.queryOne("#sequenceAnalysisPanel", Ext4CmpRef.class);
        Map<String, Object> params = (Map) panel.getEval("getJsonParams()");
        String invalidJSONError = "Incorrect param in form JSON";

        assertEquals(invalidJSONError, true, params.get("deleteIntermediateFiles"));
        assertEquals(invalidJSONError, analysisDescription, params.get("jobDescription"));
        assertEquals(invalidJSONError, jobName, params.get("jobName"));

        assertEquals(invalidJSONError, "IlluminaAdapterTrimming;AdapterTrimming;AvgQualFilter;CropReads;FASTQC;DownsampleReads;PrintReadsContaining;Flash;MaxInfoTrim;SlidingWindowTrim;ReadLengthFilter", params.get("fastqProcessing"));
        assertEquals(invalidJSONError, overlapLength.toString(), params.get("fastqProcessing.AdapterTrimming.overlapLength").toString());
        assertEquals(invalidJSONError, errorRate.toString(), params.get("fastqProcessing.AdapterTrimming.errorRate").toString());
        assertEquals(invalidJSONError, minReadLength, params.get("fastqProcessing.AdapterTrimming.minLength").toString());
        assertEquals(invalidJSONError, "20", params.get("fastqProcessing.AvgQualFilter.avgqual").toString());

        assertEquals(invalidJSONError, cropLength.toString(), params.get("fastqProcessing.CropReads.cropLength").toString());
        assertEquals(invalidJSONError, totalReadsVal.toString(), params.get("fastqProcessing.DownsampleReads.downsampleReadNumber").toString());
        assertEquals(invalidJSONError, minReadLength, params.get("fastqProcessing.ReadLengthFilter.minLength").toString());

        assertEquals(invalidJSONError, qualAvgQual, params.get("fastqProcessing.SlidingWindowTrim.avgQual").toString());
        assertEquals(invalidJSONError, qualWindowSize, params.get("fastqProcessing.SlidingWindowTrim.windowSize").toString());

        assertEquals(invalidJSONError, "Virus", params.get("referenceLibraryCreation"));
        assertEquals(invalidJSONError, "Virus", params.get("referenceLibraryCreation.Virus.category"));
        assertEquals(invalidJSONError, strain, params.get("referenceLibraryCreation.Virus.subset"));

        List<Object> adapters = (List) params.get("fastqProcessing.AdapterTrimming.adapters");
        List<Object> adapter1 = (List) adapters.get(0);
        assertEquals(invalidJSONError, rocheAdapters[1][0], adapter1.get(0).toString());
        assertEquals(invalidJSONError, rocheAdapters[1][1], adapter1.get(1).toString());
        assertEquals(invalidJSONError, true, adapter1.get(2));
        assertEquals(invalidJSONError, true, adapter1.get(3));

        assertEquals(invalidJSONError, alignerField.getValue(), params.get("alignment"));
        assertEquals(invalidJSONError, 60L, params.get("alignment.Mosaik.align_threshold"));
        assertEquals(invalidJSONError, 15L, params.get("alignment.Mosaik.hash_size"));
        assertEquals(invalidJSONError, 0.02, params.get("alignment.Mosaik.max_mismatch_pct"));
        assertEquals(invalidJSONError, true, params.get("alignment.Mosaik.output_multiple"));

        List readsets = (List) params.get("readsetIds");
        assertEquals(invalidJSONError, 2, readsets.size());

        checkErrors(); //try to track down when error occurring
    }

    /**
     * This method will make a request to download merged FASTQ files created during the illumina test
     */
    private void validateFastqDownload(File output) throws IOException
    {
        log("Verifying merged FASTQ export");

        assertTrue("Unable to find file: " + output.getPath(), output.exists());
        log("File size: " + FileUtils.sizeOf(output));

        try (InputStream fileStream = new FileInputStream(output); BufferedReader br = Readers.getReader(new GZIPInputStream(fileStream)))
        {
            int count = 0;
            int totalChars = 0;
            String thisLine;
            while ((thisLine = br.readLine()) != null)
            {
                count++;
                totalChars += thisLine.length();
            }

            int expectedLength = 504;
            assertEquals("Length of file doesn't match expected value.  Total characters: " + totalChars, expectedLength, count);

            output.delete();
        }
    }

    /**
     * The intent of this method is to perform additional tests of this AlignmentImportPanel,
     * with the goal of exercising all UI options, but not actually running the pipeline job since this runs remotely.
     */
    private void alignmentImportPanelTest(boolean sequencePipelineEnabled) throws Exception
    {
        log("Verifying AlignmentImportPanel UI");

        goToProjectHome();
        File fileRoot = TestFileUtils.getDefaultFileRoot(getProjectName());
        log("file root: " + fileRoot.getPath());

        File inputBam = new File(fileRoot, "test.bam");
        File inputBamIdx = new File(inputBam.getPath() + ".bai");
        if (inputBam.exists())
        {
            log("deleting existing BAM");
            inputBam.delete();
        }

        if (inputBamIdx.exists())
        {
            log("deleting existing BAM Index");
            inputBamIdx.delete();
        }

        FileUtils.copyFile(new File(_sampleData, "test.bam"), inputBam);
        FileUtils.copyFile(new File(_sampleData, "test.bam.bai"), inputBamIdx);

        _helper.initiatePipelineJob(_alignmentImportPipelineName, Arrays.asList(inputBam.getName()), getProjectName());
        waitForText("Job Name");

        Ext4FieldRef.getForLabel(this, "Job Name").setValue("AlignmentTest_" + System.currentTimeMillis());

        waitForElement(Locator.linkContainingText(inputBam.getName()));

        Ext4FieldRef treatmentField = Ext4FieldRef.getForLabel(this, "Treatment of Input Files");
        Assert.assertEquals("Incorrect starting value for input file-handling field", "delete", treatmentField.getValue());

        waitAndClick(Ext4Helper.Locators.ext4Button("Import Data"));
        waitForElement(Ext4Helper.Locators.window("Error"));
        waitForElement(Locator.tagContainingText("div", "There are 4 errors.  Please review the cells highlighted in red."));
        click(Ext4Helper.Locators.ext4Button("OK"));

        Ext4GridRef readsetGrid = _ext4Helper.queryOne("#sampleGrid", Ext4GridRef.class);
        readsetGrid.setClicksToEdit(1);
        readsetGrid.setGridCell(1, "readsetname", "Readset1");
        readsetGrid.setGridCell(1, "platform", "ILLUMINA");
        readsetGrid.setGridCell(1, "application", "DNA Sequencing (Genome)");

        waitAndClick(Ext4Helper.Locators.ext4Button("Add Step"));
        Locator.XPathLocator win = Ext4Helper.Locators.window("Add Steps");
        waitForElement(win);

        //NOTE: team city is getting JS errors I cant repro locally.  adding sleep() to see if waiting helps
        List<Ext4CmpRef> btns = _ext4Helper.componentQuery("window ldk-linkbutton[text='Add']", Ext4CmpRef.class);
        for (Ext4CmpRef btn : btns)
        {
            // Note: BQSR includes a genome selector field, which will log a warning to the server is a genome isnt configured ahead of time.
            // We should eventually fix this for alignment import, but its safe to just skip it in the test for now
            String stepLabel = (String)btn.getEval("step.label");
            if (!"Base Quality Score Recalibration".equals(stepLabel))
            {
                waitAndClick(Locator.id(btn.getId()).append(Locator.tag("a")));
                sleep(100);
            }
        }

        waitAndClick(Ext4Helper.Locators.ext4Button("Done"));
        waitForElementToDisappear(Ext4Helper.Locators.window("Add Steps"));

        sleep(500);

        //check JSON, should be null w/o a valid genome Id
        Ext4CmpRef panel = _ext4Helper.queryOne("#sequenceAnalysisPanel", Ext4CmpRef.class);
        assertNull("genome field not set, except params to be null", panel.getEval("getJsonParams()"));

        //close alert
        waitForElement(Ext4Helper.Locators.window("Error"));
        click(Ext4Helper.Locators.ext4Button("OK"));

        //set genome field
        readsetGrid.setGridCellJS(1, "library_id", _createdGenomeId.toString());

        //remove all steps:
        btns = _ext4Helper.componentQuery("ldk-linkbutton[text='Remove']", Ext4CmpRef.class);
        for (Ext4CmpRef btn : btns)
        {
            Locator l = Locator.id(btn.getId()).append(Locator.tag("a"));
            waitAndClick(l);
            waitForElementToDisappear(l);
        }

        Map<String, Object> fieldsJson = (Map) panel.getEval("getJsonParams()");
        String container = (String) executeScript("return LABKEY.Security.currentContainer.id");
        assertEquals("Incorrect param for containerId", container, fieldsJson.get("containerId"));
        assertEquals("Incorrect param for baseURL", getBaseURL() + "/", fieldsJson.get("baseUrl"));

        Long id1 = (Long) executeScript("return LABKEY.Security.currentUser.id");
        Long id2 = (Long) fieldsJson.get("userId");
        assertEquals("Incorrect param for userId", id1, id2);
        //String containerPath = getURL().getPath().replaceAll("/importReadset.view(.)*", "").replaceAll("(.)*/sequenceanalysis", "");
        assertEquals("Incorrect param for containerPath.  Raw URL: [" + getURL() + "]", getCurrentContainerPath(), fieldsJson.get("containerPath"));

        assertEquals("Unexpected value for param", true, fieldsJson.get("deleteIntermediateFiles"));
        assertEquals("Unexpected value for param", "", fieldsJson.get("bamPostProcessing"));
        assertEquals("Unexpected value for param", true, fieldsJson.get("collectWgsMetrics"));
        assertEquals("Unexpected value for param", "delete", fieldsJson.get("inputFileTreatment"));
        assertEquals("Unexpected value for param", 1, ((Collection)fieldsJson.get("inputFiles")).size());
        assertEquals("Unexpected value for param", inputBam.getName(), ((Map)(((Collection) fieldsJson.get("inputFiles")).iterator().next())).get("fileName"));

        if (!sequencePipelineEnabled)
        {
            log("sequencePipelineEnabled is false, so skip actual import");
        }
        else
        {
            //now submit
            waitAndClick(Ext4Helper.Locators.ext4ButtonEnabled("Import Data"));
            waitAndClickAndWait(Ext4Helper.Locators.ext4Button("OK"));

            waitAndClickAndWait(Locator.linkWithText("All"));
            _startedPipelineJobs++;
            waitForElement(Locator.tagContainingText("span", "Data Pipeline"));

            waitForPipelineJobsToComplete(_startedPipelineJobs, getDescriptionOfLastPipelineJob(), false);
            assertTextPresent("COMPLETE");
        }

        goToProjectHome();
    }

    private String getDescriptionOfLastPipelineJob() throws Exception
    {
        //query to find description
        Connection cn = WebTestHelper.getRemoteApiConnection();
        SelectRowsCommand sr = new SelectRowsCommand("pipeline", "job");
        sr.addSort(new Sort("rowid", Sort.Direction.DESCENDING));
        sr.setMaxRows(1);
        SelectRowsResponse resp = sr.execute(cn, getProjectName());
        return resp.getRows().get(0).get("Description").toString();
    }

    /**
     * The intent of this method is to perform additional tests of this Readset Import Panel,
     * with the goal of exercising all UI options
     */
    private void readsetPanelTest() throws IOException
    {
        log("Verifying Readset Import Panel UI");

        goToProjectHome();
        File fileRoot = TestFileUtils.getDefaultFileRoot(getProjectName());
        log("file root: " + fileRoot.getPath());

        List<String> files = new ArrayList<>();

        String filename1 = "dualBarcodes_SIV.fastq.gz";
        FileUtils.copyFile(new File(_sampleData, filename1), new File(fileRoot, filename1));
        files.add(filename1);

        String filename2 = "sample454_SIV.sff";
        FileUtils.copyFile(new File(_sampleData, filename2), new File(fileRoot, filename2));
        files.add(filename2);

        //multiples files from a single group
        List<Pair<String, String>> readGroup = new ArrayList<>();
        readGroup.add(Pair.of("s_G1_L001_R1_001.fastq.gz", "s_G1_L001_R2_001.fastq.gz"));
        readGroup.add(Pair.of("s_G1_L001_R1_002.fastq.gz", "s_G1_L001_R2_002.fastq.gz"));
        readGroup.add(Pair.of("s_G1_L002_R1_001.fastq.gz", "s_G1_L002_R2_001.fastq.gz"));
        for (Pair<String, String> pair : readGroup)
        {
            FileUtils.copyFile(new File(_sampleData, "paired1.fastq.gz"), new File(fileRoot, pair.getLeft()));
            files.add(pair.getLeft());

            FileUtils.copyFile(new File(_sampleData, "paired2.fastq.gz"), new File(fileRoot, pair.getRight()));
            files.add(pair.getRight());
        }

        _helper.initiatePipelineJob(_readsetPipelineName, files, getProjectName());
        waitForText("Job Name");

        Ext4FieldRef.getForLabel(this, "Job Name").setValue("SequenceTest_" + System.currentTimeMillis());

        waitForElement(Locator.linkContainingText(filename1));
        waitForElement(Locator.linkContainingText(filename2));

        Ext4FieldRef barcodeField = Ext4FieldRef.getForLabel(this, "Demultiplex Data");
        Ext4FieldRef treatmentField = Ext4FieldRef.getForLabel(this, "Treatment of Input Files");

        assertEquals("Incorrect starting value for input file-handling field", "delete", treatmentField.getValue());

        Ext4GridRef readDataGrid = _ext4Helper.queryOne("#readDataGrid", Ext4GridRef.class);
        readDataGrid.setClicksToEdit(1);
        Ext4GridRef readsetGrid = _ext4Helper.queryOne("#readsetGrid", Ext4GridRef.class);
        readsetGrid.setClicksToEdit(1);

        //verify file groups worked right
        Assert.assertEquals("incorrect row count", 5, readDataGrid.getRowCount());
        Assert.assertEquals("incorrect row count", 3, readsetGrid.getRowCount());

        Assert.assertNotNull(StringUtils.trimToNull(Objects.toString(readDataGrid.getFieldValue(1, "fileRecord1"))));
        Assert.assertNull(StringUtils.trimToNull(Objects.toString(readDataGrid.getFieldValue(1, "fileRecord2"))));

        Assert.assertNotNull(StringUtils.trimToNull(Objects.toString(readDataGrid.getFieldValue(2, "fileRecord1"))));
        Assert.assertNotNull(StringUtils.trimToNull(Objects.toString(readDataGrid.getFieldValue(2, "fileRecord2"))));
        Assert.assertNotNull(StringUtils.trimToNull(Objects.toString(readDataGrid.getFieldValue(3, "fileRecord1"))));
        Assert.assertNotNull(StringUtils.trimToNull(Objects.toString(readDataGrid.getFieldValue(3, "fileRecord2"))));
        Assert.assertNotNull(StringUtils.trimToNull(Objects.toString(readDataGrid.getFieldValue(4, "fileRecord1"))));
        Assert.assertNotNull(StringUtils.trimToNull(Objects.toString(readDataGrid.getFieldValue(4, "fileRecord2"))));

        Assert.assertNotNull(StringUtils.trimToNull(Objects.toString(readDataGrid.getFieldValue(5, "fileRecord1"))));
        Assert.assertNull(StringUtils.trimToNull(Objects.toString(readDataGrid.getFieldValue(5, "fileRecord2"))));

        Assert.assertEquals("incorrect group count", 3L, readDataGrid.getEval("store.getGroups().length"));

        //then split groups
        waitAndClick(readDataGrid.getRow(2));
        readDataGrid.clickTbarButton("Split/Regroup Selected");
        waitForElement(Ext4Helper.Locators.window("Split/Regroup Files"));
        String groupName = "NewGroup";
        _ext4Helper.queryOne("window textfield", Ext4FieldRef.class).setValue(groupName);
        waitAndClick(Ext4Helper.Locators.ext4ButtonEnabled("OK"));
        sleep(200);
        Assert.assertEquals("incorrect group count", 4L, readDataGrid.getEval("store.getGroups().length"));
        Assert.assertEquals("incorrect readsetname", "dualBarcodes_SIV", readsetGrid.getFieldValue(1, "fileGroupId"));
        readDataGrid.setGridCell(2, "fileGroupId", groupName);
        sleep(200);
        Assert.assertEquals("incorrect group count", 3L, readDataGrid.getEval("store.getGroups().length"));
        Assert.assertEquals("incorrect readsetname", groupName, readsetGrid.getFieldValue(1, "fileGroupId"));

        readDataGrid.setGridCell(1, "fileGroupId", groupName);
        Assert.assertEquals("incorrect readsetname", "sample454_SIV", readsetGrid.getFieldValue(3, "fileGroupId"));
        readDataGrid.setGridCell(5, "fileGroupId", "sample454_SIVb");
        Assert.assertEquals("incorrect readsetname", "sample454_SIVb", readsetGrid.getFieldValue(3, "fileGroupId"));

        //now we check readset info
        readsetGrid.clickTbarButton("Bulk Edit");
        waitForElement(Ext4Helper.Locators.window("Bulk Edit").notHidden());
        Ext4FieldRef.waitForField(this, "Select Field");
        sleep(500);

        _ext4Helper.selectComboBoxItem("Select Field:", Ext4Helper.TextMatchTechnique.CONTAINS, "Application");
        Ext4ComboRef.waitForField(this, "Application");
        Ext4ComboRef.getForLabel(this, "Application").setValue("DNA Sequencing (Genome)");
        waitAndClick(Ext4Helper.Locators.ext4ButtonEnabled("Submit"));
        waitForElementToDisappear(Ext4Helper.Locators.window("Bulk Edit"));
        sleep(200);
        Assert.assertEquals("field not set", "DNA Sequencing (Genome)", readsetGrid.getFieldValue(1, "application"));
        Assert.assertEquals("field not set", "DNA Sequencing (Genome)", readsetGrid.getFieldValue(2, "application"));
        Assert.assertEquals("field not set", "DNA Sequencing (Genome)", readsetGrid.getFieldValue(3, "application"));

        barcodeField.setChecked(true);
        sleep(200);
        waitForElement(Locator.tagContainingText("span", "5' Barcode").notHidden());
        Assert.assertEquals("Incorrect value for input file-handling field after barcode toggle", "compress", treatmentField.getValue());
        Assert.assertEquals("The test is looking for the wrong column index for 5' barcode", "barcode5", readsetGrid.getEval("columns[2].dataIndex"));
        Assert.assertEquals("The test is looking for the wrong column index for 3' barcode", "barcode3", readsetGrid.getEval("columns[3].dataIndex"));

        Assert.assertFalse("5' barcode column should not be hidden", (Boolean) readsetGrid.getEval("columns[2].hidden"));
        Assert.assertFalse("3' barcode column should not be hidden", (Boolean) readsetGrid.getEval("columns[3].hidden"));

        barcodeField.setChecked(false);
        sleep(200);
        waitForElementToDisappear(Locator.tagContainingText("span", "5' Barcode").notHidden());
        assertEquals("Incorrect value for input file-handling field after barcode toggle", "delete", treatmentField.getValue());
        assertTrue("5' barcode column should be hidden", (Boolean) readsetGrid.getEval("columns[2].hidden"));
        assertTrue("3' barcode column should be hidden", (Boolean) readsetGrid.getEval("columns[3].hidden"));

        waitAndClick(Ext4Helper.Locators.ext4Button("Import Data"));
        waitForElement(Ext4Helper.Locators.window("Error"));
        Locator errorEl = Locator.tagContainingText("div", "There are 12 errors.  Please review the cells highlighted in red.").notHidden();
        waitForElement(errorEl);
        click(Ext4Helper.Locators.ext4Button("OK"));

        waitForElementToDisappear(errorEl);
        assertEquals("Incorrect row count", 3, readsetGrid.getRowCount());
        setBasicSampleDetails(readsetGrid);
        readsetGrid.setGridCell(2, "readsetname", "Readset1"); //duplicate name

        waitAndClick(Ext4Helper.Locators.ext4Button("Import Data"));
        waitForElement(Ext4Helper.Locators.window("Error"));
        waitForElement(Locator.tagContainingText("div", "There are 2 errors.  Please review the cells highlighted in red.  Note: you can hover over the cell for more information on the issue."));
        waitAndClick(Ext4Helper.Locators.ext4Button("OK"));

        //try duplicate barcodes
        barcodeField.setChecked(true);

        String barcode = "FLD0376";
        readsetGrid.setGridCellJS(1, "barcode5", barcode);
        readsetGrid.setGridCellJS(2, "barcode3", barcode);
        waitAndClick(Ext4Helper.Locators.ext4Button("Import Data"));
        waitForElement(Ext4Helper.Locators.window("Error"));

        waitForElement(Locator.tagContainingText("div", "There are 4 errors.  Please review the cells highlighted in red."));
        click(Ext4Helper.Locators.ext4Button("OK"));
        barcodeField.setChecked(false);
        readsetGrid.setGridCell(2, "readsetname", "Readset2");
        Ext4CmpRef panel = _ext4Helper.queryOne("#sequenceAnalysisPanel", Ext4CmpRef.class);
        Map<String, Object> fieldsJson = (Map) panel.getEval("getJsonParams()");
        //List<Long> fileIds = (List)params.get("distinctIds");
        //List<String> fieldsNames = (List)params.get("distinctNames");

        String container = (String) executeScript("return LABKEY.Security.currentContainer.id");
        assertEquals("Incorrect param for containerId", container, fieldsJson.get("containerId"));
        assertEquals("Incorrect param for baseURL", getBaseURL() + "/", fieldsJson.get("baseUrl"));

        Long id1 = (Long) executeScript("return LABKEY.Security.currentUser.id");
        Long id2 = (Long) fieldsJson.get("userId");
        assertEquals("Incorrect param for userId", id1, id2);
        //String containerPath = getURL().getPath().replaceAll("/importReadset.view(.)*", "").replaceAll("(.)*/sequenceanalysis", "");
        assertEquals("Incorrect param for containerPath.  Raw URL: [" + getURL() + "]", getCurrentContainerPath(), fieldsJson.get("containerPath"));

        Collections.sort(files);
        List<String> jsonFileNames = new ArrayList<>();
        for (Object o : (Collection)fieldsJson.get("inputFiles"))
        {
            jsonFileNames.add(((Map)o).get("fileName").toString());
        }
        assertEquals("Unexpected value for param", StringUtils.join(files, ";"), StringUtils.join(jsonFileNames, ";"));

        assertNull("Unexpected value for param", StringUtils.trimToNull((String) fieldsJson.get("inputfile.barcodeGroups")));
        assertEquals("Unexpected value for param", Long.valueOf(0), fieldsJson.get("inputfile.barcodeEditDistance"));
        assertEquals("Unexpected value for param", Long.valueOf(0), fieldsJson.get("inputfile.barcodeOffset"));
        assertEquals("Unexpected value for param", Long.valueOf(0), fieldsJson.get("inputfile.barcodeDeletions"));
        assertEquals("Unexpected value for param", false, fieldsJson.get("inputfile.barcode"));
        assertEquals("Unexpected value for param", "delete", fieldsJson.get("inputFileTreatment"));
        assertEquals("Unexpected value for param", true, fieldsJson.get("deleteIntermediateFiles"));

        Map<String, Object> fileGroup0 = (Map) fieldsJson.get("fileGroup_1");
        Assert.assertEquals("Unexpected value for param", groupName, fileGroup0.get("name"));
        List<Map> arr0 = (List)fileGroup0.get("files");
        Assert.assertEquals(2, arr0.size());
        Assert.assertEquals(groupName, arr0.get(0).get("fileGroupId"));
        Assert.assertEquals("s_G1_L001", arr0.get(0).get("platformUnit"));

        Assert.assertEquals(groupName, arr0.get(1).get("fileGroupId"));
        Assert.assertEquals("", arr0.get(1).get("platformUnit"));

        Map<String, Object> fileGroup1 = (Map) fieldsJson.get("fileGroup_2");
        Assert.assertEquals(2, ((List)fileGroup1.get("files")).size());
        Map<String, Object> fileGroup2 = (Map) fieldsJson.get("fileGroup_3");
        Assert.assertEquals(1, ((List)fileGroup2.get("files")).size());

        Map<String, Object> sample0 = (Map) fieldsJson.get("readset_0");
        Map<String, Object> sample1 = (Map) fieldsJson.get("readset_1");
        Map<String, Object> sample2 = (Map) fieldsJson.get("readset_2");

        Assert.assertEquals("Unexpected value for param", "Readset1", sample0.get("readsetname"));
        Assert.assertEquals("Unexpected value for param", "Subject1", sample0.get("subjectid"));
        Assert.assertNull("Unexpected value for param", StringUtils.trimToNull((String) sample0.get("readset")));
        Assert.assertEquals("Unexpected value for param", "ILLUMINA", sample0.get("platform"));
        Assert.assertEquals("Unexpected value for param", groupName, StringUtils.trimToNull((String) sample0.get("fileGroupId")));
        Assert.assertNull("param should not be present", StringUtils.trimToNull((String) sample0.get("barcode5")));
        Assert.assertNull("param should not be present", StringUtils.trimToNull((String) sample0.get("barcode3")));

        Assert.assertEquals("Unexpected value for param", "Readset2", sample1.get("readsetname"));
        Assert.assertEquals("Unexpected value for param", "Subject2", sample1.get("subjectid"));
        Assert.assertEquals("Unexpected value for param", "2010-10-20", sample1.get("sampledate"));
        Assert.assertNull("Unexpected value for param", StringUtils.trimToNull((String) sample1.get("readset")));
        Assert.assertEquals("Unexpected value for param", "LS454", sample1.get("platform"));
        Assert.assertEquals("Unexpected value for param", "s_G1", StringUtils.trimToNull((String) sample1.get("fileGroupId")));
        Assert.assertEquals("Unexpected value for param", "gDNA", sample1.get("sampletype"));
        Assert.assertNull("param should not be present", StringUtils.trimToNull((String)sample1.get("barcode5")));
        Assert.assertNull("param should not be present", StringUtils.trimToNull((String)sample1.get("barcode3")));

        Assert.assertEquals("Unexpected value for param", "Readset3", sample2.get("readsetname"));
        Assert.assertEquals("Unexpected value for param", "Subject3", sample2.get("subjectid"));
        Assert.assertEquals("Unexpected value for param", "2010-10-20", sample2.get("sampledate"));
        Assert.assertNull("Unexpected value for param", StringUtils.trimToNull((String) sample2.get("readset")));
        Assert.assertEquals("Unexpected value for param", "LS454", sample2.get("platform"));
        Assert.assertEquals("Unexpected value for param", "sample454_SIVb", StringUtils.trimToNull((String) sample2.get("fileGroupId")));
        Assert.assertEquals("Unexpected value for param", "gDNA", sample2.get("sampletype"));
        Assert.assertEquals("Unexpected value for param", 0.2, sample2.get("concentration"));
        Assert.assertEquals("Unexpected value for param", 410L, sample2.get("fragmentSize"));
        Assert.assertNull("param should not be present", StringUtils.trimToNull((String)sample2.get("barcode5")));
        Assert.assertNull("param should not be present", StringUtils.trimToNull((String)sample2.get("barcode3")));

        barcodeField.setValue(true);
        Ext4FieldRef.getForLabel(this, "Additional Barcodes To Test").setValue("GSMIDs");
        Ext4FieldRef.getForLabel(this, "Mismatches Tolerated").setValue(9);
        Ext4FieldRef.getForLabel(this, "Deletions Tolerated").setValue(9);
        Ext4FieldRef.getForLabel(this, "Allowed Distance From Read End").setValue(9);

        Ext4FieldRef.getForLabel(this, "Delete Intermediate Files").setValue(false);
        treatmentField.setValue("compress");

        readsetGrid.setGridCell(1, "barcode5", "FLD0001");
        readsetGrid.setGridCell(2, "barcode5", "FLD0002");
        readsetGrid.setGridCell(3, "barcode5", "FLD0003");

        fieldsJson = (Map) panel.getEval("getJsonParams()");

        Assert.assertEquals("Unexpected value for param", true, fieldsJson.get("inputfile.barcode"));
        Assert.assertEquals("Unexpected value for param", Collections.singletonList("GSMIDs"), fieldsJson.get("inputfile.barcodeGroups"));
        Assert.assertEquals("Unexpected value for param", Long.valueOf(9), fieldsJson.get("inputfile.barcodeEditDistance"));
        Assert.assertEquals("Unexpected value for param", Long.valueOf(9), fieldsJson.get("inputfile.barcodeOffset"));
        Assert.assertEquals("Unexpected value for param", Long.valueOf(9), fieldsJson.get("inputfile.barcodeDeletions"));

        Assert.assertEquals("Unexpected value for param", "compress", fieldsJson.get("inputFileTreatment"));
        Assert.assertEquals("Unexpected value for param", false, fieldsJson.get("deleteIntermediateFiles"));

        sample0 = (Map) fieldsJson.get("readset_0");
        Assert.assertEquals("Unexpected value for param", "Readset1", sample0.get("readsetname"));
        Assert.assertEquals("Unexpected value for param", "Subject1", sample0.get("subjectid"));
        Assert.assertEquals("Unexpected value for param", null, StringUtils.trimToNull((String) sample0.get("readset")));
        Assert.assertEquals("Unexpected value for param", "ILLUMINA", sample0.get("platform"));
        Assert.assertEquals("Unexpected value for param", null, StringUtils.trimToNull((String) sample0.get("fileId")));
        Assert.assertEquals("Unexpected value for param", "gDNA", sample0.get("sampletype"));
        Assert.assertEquals("Unexpected value for param", "FLD0001", sample0.get("barcode5"));
        Assert.assertNull("Unexpected value for param", StringUtils.trimToNull((String)sample0.get("barcode3")));
    }

    private void setBasicSampleDetails(Ext4GridRef grid)
    {
        grid.setGridCell(1, "readsetname", "Readset1");
        grid.setGridCellJS(1, "platform", "ILLUMINA");
        grid.setGridCellJS(1, "sampletype", "gDNA");
        grid.setGridCell(1, "subjectid", "Subject1");

        grid.setGridCell(2, "readsetname", "Readset2");
        grid.setGridCellJS(2, "platform", "LS454");
        grid.setGridCellJS(2, "sampletype", "gDNA");
        grid.setGridCell(2, "subjectid", "Subject2");
        grid.setGridCell(2, "sampledate", "2010-10-20");

        if (grid.getRowCount() > 2)
        {
            grid.setGridCell(3, "readsetname", "Readset3");
            grid.setGridCellJS(3, "platform", "LS454");
            grid.setGridCellJS(3, "sampletype", "gDNA");
            grid.setGridCell(3, "subjectid", "Subject3");
            grid.setGridCell(3, "sampledate", "2010-10-20");
            grid.setGridCell(3, "concentration", "0.2");
            grid.setGridCell(3, "fragmentSize", "410");
        }
    }

    private void readsetImportTest() throws IOException, CommandException
    {
        log("Verifying Readset Import");

        goToProjectHome();

        File fileRoot = TestFileUtils.getDefaultFileRoot(getProjectName());
        log("file root: " + fileRoot.getPath());
        List<String> files = new ArrayList<>();

        String filename1 = "dualBarcodes_SIV.fastq.gz";
        FileUtils.copyFile(new File(_sampleData, filename1), new File(fileRoot, filename1));
        files.add(filename1);

        String filename2 = "paired1.fastq.gz";
        FileUtils.copyFile(new File(_sampleData, filename2), new File(fileRoot, filename2));
        files.add(filename2);

        //multiples files from a single group
        List<Pair<String, String>> readGroup = new ArrayList<>();
        readGroup.add(Pair.of("s_G1_L001_R1_001.fastq.gz", "s_G1_L001_R2_001.fastq.gz"));

        readGroup.add(Pair.of("s_G1_L002_R1_001.fastq.gz", "s_G1_L002_R2_001.fastq.gz"));
        for (Pair<String, String> pair : readGroup)
        {
            FileUtils.copyFile(new File(_sampleData, "paired1.fastq.gz"), new File(fileRoot, pair.getLeft()));
            files.add(pair.getLeft());

            FileUtils.copyFile(new File(_sampleData, "paired2.fastq.gz"), new File(fileRoot, pair.getRight()));
            files.add(pair.getRight());
        }

        _helper.initiatePipelineJob(_readsetPipelineName, files, getProjectName());
        waitForText("Job Name");

        Ext4FieldRef.getForLabel(this, "Job Name").setValue("SequenceTest_" + System.currentTimeMillis());

        waitForElement(Locator.linkContainingText(filename1));
        waitForElement(Locator.linkContainingText(filename2));

        Ext4FieldRef.getForLabel(this, "Treatment of Input Files").setValue("none");
        Ext4GridRef readsetGrid = _ext4Helper.queryOne("#readsetGrid", Ext4GridRef.class);
        readsetGrid.setClicksToEdit(1);

        String readset1 = "ImportReadsetTest1";
        String readset2 = "ImportReadsetTest2";
        String readset3 = "ImportReadsetTest3";

        readsetGrid.setGridCell(1, "readsetname", readset1);
        readsetGrid.setGridCellJS(1, "platform", "ILLUMINA");
        readsetGrid.setGridCellJS(1, "application", "DNA Sequencing (Genome)");
        readsetGrid.setGridCellJS(1, "sampletype", "gDNA");
        readsetGrid.setGridCell(1, "subjectid", "Subject1");
        readsetGrid.setGridCell(1, "sampledate", "2011-02-03");

        readsetGrid.setGridCell(2, "readsetname", readset2);
        readsetGrid.setGridCellJS(2, "platform", "LS454");
        readsetGrid.setGridCellJS(2, "application", "DNA Sequencing (Genome)");
        readsetGrid.setGridCellJS(2, "sampletype", "gDNA");
        readsetGrid.setGridCell(2, "subjectid", "Subject2");

        readsetGrid.setGridCell(3, "readsetname", readset3);
        readsetGrid.setGridCellJS(3, "platform", "LS454");
        readsetGrid.setGridCellJS(3, "application", "DNA Sequencing (Genome)");
        readsetGrid.setGridCellJS(3, "sampletype", "gDNA");
        readsetGrid.setGridCell(3, "subjectid", "Subject3");
        readsetGrid.setGridCell(3, "concentration", "0.2");
        readsetGrid.setGridCell(3, "fragmentSize", "410");

        waitAndClick(Ext4Helper.Locators.ext4Button("Import Data"));
        waitAndClickAndWait(Ext4Helper.Locators.ext4Button("OK"));

        waitAndClickAndWait(Locator.linkWithText("All"));
        _startedPipelineJobs += 3;
        waitForElement(Locator.tagContainingText("span", "Data Pipeline"));
        waitForPipelineJobsToComplete(_startedPipelineJobs, "Import Readsets", false);
        assertTextPresent("COMPLETE");

        log("verify readsets created");
        Connection cn = WebTestHelper.getRemoteApiConnection();
        SelectRowsCommand sr = new SelectRowsCommand("sequenceanalysis", "sequence_readsets");
        sr.addFilter(new Filter("name", readset1 + ";" + readset2 + ";" + readset3, Filter.Operator.IN));
        SelectRowsResponse resp = sr.execute(cn, getProjectName());
        Assert.assertEquals("Incorrect readset number", 3, resp.getRowCount().intValue());
        resp.getRowset().forEach(row -> {
            if (row.getValue("name").equals("readset3"))
            {
                Assert.assertEquals("Incorrect value", 0.2, row.getValue("concentration"));
                Assert.assertEquals("Incorrect value", 410L, row.getValue("fragmentSize"));
            }
        });

        SelectRowsCommand sr2 = new SelectRowsCommand("sequenceanalysis", "readdata");
        sr2.addFilter(new Filter("readset/name", readset1, Filter.Operator.EQUAL));
        Assert.assertEquals("Incorrect readdata number", 1, sr2.execute(cn, getProjectName()).getRowCount().intValue());

        SelectRowsCommand sr3 = new SelectRowsCommand("sequenceanalysis", "readdata");
        sr3.addFilter(new Filter("readset/name", readset2, Filter.Operator.EQUAL));
        Assert.assertEquals("Incorrect readdata number", 1, sr3.execute(cn, getProjectName()).getRowCount().intValue());

        SelectRowsCommand sr4 = new SelectRowsCommand("sequenceanalysis", "readdata");
        sr4.addFilter(new Filter("readset/name", readset3, Filter.Operator.EQUAL));
        Assert.assertEquals("Incorrect readdata number", 2, sr4.execute(cn, getProjectName()).getRowCount().intValue());

        //save file paths:
        String filter = readset1 + ";" + readset2 + ";" + readset3;
        SelectRowsCommand filePaths = new SelectRowsCommand("sequenceanalysis", "readdata");
        filePaths.addFilter(new Filter("readset/name", filter, Filter.Operator.IN));
        filePaths.setColumns(Arrays.asList("fileid1/DataFileUrl"));

        List<File> importedFiles = new ArrayList<>();
        filePaths.execute(cn, getProjectName()).getRowset().forEach(x -> {
            importedFiles.add(new File(URI.create(x.getValue("fileid1/DataFileUrl").toString())));
        });

        importedFiles.forEach(x -> {
            Assert.assertTrue("File does not exist: " + x.getPath(), x.exists());
        });

        log("attempting to re-import same files");
        goToProjectHome();
        _helper.initiatePipelineJob(_readsetPipelineName, Arrays.asList(filename1, filename2), getProjectName());
        waitForText("Job Name");
        waitForElement(Ext4Helper.Locators.window("Error"));
        waitForElement(Locator.tagContainingText("div", "There are errors with the input files"));
        isTextPresent("File is already used in existing readsets')]");
        waitAndClick(Ext4Helper.Locators.ext4Button("OK"));
        goToProjectHome();

        //make sure custom delete actions work:
        _helper.clickNavPanelItemAndWait("Readsets:", 1);
        DataRegionTable dr = new DataRegionTable.DataRegionFinder(getDriver()).withName("query").waitFor();
        dr.setFilter("name", "Equals One Of (example usage: a;b;c)", filter);
        dr.checkAllOnPage();

        Assert.assertEquals("Should have three rows", 3, dr.getCheckedCount());
        dr.clickHeaderButtonAndWait("Delete");
        waitForElement(Locator.tagContainingText("div", "Are you sure you want to delete the following 3 readsets"));

        assertTextPresent("Pipeline run: SequenceTest", 3);
        clickButton("OK");

        //make sure file actually deleted
        SelectRowsCommand sr5 = new SelectRowsCommand("sequenceanalysis", "readdata");
        sr5.addFilter(new Filter("readset/name", filter, Filter.Operator.IN));
        Assert.assertEquals("Incorrect readdata number", 0, sr5.execute(cn, getProjectName()).getRowCount().intValue());

        //make sure file actually deleted
        SelectRowsCommand sr6 = new SelectRowsCommand("sequenceanalysis", "quality_metrics");
        sr6.addFilter(new Filter("readset/name", filter, Filter.Operator.IN));
        Assert.assertEquals("Quality metrics not deleted", 0, sr6.execute(cn, getProjectName()).getRowCount().intValue());

        //NOTE: since the initial files are unaltered, they should be left alone
        importedFiles.forEach(x -> {
            Assert.assertTrue("File was deleted: " + x.getPath(), x.exists());
        });

        //Readset delete should delete these jobs
        _startedPipelineJobs -= 3;
    }

    @Override
    protected void doCleanup(boolean afterTest) throws TestTimeoutException
    {
        //cleanupDirectory(_illuminaPipelineLoc);
        super.doCleanup(afterTest);
    }

    protected void cleanupDirectory(String path)
    {
        File dir = new File(path);
        File[] files = dir.listFiles();
        for (File file : files)
        {
            if (file.isDirectory() &&
                    (file.getName().startsWith("sequenceAnalysis_") || file.getName().equals("illuminaImport") || file.getName().equals(".labkey")))
                TestFileUtils.deleteDir(file);
            if (file.getName().startsWith("SequenceImport"))
                file.delete();
        }
    }

    @Override
    public void setPipelineRoot(String rootPath)
    {
        if (rootPath.equals(_pipelineRoot))
            return;

        _pipelineRoot = rootPath;
        super.setPipelineRoot(rootPath);
    }

    @Override
    public List<String> getAssociatedModules()
    {
        return null;
    }

    public static String TEST_GENOME_NAME = "TestGenome1";

    private static void ensureSIVmac239exists(BaseWebDriverTest test) throws Exception
    {
        Connection cn = new Connection(test.getBaseURL(), PasswordUtil.getUsername(), PasswordUtil.getPassword());
        SelectRowsCommand sr = new SelectRowsCommand("sequenceanalysis", "ref_nt_sequences");
        sr.addFilter(new Filter("name", "SIVmac239_Test", Filter.Operator.EQUAL));
        SelectRowsResponse resp = sr.execute(cn, test.getContainerId());
        if (resp.getRowCount().intValue() == 0)
        {
            test.log("creating SIVMac239_Test sequence in container: " + test.getCurrentContainerPath());
            Map<String, Object> row = new HashMap<>();
            row.put("name", "SIVmac239_Test");
            row.put("subset", "SIVmac239_Test");
            String sequence = read239FromFile();
            row.put("sequence", sequence);
            row.put("category", "Virus");

            InsertRowsCommand ic = new InsertRowsCommand("sequenceanalysis", "ref_nt_sequences");
            ic.addRow(row);
            ic.execute(cn, test.getContainerId());
        }
        else
        {
            test.log("SIVMac239_Test sequence already exists for container: " + test.getCurrentContainerPath());
            String subset = (String)resp.getRows().get(0).get("subset");
            test.log("subset: " + subset);
        }

        //verify virus_strains query
        sr = new SelectRowsCommand("sequenceanalysis", "virus_strains");
        sr.addFilter(new Filter("name", "SIVmac239_Test", Filter.Operator.EQUAL));
        resp = sr.execute(cn, test.getContainerId());
        assertTrue(resp.getRowCount().intValue() > 0);
        test.log("total viral sequences: " + resp.getRowCount() + " in container: " + test.getCurrentContainerPath());
    }

    private static String read239FromFile() throws Exception
    {
        File fasta = new File(_sampleData, "Ref_DB.fasta");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(fasta), StandardCharsets.UTF_8)))
        {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null)
            {
                if (line.startsWith(">"))
                {
                    continue;
                }

                sb.append(line.trim());
            }

            return sb.toString();
        }
    }

    public static Integer createReferenceGenome(BaseWebDriverTest test, int previouslyStartedPipelineJobs) throws Exception
    {
        test.log("creating SIVmac239 reference genome");
        test.beginAt("/sequenceanalysis/" + test.getContainerId() + "/begin.view");

        //verify SIVmac239_Test NT sequence exists:
        ensureSIVmac239exists(test);

        test.waitAndClickAndWait(Locator.linkContainingText("Reference Sequences"));
        DataRegionTable dr = new DataRegionTable("query", test);
        dr.setFilter("name", "Equals", "SIVmac239_Test");
        dr.checkCheckbox(0);
        dr.clickHeaderMenu("More Actions", false, "Create Reference Genome");
        test.waitForElement(Ext4Helper.Locators.window("Create Reference Genome"));
        Ext4FieldRef.getForLabel(test, "Name").setValue(TEST_GENOME_NAME);
        String description = "This is a reference genome description";
        Ext4FieldRef.getForLabel(test, "Description").setValue(description);
        Ext4FieldRef.getForLabel(test, "Skip Aligner Index Creation").setChecked(true);  //skip this since it requires sequence tools
        test.waitAndClick(Ext4Helper.Locators.ext4ButtonEnabled("Submit"));
        test.waitForElement(Ext4Helper.Locators.window("Success"));
        test.waitAndClickAndWait(Ext4Helper.Locators.ext4ButtonEnabled("OK"));
        test.waitForPipelineJobsToComplete(previouslyStartedPipelineJobs + 1, "Create Reference Genome", false);
        test.goToProjectHome();

        Connection cn = new Connection(test.getBaseURL(), PasswordUtil.getUsername(), PasswordUtil.getPassword());
        SelectRowsCommand sr = new SelectRowsCommand("sequenceanalysis", "reference_libraries");
        sr.addFilter(new Filter("name", TEST_GENOME_NAME, Filter.Operator.EQUAL));
        sr.addFilter(new Filter("description", description, Filter.Operator.EQUAL));
        SelectRowsResponse resp = sr.execute(cn, test.getContainerId());

        return (Integer)resp.getRows().get(0).get("rowid");
    }

    public static int addReferenceGenomeTracks(BaseWebDriverTest test, String projectName, String genomeName, Integer genomeId, int startedPipelineJobs) throws Exception
    {
        test.log("adding resources to genome: " + genomeName);
        test.beginAt("/sequenceanalysis/" + test.getContainerId() + "/begin.view");

        test.waitAndClickAndWait(Locator.linkContainingText("Reference Genomes"));
        DataRegionTable dr = new DataRegionTable("query", test);
        dr.setFilter("name", "Equals", genomeName);

        //page has loaded
        dr = new DataRegionTable("query", test);
        test.clickAndWait(dr.link(0, "Genome Name"));
        test.waitForElement(Locator.tagContainingText("span", "Aliases for External Browsers or Databases"));

        //first do single file
        File dataDir = TestFileUtils.getSampleData("sequenceAnalysis/genomeAnnotations");
        File fileRoot = TestFileUtils.getDefaultFileRoot(projectName);

        File f = new File(dataDir, "fakeData.bed");
        f = replaceContigName(f, new File(fileRoot, "fakeData.bed"), genomeName);

        test.log("adding track: " + f.getName());
        DataRegionTable dr2 = DataRegionTable.findDataRegionWithinWebpart(test, "Annotations/Tracks");
        dr2.clickHeaderButton("Add Annotation");
        test.waitForElement(Ext4Helper.Locators.window("Import Annotations/Tracks"));
        Ext4FieldRef.getForLabel(test, "Track Name").setValue(LabModuleHelper.getBaseName(f.getName()));
        Ext4FieldRef.getForLabel(test, "Description").setValue("This is the track description");
        Ext4FileFieldRef fileField = test._ext4Helper.queryOne("field[fieldLabel=File]", Ext4FileFieldRef.class);
        fileField.setToFile(f);

        test.waitAndClick(Ext4Helper.Locators.ext4ButtonEnabled("Submit"));
        test.waitForElement(Ext4Helper.Locators.window("Success"));
        test.waitAndClickAndWait(Ext4Helper.Locators.ext4ButtonEnabled("OK"));
        test.waitAndClickAndWait(Locator.tagWithText("a", "All"));

        startedPipelineJobs++;
        test.waitForPipelineJobsToComplete(startedPipelineJobs, "Import Genome Tracks", false);

        //now test bulk import
        List<String> fileNames = Arrays.asList("fakeData.gff", "fakeData.bed");
        for (String fn : fileNames)
        {
            File target = new File(fileRoot, fn);
            if (target.exists())
            {
                target.delete();
            }

            replaceContigName(new File(dataDir, fn), target, genomeName);
        }

        LabModuleHelper helper = new LabModuleHelper(test);
        helper.initiatePipelineJob("Import Genome Tracks", fileNames, projectName);

        Ext4GridRef grid = test._ext4Helper.queryOne("grid", Ext4GridRef.class);
        grid.setClicksToEdit(1);
        grid.setGridCellJS(1, "name", "Track1");
        grid.setGridCellJS(1, "libraryId", genomeId);

        test.waitAndClick(Ext4Helper.Locators.ext4ButtonEnabled("Submit"));
        test.waitForElement(Ext4Helper.Locators.window("Error"));
        test.waitAndClick(Ext4Helper.Locators.ext4ButtonEnabled("OK"));

        grid.setGridCellJS(2, "name", "Track2");
        grid.setGridCellJS(2, "libraryId", genomeId);

        test.waitAndClick(Ext4Helper.Locators.ext4ButtonEnabled("Submit"));
        test.waitForElement(Ext4Helper.Locators.window("Success"));
        test.waitAndClickAndWait(Ext4Helper.Locators.ext4ButtonEnabled("OK"));
        test.waitAndClickAndWait(Locator.tagWithText("a", "All"));

        startedPipelineJobs = startedPipelineJobs + 2;
        test.waitForPipelineJobsToComplete(startedPipelineJobs, "Import Tracks", false);

        return startedPipelineJobs;
    }

    private static File replaceContigName(File input, File output, String genomeName) throws IOException
    {
        try (BufferedReader reader = Readers.getReader(input); PrintWriter writer = PrintWriters.getPrintWriter(output))
        {
            String line;
            while ((line = reader.readLine()) != null)
            {
                writer.println(line.replaceAll("SIVmac239", "SIVmac239_Test"));
            }
        }

        return output;
    }

    public static void addOutputFile(BaseWebDriverTest test, File toAdd, String genomeName, String name, String category, String description, boolean isWorkbook) throws Exception
    {
        test.log("adding output file: " + toAdd.getName());
        test.beginAt("/query/" + test.getContainerId() + "/executeQuery.view?query.queryName=outputfiles&schemaName=sequenceanalysis");

        DataRegionTable dr = new DataRegionTable("query", test);
        dr.clickHeaderButton("Import Files");
        if (!isWorkbook)
        {
            test.waitForElement(Ext4Helper.Locators.window("Import Sequence Output File"));
            test.waitAndClick(Ext4Helper.Locators.ext4Button("Submit"));
        }

        test.waitForElement(Ext4Helper.Locators.window("Import Output File"));
        Ext4FieldRef.getForLabel(test, "Name").setValue(name);
        Ext4ComboRef.getForLabel(test, "Category").setValue(category);
        Ext4FieldRef.getForLabel(test, "Description").setValue(description);
        Ext4ComboRef.getForLabel(test, "Reference Genome").setComboByDisplayValue(genomeName);

        Ext4FileFieldRef fileField = test._ext4Helper.queryOne("field[fieldLabel=File]", Ext4FileFieldRef.class);
        fileField.setToFile(toAdd);

        test.waitAndClick(Ext4Helper.Locators.ext4ButtonEnabled("Submit"));
        test.waitForElement(Ext4Helper.Locators.window("Success"));
        test.waitAndClickAndWait(Ext4Helper.Locators.ext4ButtonEnabled("OK"));
    }

    private void basicOutputFilesTest(boolean sequencePipelineEnabled) throws Exception
    {
        File testBamSource = new File(_sampleData, "test.bam");

        if (!testBamSource.exists())
        {
            throw new RuntimeException("BAM not found: " + testBamSource.getPath());
        }

        File testBam = new File(TestFileUtils.getDefaultFileRoot(getProjectName()), "test.bam");
        if (!testBam.exists())
        {
            FileUtils.copyFile(testBamSource, testBam);
            FileUtils.copyFile(new File(testBamSource.getPath() + ".bai"), new File(testBam.getPath() + ".bai"));
        }
        SequenceTest.addOutputFile(this, testBam, SequenceTest.TEST_GENOME_NAME, "TestBAM", "BAM File", "This is an output file", false);

        goToProjectHome();
        waitForElement(Locator.tagWithText("span", "BAM File:"));

        //click BAM file
        beginAt("/query/" + getProjectName() + "/executeQuery.view?schemaName=sequenceanalysis&query.queryName=outputfiles");
        DataRegionTable dr = DataRegionTable.DataRegion(getDriver()).withName("query").waitFor();

        //click visualize/analyze
        dr.setFilter("Name", "Equals", "TestBAM");
        Assert.assertEquals(1, dr.getDataRowCount());
        dr.checkAllOnPage();
        dr.clickHeaderButton("Visualize/Analyze Data");

        //verify some of the handlers present
        waitForElement(Ext4Helper.Locators.window("Visualize/Analyze Files"));
        waitForElement(Locator.tagWithText("div", "Calculate Local Haplotypes"));
        assertElementPresent(Locator.tagWithText("div", "Cleanup BAM File"));
        assertElementPresent(Locator.tagWithText("div", "Picard Metrics"));

        //run one of them
        click(Locator.tagWithText("div", "Picard Metrics"));
        waitAndClick(Ext4Helper.Locators.ext4Button("Submit"));

        //workbook window
        waitForElement(Ext4Helper.Locators.window("Create New Workbook or Add To Existing?"));
        waitAndClick(Ext4Helper.Locators.ext4Button("Submit"));

        //tool window
        waitForElement(Ext4Helper.Locators.window("Picard Metrics"));

        if (sequencePipelineEnabled)
        {
            waitAndClickAndWait(Ext4Helper.Locators.ext4Button("Submit"));

            _startedPipelineJobs++;
            waitForElement(Locator.tagContainingText("span", "Data Pipeline"));
            waitForPipelineJobsToComplete(_startedPipelineJobs, "Picard Metrics", false);
            assertTextPresent("COMPLETE");
        }
        else
        {
            log("skipping pipeline job since sequence pipeline is not enabled");
        }

        //make sure custom delete actions work:
        goToProjectHome();

        Connection cn = new Connection(getBaseURL(), PasswordUtil.getUsername(), PasswordUtil.getPassword());
        SelectRowsCommand sr = new SelectRowsCommand("sequenceanalysis", "outputfiles");
        sr.addFilter(new Filter("category", "BAM File", Filter.Operator.EQUAL));
        sr.setColumns(Arrays.asList("dataid/DataFileUrl", "rowid"));
        SelectRowsResponse srr = sr.execute(cn, getProjectName());
        File existing = new File(URI.create(srr.getRows().get(0).get("dataid/DataFileUrl").toString()));
        Integer rowId = Integer.valueOf(srr.getRows().get(0).get("rowid").toString());

        Assert.assertTrue("File does not exist: " + existing.getPath(), existing.exists());
        Assert.assertEquals("Expected one row", 1, srr.getRowCount().intValue());

        beginAt("/query/" + getProjectName() + "/executeQuery.view?schemaName=sequenceanalysis&query.queryName=outputfiles&query.category~eq=BAM File");

        dr = new DataRegionTable.DataRegionFinder(getDriver()).withName("query").waitFor();
        dr.checkAllOnPage();
        Assert.assertEquals("Should have one row", 1, dr.getCheckedCount());

        List<String> drRowIDs = dr.getColumnDataAsText("rowid");
        log("RowIds from DR: " + StringUtils.join(drRowIDs, ","));
        log("Categories from DR: " + StringUtils.join(dr.getColumnDataAsText("category"), ","));

        dr.clickHeaderButtonAndWait("Delete");
        Locator l = Locator.tagContainingText("div", "Are you sure you want to delete the following 1 output files");
        waitForElement(l);
        log("Complete text: " + l.findElement(getDriver()).getText());

        clickButton("OK");

        goToProjectHome();
        sleep(3000);

        //make sure file actually deleted
        SelectRowsCommand sr2 = new SelectRowsCommand("sequenceanalysis", "outputfiles");
        sr2.addFilter(new Filter("category", "BAM File", Filter.Operator.EQUAL));
        sr2.setColumns(Arrays.asList("rowid"));
        SelectRowsResponse srr2 = sr2.execute(cn, getProjectName());
        if (srr2.getRowCount().intValue() > 0)
        {
            Integer rowId2 = Integer.valueOf(srr.getRows().get(0).get("rowid").toString());
            log("initial rowId: " + rowId + ", after: " + rowId2);
        }

        Assert.assertEquals("Output file not deleted", 0, srr2.getRowCount().intValue());

        Assert.assertFalse("File still exists: " + existing.getPath(), existing.exists());
    }
}