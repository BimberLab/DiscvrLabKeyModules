/*
 * Copyright (c) 2015 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.remoteapi.CommandException;
import org.labkey.remoteapi.Connection;
import org.labkey.remoteapi.query.Filter;
import org.labkey.remoteapi.query.InsertRowsCommand;
import org.labkey.remoteapi.query.SelectRowsCommand;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.TestFileUtils;
import org.labkey.test.TestTimeoutException;
import org.labkey.test.WebTestHelper;
import org.labkey.test.categories.External;
import org.labkey.test.categories.LabModule;
import org.labkey.test.components.ext4.Window;
import org.labkey.test.util.DataRegionTable;
import org.labkey.test.util.Ext4Helper;
import org.labkey.test.util.PasswordUtil;
import org.labkey.test.util.PortalHelper;
import org.labkey.test.util.ext4cmp.Ext4ComboRef;
import org.labkey.test.util.ext4cmp.Ext4FieldRef;
import org.labkey.test.util.external.labModules.LabModuleHelper;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collector;
import java.util.stream.Collectors;

/**
 * Created by bimber on 1/20/2015.
 */

//disabled until we have a solution to install the jbrowse scripts on team city
@Category({External.class, LabModule.class})
public class JBrowseTest extends BaseWebDriverTest
{
    protected LabModuleHelper _helper = new LabModuleHelper(this);

    @Override
    protected String getProjectName()
    {
        return "JBrowseVerifyProject";
    }

    @Test
    public void testSteps() throws Exception
    {
        setUpTest();

        //testOutputFileProcessing();

        /*testDemoNoSession();
        testDemoUi();
        testConfigWidgetUi();
        testMessageDisplay();
        testSessionCardDisplay();
        testTitleMapping();
        testPredictedFunction();
        testAlleleFrequencies();
        testGenotypeFrequencies();
        testFilterWidgetOpens();
        testAddingNumericFilter();
        testAddingOptionFilter();
        testLoadingConfigFilters();
        testRemovingFilters();
        testInvalidFilterHandling();*/
        testColorWidgetOpens();
        testDefaultImpactColor();
        testAFColor();
    }

    private void testColorWidgetOpens()
    {
        beginAt("/home/jbrowse-jbrowse.view?session=mgap");
        waitForJBrowseToLoad();

        //Actions actions = new Actions(getDriver());
        waitAndClick(Locator.xpath("//button[@data-testid='track_menu_icon']"));
        waitAndClick(Locator.xpath("//span[contains(text(), 'Color')]"));
        assertElementPresent(Locator.xpath("//h6[contains(text(), 'Color Schemes')]"));
    }

    private void testDefaultImpactColor()
    {
        beginAt("/home/jbrowse-jbrowse.view?session=mgap");
        waitForJBrowseToLoad();

        waitAndClick(Locator.xpath("//button[@data-testid='track_menu_icon']"));
        waitAndClick(Locator.xpath("//span[contains(text(), 'Color')]"));
        assertElementPresent(Locator.tagWithText("td", "HIGH"));
        assertElementPresent(Locator.tagWithAttribute("div", "fill", "red"));
        assertElementPresent(Locator.tagWithAttribute("polygon", "fill", "red"));

        assertElementPresent(Locator.tagWithText("td", "MODERATE"));
        assertElementPresent(Locator.tagWithAttribute("div", "fill", "goldenrod"));
        assertElementPresent(Locator.tagWithAttribute("polygon", "fill", "goldenrod"));

        assertElementPresent(Locator.tagWithText("td", "LOW"));
        assertElementPresent(Locator.tagWithAttribute("div", "fill", "#049931"));
        assertElementPresent(Locator.tagWithAttribute("polygon", "fill", "#049931"));

        assertElementPresent(Locator.tagWithText("td", "Other"));
        assertElementPresent(Locator.tagWithAttribute("div", "fill", "gray"));
        assertElementPresent(Locator.tagWithAttribute("polygon", "fill", "gray"));

    }

    private void testAFColor()
    {
        beginAt("/home/jbrowse-jbrowse.view?session=mgap");
        waitForJBrowseToLoad();

        waitAndClick(Locator.xpath("//button[@data-testid='track_menu_icon']"));
        waitAndClick(Locator.xpath("//span[contains(text(), 'Color')]"));
        waitAndClick(Locator.tagWithId("div", "category-select"));
        waitAndClick(Locator.xpath("//li[@data-value = 'AF']"));
        assertElementPresent(Locator.tagWithText("td", "0.000"));
        assertElementPresent(Locator.tagWithAttribute("div", "fill", "#0C28F9"));
        assertElementPresent(Locator.tagWithText("td", "1.000"));
        assertElementPresent(Locator.tagWithAttribute("div", "fill", "#F90C00"));
        assertElementPresent(Locator.tagWithText("td", "Other"));
        assertElementPresent(Locator.tagWithAttribute("div", "fill", "gray"));

        waitForElement(Locator.tagWithAttribute("polygon", "fill", "#1527EF"));
        assertElementPresent(Locator.tagWithAttribute("polygon", "fill", "#1527EF"));
    }
    private void testFilterWidgetOpens()
    {
        beginAt("/home/jbrowse-jbrowse.view?session=mgap");
        waitForJBrowseToLoad();

        Actions actions = new Actions(getDriver());
        waitAndClick(Locator.xpath("//button[@data-testid='track_menu_icon']"));
        waitAndClick(Locator.xpath("//span[contains(text(), 'Filter')]"));
        assertElementPresent(Locator.xpath("//h6[contains(text(), 'Filters')]"));

    }

    private void testAddingNumericFilter(){
        beginAt("/home/jbrowse-jbrowse.view?session=mgap");
        waitForJBrowseToLoad();
        waitAndClick(Locator.xpath("//button[@data-testid='track_menu_icon']"));

        waitAndClick(Locator.xpath("//span[contains(text(), 'Filter')]"));
        waitAndClick(Locator.xpath("//div[em[contains(text(), 'Add New Filter...')]]"));
        waitAndClick(Locator.xpath("//li[@data-value = 'AC']"));
        waitForElement(Locator.tagWithText("td", "AC"));
        waitForElement(Locator.tagWithClassContaining("div", "formControl"));

        waitForElement(Locator.tagWithText("td", "AC").followingSibling("td"));
        waitAndClick(Locator.tagWithText("em", "Operator"));
        waitAndClick(Locator.tagContainingText("li", "<"));
        By numInput = Locator.xpath("//input[@type='number']");
        WebElement input = getDriver().findElement(numInput);
        String str = "1";
        input.sendKeys(str);
        assert(isVariantVisible("mgap_hg38", "SNV G -> A,C", true));
        assert(!isVariantVisible("mgap_hg38", "SNV A -> T", true));

    }

    private void testAddingOptionFilter(){
        beginAt("/home/jbrowse-jbrowse.view?session=mgap");
        waitForJBrowseToLoad();
        waitAndClick(Locator.xpath("//button[@data-testid='track_menu_icon']"));

        waitAndClick(Locator.xpath("//span[contains(text(), 'Filter')]"));
        waitAndClick(Locator.xpath("//div[em[contains(text(), 'Add New Filter...')]]"));
        waitAndClick(Locator.xpath("//li[@data-value = 'IMPACT']"));
        assertElementPresent(Locator.xpath("//td[text()='IMPACT']"));
        waitAndClick(Locator.xpath("//td[text()='IMPACT']/../*[2]/div"));
        waitAndClick(Locator.xpath("//li[contains(text(), '=')]"));
        waitAndClick(Locator.xpath("//td[text()='IMPACT']/../*[3]/div"));
        waitAndClick(Locator.xpath("//li[@data-value = 'HIGH']"));
        assert(isVariantVisible("mgap_hg38", "SNV A -> T", true));
        assert(!isVariantVisible("mgap_hg38", "SNV T -> C", true));

    }

    private void testLoadingConfigFilters(){
        beginAt("/home/jbrowse-jbrowse.view?session=mgapf");
        waitForJBrowseToLoad();
        assert(isVariantVisible("mgap_hg38", "SNV T -> C", true));
        waitAndClick(Locator.xpath("//button[@data-testid='track_menu_icon']"));
        waitAndClick(Locator.xpath("//span[contains(text(), 'Filter')]"));
        assertElementPresent(Locator.xpath("//td[text()='IMPACT']"));
        assertElementPresent(Locator.xpath("//td[text()='AC']"));
        assertElementPresent(Locator.xpath("//td[text()='AF']"));

    }

    private void testRemovingFilters(){
        beginAt("/home/jbrowse-jbrowse.view?session=mgapf");
        waitForJBrowseToLoad();
        waitAndClick(Locator.xpath("//button[@data-testid='track_menu_icon']"));
        waitAndClick(Locator.xpath("//span[contains(text(), 'Filter')]"));
        waitAndClick(Locator.xpath("//td[text()='AF']/..//button[@title='Remove filter']"));
        assert(isVariantVisible("mgap_hg38", "SNV T -> C", true));
        assert(isVariantVisible("mgap_hg38", "SNV C -> T", true));
    }

    private void testInvalidFilterHandling(){
        beginAt("/home/jbrowse-jbrowse.view?session=mgapif");
        waitForJBrowseToLoad();
        assert(isVariantVisible("mgap_hg38", "SNV A -> T", true));
        assert(!isVariantVisible("mgap_hg38", "SNV T -> C", true));
    }

    private void testDemoNoSession()
    {
        beginAt("/home/jbrowse-jbrowse.view?");
        waitForElement(Locator.tagWithText("p", "Error - no session provided."));
    }

    private void testDemoUi()
    {
        beginAt("/home/jbrowse-jbrowse.view?session=demo");
        waitForJBrowseToLoad();

        Actions actions = new Actions(getDriver());
        By by = getVariantWithinTrack("clinvar_ncbi_hg38_2", "294665", true);
        WebElement toClick = getDriver().findElements(by).stream().filter(WebElement::isDisplayed).findFirst().orElseThrow();
        actions.click(toClick).perform();
        assertElementPresent(Locator.tagWithText("th", "Hello"));
    }

    private void testConfigWidgetUi()
    {
        beginAt("/home/jbrowse-jbrowse.view?session=demo");
        waitForJBrowseToLoad();

        // 294665 is a visible element given minimalSession's location
        Actions actions = new Actions(getDriver());
        By by = getVariantWithinTrack("clinvar_ncbi_hg38", "294665", true);
        WebElement toClick = getDriver().findElements(by).stream().filter(WebElement::isDisplayed).findFirst().orElseThrow();
        actions.click(toClick).perform();
        waitForElement(Locator.tagWithText("div", "1:197,268,209..197,268,209"));
        assertElementPresent(Locator.tagWithText("span", "Predicted Function - 1"));
    }


    private By getVariantWithinTrack(String trackId, String variantText, boolean waitFor)
    {
        try
        {
            trackId = "trackRenderingContainer-linearGenomeView-" + trackId;
            Locator.XPathLocator l = Locator.tagWithAttributeContaining("div", "data-testid", trackId);
            if (waitFor)
            {
                waitForElement(l);
            }

            l = l.append(Locator.xpath("//*[name()='text' and contains(text(), '" + variantText + "')]/..")).notHidden();

            if (waitFor)
            {
                waitForElement(l);
            }

            waitForElementToDisappear(Locator.tagWithText("p", "Loading"));
            sleep(250);

            return By.xpath(l.toXpath());
        }
        catch(Exception e)
        {
            return null;
        }
    }

    private boolean isVariantVisible(String trackId, String variantText, boolean waitFor){
        By var = getVariantWithinTrack(trackId, variantText, waitFor);
        if(var != null){
            return true;
        }
        return false;
    }

    private void testMessageDisplay()
    {
        beginAt("/home/jbrowse-jbrowse.view?session=mgap");
        waitForJBrowseToLoad();

        Actions actions = new Actions(getDriver());
        WebElement toClick = getDriver().findElements(getVariantWithinTrack("mgap_hg38", "SNV A -> T", true)).stream().filter(WebElement::isDisplayed).collect(toSingleton());
        actions.click(toClick).perform();
        waitForElement(Locator.tagContainingText("div", "Aut molestiae temporibus nesciunt."));
    }

    private void waitForJBrowseToLoad()
    {
        waitForElementToDisappear(Locator.tagWithText("p", "Loading..."));
        waitForElement(Locator.tagWithClass("span", "MuiIconButton-label").notHidden()); //this is the top-left icon
        waitForElement(Locator.tagWithClassContaining("span", "MuiTypography-root").notHidden(), WAIT_FOR_PAGE); //this is the icon from the track label

        waitForElementToDisappear(Locator.tagWithText("div", "Loading...")); //track data
    }

    private void testSessionCardDisplay()
    {
        beginAt("/home/jbrowse-jbrowse.view?session=mgap");
        waitForJBrowseToLoad();

        Actions actions = new Actions(getDriver());
        WebElement toClick = getDriver().findElements(getVariantWithinTrack("mgap_hg38", "SNV A -> T", true)).stream().filter(WebElement::isDisplayed).collect(toSingleton());
        actions.click(toClick).perform();
        waitForElement(Locator.tagWithText("span", "AC, AF"));
        waitForElement(Locator.tagWithText("div", "HIGH")); //the IMPACT field calculated in ExtendedVariantAdapter
    }

    private void testTitleMapping()
    {
        beginAt("/home/jbrowse-jbrowse.view?session=mgap&location=1:116981373..116981544");
        waitForJBrowseToLoad();

        Actions actions = new Actions(getDriver());
        WebElement toClick = getDriver().findElements(getVariantWithinTrack("mgap_hg38", "SNV T -> C", true)).stream().filter(WebElement::isDisplayed).collect(toSingleton()); // 1:116,981,406..116,981,406
        actions.click(toClick).perform();
        waitForElement(Locator.tagWithText("div", "1:116,981,406..116,981,406"));
        assertElementPresent(Locator.tagWithText("div", "Minor Allele Frequency"));
    }

    private void testPredictedFunction()
    {
        beginAt("/home/jbrowse-jbrowse.view?session=mgap&location=1:116981373..116981544");
        waitForJBrowseToLoad();

        Actions actions = new Actions(getDriver());
        WebElement toClick = getDriver().findElements(getVariantWithinTrack("mgap_hg38", "SNV T -> C", true)).stream().filter(WebElement::isDisplayed).collect(toSingleton()); // 1:116,981,406..116,981,406
        actions.click(toClick).perform();
        waitForElement(Locator.tagWithText("div", "1:116,981,406..116,981,406"));
        assertElementPresent(Locator.tagWithText("th", "Effect"));
        assertElementPresent(Locator.tagWithText("th", "Impact"));
        assertElementPresent(Locator.tagWithText("th", "Gene Name"));
        assertElementPresent(Locator.tagWithText("th", "Position/Consequence"));
        assertElementPresent(Locator.tagWithText("td", "intron_variant"));
        assertElementPresent(Locator.tagWithText("td", "custom"));
    }

    private void testAlleleFrequencies()
    {
        beginAt("/home/jbrowse-jbrowse.view?session=mgap&location=1:116999734..116999776");
        waitForJBrowseToLoad();

        Actions actions = new Actions(getDriver());
        WebElement toClick = getDriver().findElements(getVariantWithinTrack("mgap_hg38", "SNV C -> A", true)).stream().filter(WebElement::isDisplayed).collect(toSingleton()); // 1:116,999,755
        actions.click(toClick).perform();
        waitForElement(Locator.tagWithText("div", "1:116,999,755..116,999,755"));
        assertElementPresent(Locator.tagWithText("th", "Sequence"));
        assertElementPresent(Locator.tagWithText("th", "Fraction"));
        assertElementPresent(Locator.tagWithText("th", "Count"));
        assertElementPresent(Locator.tagWithText("td", "3041"));
        assertElementPresent(Locator.tagWithText("td", "3"));
    }

    private void testGenotypeFrequencies()
    {
        beginAt("/home/jbrowse-jbrowse.view?session=mgap&location=1:116999734..116999776");
        waitForJBrowseToLoad();

        Actions actions = new Actions(getDriver());
        WebElement toClick = getDriver().findElements(getVariantWithinTrack("mgap_hg38", "SNV C -> A", true)).stream().filter(WebElement::isDisplayed).collect(toSingleton()); // 1:116,999,755
        actions.click(toClick).perform();
        waitForElement(Locator.tagWithText("div", "1:116,999,755..116,999,755"));
        assertElementPresent(Locator.tagWithText("td", "3041"));
        assertElementPresent(Locator.tagWithText("span", "Genotype Frequency (2329)"));
        assertElementPresent(Locator.tagWithText("a", "Click here to view sample-level genotypes"));
        while(isTextPresent("Loading")){
            sleep(10);
        }
        assertElementPresent(Locator.tagWithAttributeContaining("div","id","reactgooglegraph"));
    }

    @Override
    protected void doCleanup(boolean afterTest) throws TestTimeoutException
    {
        super.doCleanup(afterTest);
    }

    protected void setUpTest() throws Exception
    {
        _containerHelper.createProject(getProjectName(), "Sequence Analysis");
        _containerHelper.enableModule("JBrowse");

        //create genome and add resources.
        Integer genomeId = SequenceTest.createReferenceGenome(this, 1);
        createGenomeFeatures(genomeId);

        SequenceTest.addReferenceGenomeTracks(this, getProjectName(), SequenceTest.TEST_GENOME_NAME, genomeId, 1);
    }

    private void createGenomeFeatures(int genomeId) throws IOException, CommandException
    {
        Connection cn = new Connection(WebTestHelper.getBaseURL(), PasswordUtil.getUsername(), PasswordUtil.getPassword());
        SelectRowsCommand sr = new SelectRowsCommand("sequenceanalysis", "reference_library_members");
        sr.addFilter(new Filter("library_id", genomeId, Filter.Operator.EQUAL));
        sr.setColumns(Arrays.asList("ref_nt_id"));
        Integer refNtId = (int)sr.execute(cn, getContainerId()).getRows().get(0).get("ref_nt_id");

        InsertRowsCommand ic = new InsertRowsCommand("sequenceanalysis", "ref_aa_sequences");
        ic.addRow(Map.of("ref_nt_id", refNtId, "name", "AA1", "exons", "1-30;60-68", "isComplement", false, "start_location", 1, "sequence", "AAAAAAAAAAAAA"));
        ic.addRow(Map.of("ref_nt_id", refNtId, "name", "AA2", "exons", "101-130;160-168", "isComplement", true, "start_location", 100, "sequence", "AAAAAAAAAAAAA"));

        ic.execute(cn, getContainerId());

        ic = new InsertRowsCommand("sequenceanalysis", "ref_nt_features");
        ic.addRow(Map.of("ref_nt_id", refNtId, "category", "Feature", "nt_start", 10, "nt_stop", 100, "name", "Feature1"));
        ic.addRow(Map.of("ref_nt_id", refNtId, "category", "Feature", "nt_start", 200, "nt_stop", 300, "name", "Feature2"));
        ic.execute(cn, getContainerId());
    }

    @Override
    public List<String> getAssociatedModules()
    {
        return Arrays.asList("jbrowse");
    }

    private void testOutputFileProcessing() throws Exception
    {
        goToProjectHome();

        //import BAM, VCF, BED, GFF
        File dataDir = TestFileUtils.getSampleData("sequenceAnalysis/genomeAnnotations");
        File fileRoot = TestFileUtils.getDefaultFileRoot(getProjectName());
        for (File f : dataDir.listFiles())
        {
            File target = SequenceTest.replaceContigName(f, SequenceTest.GENOME_SEQ_NAME);
            SequenceTest.addOutputFile(this, target, SequenceTest.TEST_GENOME_NAME, target.getName(), "Gene Annotations", "This is an output file", false);
        }

        File testBam = new File(SequenceTest._sampleData, "test.bam");
        SequenceTest.addOutputFile(this, testBam, SequenceTest.TEST_GENOME_NAME, "TestBAM", "BAM File", "This is an output file", false);

        //create session w/ some of these, verify
        log("creating initial jbrowse session");
        beginAt("/query/" + getContainerId() + "/executeQuery.view?query.queryName=outputfiles&schemaName=sequenceanalysis");
        DataRegionTable dr = DataRegionTable.DataRegion(getDriver()).find();
        dr.uncheckAllOnPage();
        dr.checkCheckbox(0);
        dr.clickHeaderButton("Visualize/Analyze Data");
        new Window.WindowFinder(getDriver()).withTitle("Visualize/Analyze Files").waitFor();
        waitForElement(Locator.tagWithText("div", "View In JBrowse"));
        click(Locator.tagWithText("div", "View In JBrowse"));
        waitAndClick(Ext4Helper.Locators.ext4Button("Submit"));

        String sessionName = "TestSession1";
        int existingPipelineJobs = SequenceTest.getTotalPipelineJobs(this);
        new Window.WindowFinder(getDriver()).withTitle("Create/Modify JBrowse Session").waitFor();
        Ext4FieldRef.getForLabel(this, "Name").setValue(sessionName);
        Ext4FieldRef.getForLabel(this, "Description").setValue("This is the first session with BAM");
        waitAndClick(Ext4Helper.Locators.ext4ButtonEnabled("Submit"));

        Window window = new Window.WindowFinder(getDriver()).withTitle("Create New Workbook or Add To Existing?").waitFor();
        window.clickButton("Submit", 0);

        window = new Window.WindowFinder(getDriver()).withTitle("Success").waitFor();
        window.clickButton("OK");
        waitForPipelineJobsToComplete(existingPipelineJobs + 1, "Create New Session", false);

        //add additional file to session, verify
        beginAt("/query/" + getContainerId() + "/executeQuery.view?query.queryName=outputfiles&schemaName=sequenceanalysis");

        existingPipelineJobs = SequenceTest.getTotalPipelineJobs(this);
        dr = DataRegionTable.DataRegion(getDriver()).find();
        dr.uncheckAllOnPage();
        dr.checkCheckbox(1);
        dr.checkCheckbox(2);
        dr.clickHeaderButton("Visualize/Analyze Data");
        new Window.WindowFinder(getDriver()).withTitle("Visualize/Analyze Files").waitFor();
        waitForElement(Locator.tagWithText("div", "View In JBrowse"));
        click(Locator.tagWithText("div", "View In JBrowse"));
        waitAndClick(Ext4Helper.Locators.ext4Button("Submit"));

        new Window.WindowFinder(getDriver()).withTitle("Create/Modify JBrowse Session").waitFor();
        Ext4FieldRef.getForBoxLabel(this, "Add To Existing Session").setChecked(true);
        Ext4FieldRef.waitForField(this, "Session");
        Ext4ComboRef.getForLabel(this, "Session").setComboByDisplayValue(sessionName);
        waitAndClick(Ext4Helper.Locators.ext4ButtonEnabled("Submit"));
        new Window.WindowFinder(getDriver()).withTitle("Success").waitFor();
        waitAndClickAndWait(Ext4Helper.Locators.ext4ButtonEnabled("OK"));
        waitForPipelineJobsToComplete(existingPipelineJobs + 1, "Add To Existing Session", false);

        beginAt("/query/" + getContainerId() + "/executeQuery.view?query.queryName=jsonfiles&schemaName=jbrowse");
        existingPipelineJobs = SequenceTest.getTotalPipelineJobs(this);
        dr = DataRegionTable.DataRegion(getDriver()).find();
        dr.checkAllOnPage();
        dr.clickHeaderMenu("More Actions", false, "Re-process Selected");
        new Window.WindowFinder(getDriver()).withTitle("Reprocess Resources").waitFor();
        waitAndClick(Ext4Helper.Locators.ext4ButtonEnabled("Submit"));
        new Window.WindowFinder(getDriver()).withTitle("Success").waitFor();
        waitAndClickAndWait(Ext4Helper.Locators.ext4ButtonEnabled("OK"));
        waitForPipelineJobsToComplete(existingPipelineJobs + 1, "Recreating Resources", false);

        beginAt("/project/" + getContainerId() + "/begin.view");
        _helper.clickNavPanelItemAndWait("JBrowse Sessions:", 1);
        waitAndClickAndWait(Locator.tagWithText("a", "View In JBrowse"));
        waitForElement(Locator.tagWithText("div", "TestGenome1"));

        beginAt("/project/" + getContainerId() + "/begin.view");
        _helper.clickNavPanelItemAndWait("JBrowse Sessions:", 1);
        dr = DataRegionTable.DataRegion(getDriver()).find();
        dr.clickRowDetails(0);

        waitForElement(Locator.tagWithText("span", "Resources Displayed In This Session"));
        dr = DataRegionTable.findDataRegionWithinWebpart(this, "Resources Displayed In This Session");
        Assert.assertEquals("Incorrect row count", 3, dr.getDataRowCount());

        waitForElement(Locator.tagWithText("span", "Tracks Provided By This Session"));
        dr = DataRegionTable.findDataRegionWithinWebpart(this, "Tracks Provided By This Session");
        Assert.assertEquals("Incorrect row count", 3, dr.getDataRowCount());
        dr.checkCheckbox(dr.getRowIndex("File Id", "fakeData.gff"));
        dr.checkCheckbox(dr.getRowIndex("File Id", "fakeData.bed"));
        dr.clickHeaderMenu("More Actions", false, "Modify Track Config");
        new Window.WindowFinder(getDriver()).withTitle("Modify Track Config").waitFor();
        waitAndClick(Ext4Helper.Locators.ext4ButtonEnabled("Add"));
        waitAndClick(Ext4Helper.Locators.menuItem("Visible By Default"));
        waitForElement(Locator.tagWithText("div", "visibleByDefault"));
        waitAndClick(Ext4Helper.Locators.ext4ButtonEnabled("Submit"));
        new Window.WindowFinder(getDriver()).withTitle("Success").waitFor();
        waitAndClick(Ext4Helper.Locators.ext4ButtonEnabled("OK"));

        waitForElement(Locator.tagWithText("span", "Additional Tracks Provided By The Base Genome"));
        dr = DataRegionTable.findDataRegionWithinWebpart(this, "Additional Tracks Provided By The Base Genome");
        Assert.assertEquals("Incorrect row count", 3, dr.getDataRowCount());

        // Store session ID for later use
        String sessionId = StringUtils.trimToNull(getUrlParam("databaseId"));
        Assert.assertNotNull("Missing session ID on URL", sessionId);

        // Now ensure default tracks appear:
        beginAt("/project/" + getContainerId() + "/begin.view");
        _helper.clickNavPanelItemAndWait("JBrowse Sessions:", 1);
        waitAndClickAndWait(Locator.tagWithText("a", "View In JBrowse"));
        waitForElement(Locator.tagWithText("div", "TestGenome1"));
        waitAndClick(Locator.tagContainingText("span", "Show all regions in assembly").withClass("MuiButton-label"));
        waitForElement(Locator.tagWithText("span", "fakeData.gff").withClass("MuiTypography-root"));
        waitForElement(Locator.tagWithText("span", "fakeData.bed").withClass("MuiTypography-root"));

        //Now test search:
        testSearch(sessionId);
    }

    private void testSearch(String sessionId) throws Exception
    {
        goToProjectHome();

        PortalHelper portalHelper = new PortalHelper(this);
        portalHelper.addSideWebPart("JBrowse Search");

        waitForElement(Locator.tagWithText("p", "No session Id provided. Please have you admin use the customize icon to set the session ID for this webpart."));
        portalHelper.clickWebpartMenuItem("JBrowse Search", false, "Customize");
        Window window = new Window.WindowFinder(getDriver()).withTitle("Customize Webpart").waitFor();
        Ext4FieldRef.waitForField(this, "Target JBrowse DB");
        Ext4FieldRef.getForLabel(this, "Target JBrowse DB").setValue(sessionId);
        window.clickButton("Submit", WAIT_FOR_PAGE);

        String search = "Ga";
        String optionText = "Gag";
        String expected = "SIVmac239_Test:10373..10493";

        Locator searchLocator = Locator.tagWithClass("input", "MuiInputBase-input");
        waitForElement(searchLocator);
        WebElement searchBox = searchLocator.findElement(getDriver());
        searchBox.sendKeys(search);

        Locator optionLocator = Locator.tagWithText("li", optionText);
        waitForElement(optionLocator);
        WebElement locator = optionLocator.findElement(getDriver());
        int locatorIndex = Integer.parseInt(locator.getAttribute("data-option-index"));

        for (int i = 0; i <= locatorIndex; i++)
        {
            searchBox.sendKeys(Keys.ARROW_DOWN);
        }

        doAndWaitForPageToLoad(() -> {
            searchBox.sendKeys(Keys.ENTER);
        });

        waitForJBrowseToLoad();

        waitForElement(Locator.tagWithText("span", "fakeData.gff").withClass("MuiTypography-root"));
        waitForElement(Locator.tagWithText("span", "fakeData.bed").withClass("MuiTypography-root"));
    }

    public static <T> Collector<T, ?, T> toSingleton() {
        return Collectors.collectingAndThen(
                Collectors.toList(),
                list -> {
                    if (list.size() != 1) {
                        throw new IllegalStateException("Expected single element, found: " + list.size());
                    }
                    return list.get(0);
                }
        );
    }

}
