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
import org.apache.commons.lang3.tuple.Pair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
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
import org.labkey.test.util.ext4cmp.Ext4CmpRef;
import org.labkey.test.util.ext4cmp.Ext4ComboRef;
import org.labkey.test.util.ext4cmp.Ext4FieldRef;
import org.labkey.test.util.ext4cmp.Ext4GridRef;
import org.labkey.test.util.external.labModules.LabModuleHelper;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.Color;

import java.io.File;
import java.io.IOException;
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
    public static final String JB_GENOME_NAME = "JB_GRCh37";
    private boolean _searchWebpartAdded = false;

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

        testInferredDetails();
        testNoSession();
        testMessageDisplay();
        testSessionCardDisplay();
        testTitleMapping();
        testPredictedFunction();
        testAlleleFrequencies();
        testGenotypeFrequencies();

        testColorWidget();
        testDefaultColorApplied();
        testAFColor();
        testFilterWidget();

        testLoadingConfigFilters();
        testSampleFilters();
        testSampleFiltersFromUrl();
        testViewTableButton();

        testBrowserNavToVariantTable();
        testGridFailureConditions();
        testVariantTableComparators();

        testOutputFileProcessing();

        testFullTextSearch();
    }

    private void openTrackMenuItem(String name)
    {
        openTrackMenuItem(name, false);
    }

    private void openTrackMenuItem(String name, boolean waitForPage)
    {
        waitAndClick(Locator.tagWithAttribute("button", "data-testid", "track_menu_icon"));
        if (waitForPage)
        {
            waitAndClickAndWait(Locator.tagContainingText("span", name));
        }
        else
        {
            waitAndClick(Locator.tagContainingText("span", name));
        }
    }

    private void testColorWidget()
    {
        beginAt("/" + getProjectName() + "/jbrowse-jbrowse.view?session=mgap");
        waitForJBrowseToLoad();

        openTrackMenuItem("Color Selection");
        waitForElement(Locator.tagWithText("h6", "Color Schemes"));

        // We expect IMPACT to be the default scheme
        assertElementPresent(Locator.tagWithText("td", "HIGH"));
        assertBoxWithColorPresent("#ff0000"); // red
        waitForElement(Locator.tagWithAttribute("polygon", "fill", "red"));

        assertElementPresent(Locator.tagWithText("td", "MODERATE"));
        assertBoxWithColorPresent("#DAA520");  //"goldenrod"
        assertElementPresent(Locator.tagWithAttribute("polygon", "fill", "goldenrod"));

        assertElementPresent(Locator.tagWithText("td", "LOW"));
        assertBoxWithColorPresent("#049931");
        assertElementPresent(Locator.tagWithAttribute("polygon", "fill", "#049931"));

        assertElementPresent(Locator.tagWithText("td", "Other"));
        assertBoxWithColorPresent("#808080"); //gray
        assertElementPresent(Locator.tagWithAttribute("polygon", "fill", "gray"));

        // Now toggle to Allele Freq.:
        waitAndClick(Locator.tagWithText("div", "Predicted Impact"));
        waitAndClick(Locator.tagWithText("li", "Allele Frequency"));

        waitForElement(Locator.tagWithText("td", "0.000 to 0.100"));
        waitForElement(Locator.tagWithText("td", "0.800 to 0.900"));

        clickDialogButton("Apply");

        waitForElement(Locator.tagWithAttribute("polygon", "fill", "#2425E0"));
    }

    private void assertBoxWithColorPresent(final String expectedColor)
    {
        Locator l = Locator.tagWithClass("td", "MuiTableCell-root").child(Locator.tagWithClass("div", "MuiBox-root"));
        waitForElement(l);

        List<WebElement> els = getDriver().findElements(l);
        Assert.assertTrue("Unable to find box", els.stream().anyMatch(el -> {
            String hex = Color.fromString(el.getCssValue("background-color")).asHex();
            return expectedColor.equalsIgnoreCase(hex);
        }));
    }

    private void clickDialogButton(String text)
    {
        waitAndClick(Locator.XPathLocator.tagWithClass("button", "MuiButtonBase-root").withText(text));
    }

    private void testBrowserNavToVariantTable() throws Exception
    {
        beginAt("/" + getProjectName() + "/jbrowse-jbrowse.view?session=mgap");
        waitForJBrowseToLoad();

        waitAndClick(Locator.tagWithAttribute("button", "data-testid", "track_menu_icon"));
        waitAndClickAndWait(Locator.tagContainingText("span", "View As Table"));

        testVariantDataGrid();
    }

    private void testDefaultColorApplied()
    {
        beginAt("/" + getProjectName() + "/jbrowse-jbrowse.view?session=mgapF");
        waitForJBrowseToLoad();

        // Indicates AF scheme applied:
        waitForElement(Locator.tagWithAttribute("polygon", "fill", "#9A1764"));

        openTrackMenuItem("Color Selection");
        waitForElement(Locator.tagWithText("h6", "Color Schemes"));

        waitForElement(Locator.tagWithText("div", "Allele Frequency").withClass("MuiSelect-select"));

        // Now toggle to IMPACT:
        waitAndClick(Locator.tagWithText("div", "Allele Frequency"));
        waitAndClick(Locator.tagWithText("li", "Predicted Impact"));

        waitForElement(Locator.tagWithText("td", "HIGH"));
        assertBoxWithColorPresent("#ff0000"); //red
        clickDialogButton("Apply");

        // Indicates the IMPACT scheme applies:
        waitForElement(Locator.tagWithAttribute("polygon", "fill", "gray"));
    }

    private void testAFColor()
    {
        beginAt("/" + getProjectName() + "/jbrowse-jbrowse.view?session=mgap");
        waitForJBrowseToLoad();

        openTrackMenuItem("Color Selection");

        waitAndClick(Locator.tagWithId("div", "category-select"));
        waitAndClick(Locator.xpath("//li[@data-value = 'AF']"));
        assertElementPresent(Locator.tagWithText("td", "0.000 to 0.100"));
        assertBoxWithColorPresent("#0C28F9");
        assertElementPresent(Locator.tagWithText("td", "0.900 to 1.000"));
        assertBoxWithColorPresent("#E10F19");
        assertElementPresent(Locator.tagWithText("td", "Other"));
        assertBoxWithColorPresent("#808080"); //gray

        clickDialogButton("Apply");
        waitForElement(Locator.tagWithAttribute("polygon", "fill", "#9A1764"));
    }

    private void testFilterWidget()
    {
        beginAt("/" + getProjectName() + "/jbrowse-jbrowse.view?session=mgap");
        waitForJBrowseToLoad();

        openTrackMenuItem("Filter By Attributes");
        waitForElement(Locator.tagWithText("h6", "Filter Variants"));
        clickDialogButton("Add Filter");
        waitAndClick(Locator.tagWithText("li", "Predicted Impact"));

        // text filters should have only one option for operator:
        waitForElement(Locator.tagWithText("div", "=").withClass("MuiSelect-select"));

        waitAndClick(Locator.tagWithClass("div", "MuiSelect-select").index(1));
        waitAndClick(Locator.tagWithText("li", "HIGH"));

        clickDialogButton("Add Filter");
        waitAndClick(Locator.tagWithText("li", "Allele Frequency"));
        waitForElement(Locator.tagWithText("td", "Allele Frequency"));
        clickDialogButton("Apply");

        //Wait for dialog
        waitForElement(Locator.tagWithText("h2", "Invalid Filters"));
        waitAndClick(Locator.tagWithText("button", "OK").withClass("MuiButtonBase-root"));

        // Remove row
        waitAndClick(Locator.tagWithClass("button", "MuiIconButton-sizeSmall").withAttribute("aria-label", "remove filter").index(1));
        clickDialogButton("Apply");

        Assert.assertEquals("Incorrect number of variants", 1, getTotalVariantFeatures());

        // Retry using numeric filter:
        openTrackMenuItem("Filter By Attributes");
        waitForElement(Locator.tagWithText("h6", "Filter Variants"));

        waitAndClick(Locator.tagWithClass("button", "MuiIconButton-sizeSmall").withAttribute("aria-label", "remove filter").index(0));
        clickDialogButton("Add Filter");
        waitAndClick(Locator.tagWithText("li", "Allele Frequency"));

        Locator.XPathLocator valueField = Locator.tagWithClass("div", "MuiInput-underline").index(0);
        waitForElement(valueField);
        WebElement input = getDriver().findElement(valueField.child(Locator.tag("input")));

        input.sendKeys("0.02");

        waitAndClick(Locator.tagWithText("em", "Operator"));
        waitAndClick(Locator.tagContainingText("li", "<"));

        clickDialogButton("Apply");
        sleep(1000);

        // NOTE: depending on the size of the view area, this can vary. This is more a factor of the environment that actual behavior
        Assert.assertEquals("Incorrect number of variants", 87.0, getTotalVariantFeatures(), 1.0);

        // bottom filter UI
        waitForElement(Locator.tagContainingText("button", "mGAP: Showing sites where").containing("AF < 0.02"));
    }

    private long getTotalVariantFeatures()
    {
        return JBrowseTestHelper.getTotalVariantFeatures(this);
    }

    private void testLoadingConfigFilters(){
        beginAt("/" + getProjectName() + "/jbrowse-jbrowse.view?session=mgapF&location=1:1..10224");
        waitForJBrowseToLoad();

        // Wait for variants to load:
        getDriver().findElements(getVariantWithinTrack("mgap_hg38", "SNV T -> G"));

        Assert.assertEquals("Incorrect number of variants", 7, getTotalVariantFeatures());

        // bottom filter UI
        waitForElement(Locator.tagContainingText("button", "mGAP: Showing sites with a variant in any of:").containing("m00004,m00005"));

        openTrackMenuItem("Filter By Attributes");
        waitForElement(Locator.tagWithText("h6", "Filter Variants"));
        waitForElement(Locator.tagWithText("td", "Allele Frequency").withClass("MuiTableCell-sizeSmall"));
        waitForElement(Locator.tagWithClass("input", "MuiInputBase-input").withAttribute("value", "0.1"));
        Locator.findElements(getDriver(), Locator.tagWithClass("input", "MuiInputBase-input").withAttribute("value", "0.1")).get(0).sendKeys(Keys.ESCAPE);
        sleep(1000);

        openTrackMenuItem("Filter By Sample");
        waitForElement(Locator.tagWithText("h6", "Filter By Sample"));
        Locator textArea = Locator.tagWithClass("textarea", "MuiInputBase-inputMultiline");
        waitForElement(textArea);
        Assert.assertEquals("Incorrect samples", "m00004\nm00005", Locator.findElements(getDriver(), textArea).get(0).getText());
    }

    private void testSampleFilters()
    {
        beginAt("/" + getProjectName() + "/jbrowse-jbrowse.view?session=mgap&location=1:2000-5800");
        waitForJBrowseToLoad();

        // Wait for variants to load:
        getDriver().findElements(getVariantWithinTrack("mgap_hg38", "SNV A -> T"));

        // NOTE: depending on the size of the view area, this can vary. This is more a factor of the environment that actual behavior
        Assert.assertEquals("Incorrect number of variants", 37.0, getTotalVariantFeatures(), 1);

        openTrackMenuItem("Filter By Sample");
        waitForElement(Locator.tagWithText("h6", "Filter By Sample"));
        Locator textArea = Locator.tagWithClass("textarea", "MuiInputBase-inputMultiline");
        waitForElement(textArea);
        Locator.findElements(getDriver(), textArea).get(0).sendKeys("m00010");
        clickDialogButton("Apply");

        Assert.assertEquals("Incorrect number of variants", 3, getTotalVariantFeatures());
    }

    private void testInferredDetails()
    {
        beginAt("/" + getProjectName() + "/jbrowse-jbrowse.view?session=mgapF");
        waitForJBrowseToLoad();

        Actions actions = new Actions(getDriver());
        WebElement toClick = getDriver().findElements(getVariantWithinTrack("mgap_hg38", "SNV T -> C")).stream().filter(WebElement::isDisplayed).findFirst().get();

        actions.click(toClick).perform();
        waitForElement(Locator.tagWithText("div", "1:914"));
        waitForElement(Locator.tagWithText("span", "Predicted Function"));
    }

    private void testViewTableButton()
    {
        beginAt("/" + getProjectName() + "/jbrowse-jbrowse.view?session=mgap&location=1:100..1151");
        waitForJBrowseToLoad();

        waitAndClick(Locator.tagWithAttribute("button", "data-testid", "track_menu_icon"));
        assertElementPresent(Locator.tagContainingText("span", "View As Table"));
        assertElementNotPresent(Locator.tagContainingText("span", "Variant Search"));

        // NOTE: this button wont work since it's not actually indexed, but the server's config pokes in the property to show this:
        beginAt("/" + getProjectName() + "/jbrowse-jbrowse.view?session=mgapF&location=1:100..1151");
        waitForJBrowseToLoad();

        waitAndClick(Locator.tagWithAttribute("button", "data-testid", "track_menu_icon"));
        assertElementPresent(Locator.tagContainingText("span", "View As Table"));
        assertElementPresent(Locator.tagContainingText("span", "Variant Search"));
    }

    private void testSampleFiltersFromUrl()
    {
        // Note: this can be taxing on the browser, so load a more targeted region
        beginAt("/" + getProjectName() + "/jbrowse-jbrowse.view?session=mgap&sampleFilters=mgap:m00010&location=1:100..1151");
        waitForJBrowseToLoad();

        // Wait for variants to load:
        getDriver().findElements(getVariantWithinTrack("mgap_hg38", "SNV A -> G"));

        Assert.assertEquals("Incorrect number of variants", 3, getTotalVariantFeatures());

        openTrackMenuItem("Filter By Sample");
        waitForElement(Locator.tagWithText("h6", "Filter By Sample"));
        Locator textArea = Locator.tagWithClass("textarea", "MuiInputBase-inputMultiline");
        waitForElement(textArea);
        Assert.assertEquals("Incorrect samples", "m00010", Locator.findElements(getDriver(), textArea).get(0).getText());
    }

    private void testNoSession()
    {
        beginAt("/" + getProjectName() + "/jbrowse-jbrowse.view?");
        waitForElement(Locator.tagWithText("p", "Error - no session provided."));
    }

    public Locator.XPathLocator getTrackLocator(String trackId, boolean waitFor)
    {
        return JBrowseTestHelper.getTrackLocator(this, trackId, waitFor);
    }

    public By getVariantWithinTrack(String trackId, String variantText)
    {
        return JBrowseTestHelper.getVariantWithinTrack(this, trackId, variantText);
    }

    private void testMessageDisplay()
    {
        beginAt("/" + getProjectName() + "/jbrowse-jbrowse.view?session=mgap");
        waitForJBrowseToLoad();

        Actions actions = new Actions(getDriver());
        WebElement toClick = getDriver().findElements(JBrowseTestHelper.getVariantWithinTrack(this, "mgap_hg38", "deletion TA -> T", false)).stream().filter(WebElement::isDisplayed).findFirst().orElseThrow();
        actions.click(toClick).perform();
        waitForElement(Locator.tagContainingText("div", "Aut molestiae temporibus nesciunt."));
    }

    public void waitForJBrowseToLoad()
    {
        JBrowseTestHelper.waitForJBrowseToLoad(this);
    }

    private void testSessionCardDisplay()
    {
        beginAt("/" + getProjectName() + "/jbrowse-jbrowse.view?session=mgap&location=1:8328..8842");
        waitForJBrowseToLoad();

        Actions actions = new Actions(getDriver());
        WebElement toClick = getDriver().findElements(getVariantWithinTrack("mgap_hg38", "SNV A -> G")).stream().filter(WebElement::isDisplayed).collect(JBrowseTestHelper.toSingleton());
        actions.click(toClick).perform();
        waitForElement(Locator.tagWithText("span", "Section 1"));

        waitForElement(Locator.tagWithText("td", "Allele Count"));
    }

    private void testTitleMapping()
    {
        beginAt("/" + getProjectName() + "/jbrowse-jbrowse.view?session=mgap&location=1:104..275");
        waitForJBrowseToLoad();

        Actions actions = new Actions(getDriver());
        WebElement toClick = getDriver().findElements(getVariantWithinTrack("mgap_hg38", "SNV T -> C")).stream().filter(WebElement::isDisplayed).collect(JBrowseTestHelper.toSingleton()); // 1:137..137
        actions.click(toClick).perform();
        waitForElement(Locator.tagWithText("div", "1:137"));
        assertElementPresent(Locator.tagWithText("td", "Minor Allele Frequency"));
    }

    private void testPredictedFunction()
    {
        beginAt("/" + getProjectName() + "/jbrowse-jbrowse.view?session=mgap&location=1:104..275");
        waitForJBrowseToLoad();

        Actions actions = new Actions(getDriver());
        WebElement toClick = getDriver().findElements(getVariantWithinTrack("mgap_hg38", "SNV T -> C")).stream().filter(WebElement::isDisplayed).collect(JBrowseTestHelper.toSingleton()); // 1:137..137
        actions.click(toClick).perform();
        waitForElement(Locator.tagWithText("div", "1:137"));
        assertElementPresent(Locator.tagWithText("th", "Effect"));
        assertElementPresent(Locator.tagWithText("th", "Impact"));
        assertElementPresent(Locator.tagWithText("th", "Gene Name"));
        assertElementPresent(Locator.tagWithText("th", "Position/Consequence"));
        assertElementPresent(Locator.tagWithText("td", "intron_variant"));
        assertElementPresent(Locator.tagWithText("td", "custom"));
    }

    private void testAlleleFrequencies()
    {
        beginAt("/" + getProjectName() + "/jbrowse-jbrowse.view?session=mgap&location=1:18465..18507");
        waitForJBrowseToLoad();

        Actions actions = new Actions(getDriver());
        WebElement toClick = getDriver().findElements(getVariantWithinTrack("mgap_hg38", "SNV C -> A")).stream().filter(WebElement::isDisplayed).collect(JBrowseTestHelper.toSingleton()); // 1:18,486
        actions.click(toClick).perform();
        waitForElement(Locator.tagWithText("div", "1:18,486"));
        assertElementPresent(Locator.tagWithText("th", "Sequence"));
        assertElementPresent(Locator.tagWithText("th", "Fraction"));
        assertElementPresent(Locator.tagWithText("th", "Count"));
        assertElementPresent(Locator.tagWithText("td", "3041"));
        assertElementPresent(Locator.tagWithText("td", "3"));
    }

    private void testGenotypeFrequencies()
    {
        beginAt("/" + getProjectName() + "/jbrowse-jbrowse.view?session=mgap&location=1:18465..18507");
        waitForJBrowseToLoad();

        Actions actions = new Actions(getDriver());
        WebElement toClick = getDriver().findElements(getVariantWithinTrack("mgap_hg38", "SNV C -> A")).stream().filter(WebElement::isDisplayed).collect(JBrowseTestHelper.toSingleton()); // 1:18,486
        actions.click(toClick).perform();
        waitForElement(Locator.tagWithText("div", "1:18,486"));
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
        Integer genomeId = SequenceTest.createMac239ReferenceGenome(this, 1);
        createGenomeFeatures(genomeId);

        SequenceTest.addReferenceGenomeTracks(this, getProjectName(), SequenceTest.TEST_GENOME_NAME, genomeId, 1);
    }

    private void createGenomeFeatures(int genomeId) throws IOException, CommandException
    {
        Connection cn = new Connection(WebTestHelper.getBaseURL(), PasswordUtil.getUsername(), PasswordUtil.getPassword());
        SelectRowsCommand sr = new SelectRowsCommand("sequenceanalysis", "reference_library_members");
        sr.addFilter(new Filter("library_id", genomeId, Filter.Operator.EQUAL));
        sr.setColumns(List.of("ref_nt_id"));
        Integer refNtId = (int)sr.execute(cn, getProjectName()).getRows().get(0).get("ref_nt_id");

        InsertRowsCommand ic = new InsertRowsCommand("sequenceanalysis", "ref_aa_sequences");
        ic.addRow(Map.of("ref_nt_id", refNtId, "name", "AA1", "exons", "1-30;60-68", "isComplement", false, "start_location", 1, "sequence", "AAAAAAAAAAAAA"));
        ic.addRow(Map.of("ref_nt_id", refNtId, "name", "AA2", "exons", "101-130;160-168", "isComplement", true, "start_location", 100, "sequence", "AAAAAAAAAAAAA"));

        ic.execute(cn, getProjectName());

        ic = new InsertRowsCommand("sequenceanalysis", "ref_nt_features");
        ic.addRow(Map.of("ref_nt_id", refNtId, "category", "Feature", "nt_start", 10, "nt_stop", 100, "name", "Feature1"));
        ic.addRow(Map.of("ref_nt_id", refNtId, "category", "Feature", "nt_start", 200, "nt_stop", 300, "name", "Feature2"));
        ic.execute(cn, getProjectName());
    }

    @Override
    public List<String> getAssociatedModules()
    {
        return List.of("jbrowse");
    }

    private void testFullTextSearch() throws Exception
    {
        if (!SequenceTest.isExternalPipelineEnabled(getProjectName()))
        {
            log("JBrowseTest.testFullTextSearch() requires external tools, including DISCVRSeq.jar, skipping");
            return;
        }

        String seq = SequenceTest.readSeqFromFile(JBrowseTestHelper.GRCH37_GENOME);
        SequenceTest.ensureRefSeqExists(this, "1", seq);
        SequenceTest.createReferenceGenome(this, 1, JB_GENOME_NAME, "1");

        SequenceTest.addOutputFile(this, JBrowseTestHelper.MGAP_TEST_VCF, JB_GENOME_NAME, "TestVCF", "VCF File", "This is an output file to test VCF full-text search", false);

        JBrowseTestHelper.prepareSearchSession(this, getProjectName());
        Pair<String, String> info = JBrowseTestHelper.configureSearchSession(this, getProjectName());
        String sessionId = info.getKey();
        String trackId = info.getValue();

        // all
        // this should return 143 results. We can't make any other assumptions about the content
        String url = "/jbrowse/" + getProjectName() + "/luceneQuery.view?sessionId=" + sessionId + "&trackId=" + trackId + "&searchString=all&pageSize=143";
        beginAt(url);
        waitForText("data");
        waitAndClick(Locator.tagWithId("a", "rawdata-tab"));
        String jsonString = getText(Locator.tagWithClass("pre", "data"));
        JSONObject mainJsonObject = new JSONObject(jsonString);
        JSONArray jsonArray = mainJsonObject.getJSONArray("data");
        Assert.assertEquals(143, jsonArray.length());


        // stringType:
        // ref equals A
        url = "/jbrowse/" + getProjectName() + "/luceneQuery.view?sessionId=" + sessionId + "&trackId=" + trackId + "&searchString=ref%3AA";
        beginAt(url);
        waitForText("data");
        waitAndClick(Locator.tagWithId("a", "rawdata-tab"));
        jsonString = getText(Locator.tagWithClass("pre", "data"));
        mainJsonObject = new JSONObject(jsonString);
        jsonArray = mainJsonObject.getJSONArray("data");
        Assert.assertEquals(100, jsonArray.length());
        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject jsonObject = jsonArray.getJSONObject(i);
            Assert.assertEquals("A", jsonObject.getString("ref"));
        }

        // alt does not equal C
        url = "/jbrowse/" + getProjectName() + "/luceneQuery.view?sessionId=" + sessionId + "&trackId=" + trackId + "&searchString=*%3A*%20-alt%3AC";
        beginAt(url);
        waitForText("data");
        waitAndClick(Locator.tagWithId("a", "rawdata-tab"));
        jsonString = getText(Locator.tagWithClass("pre", "data"));
        mainJsonObject = new JSONObject(jsonString);
        jsonArray = mainJsonObject.getJSONArray("data");
        Assert.assertEquals(100, jsonArray.length());
        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject jsonObject = jsonArray.getJSONObject(i);
            Assert.assertNotEquals("C", jsonObject.getString("alt"));
        }

        // ref contains A
        url = "/jbrowse/" + getProjectName() + "/luceneQuery.view?sessionId=" + sessionId + "&trackId=" + trackId + "&searchString=ref%3A*A*";
        beginAt(url);
        waitForText("data");
        waitAndClick(Locator.tagWithId("a", "rawdata-tab"));
        jsonString = getText(Locator.tagWithClass("pre", "data"));
        mainJsonObject = new JSONObject(jsonString);
        jsonArray = mainJsonObject.getJSONArray("data");
        Assert.assertEquals(100, jsonArray.length());
        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject jsonObject = jsonArray.getJSONObject(i);
            Assert.assertTrue(jsonObject.getString("ref").contains("A"));
        }

        // alt does not contain AA
        url = "/jbrowse/" + getProjectName() + "/luceneQuery.view?sessionId=" + sessionId + "&trackId=" + trackId + "&searchString=*%3A*%20-alt%3A*AA*";
        beginAt(url);
        waitForText("data");
        waitAndClick(Locator.tagWithId("a", "rawdata-tab"));
        jsonString = getText(Locator.tagWithClass("pre", "data"));
        mainJsonObject = new JSONObject(jsonString);
        jsonArray = mainJsonObject.getJSONArray("data");
        Assert.assertEquals(100, jsonArray.length());
        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject jsonObject = jsonArray.getJSONObject(i);
            Assert.assertTrue(!jsonObject.getString("alt").contains("AA"));
        }

        // IMPACT starts with HI
        url = "/jbrowse/" + getProjectName() + "/luceneQuery.view?sessionId=" + sessionId + "&trackId=" + trackId + "&searchString=IMPACT%3AHI*";
        beginAt(url);
        waitForText("data");
        waitAndClick(Locator.tagWithId("a", "rawdata-tab"));
        jsonString = getText(Locator.tagWithClass("pre", "data"));
        mainJsonObject = new JSONObject(jsonString);
        jsonArray = mainJsonObject.getJSONArray("data");
        Assert.assertEquals(1, jsonArray.length());
        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject jsonObject = jsonArray.getJSONObject(i);
            Assert.assertEquals("HI", jsonObject.getString("IMPACT").substring(0, 2));
        }

        // ref ends with TA
        url = "/jbrowse/" + getProjectName() + "/luceneQuery.view?sessionId=" + sessionId + "&trackId=" + trackId + "&searchString=ref%3A*TA";
        beginAt(url);
        waitForText("data");
        waitAndClick(Locator.tagWithId("a", "rawdata-tab"));
        jsonString = getText(Locator.tagWithClass("pre", "data"));
        mainJsonObject = new JSONObject(jsonString);
        jsonArray = mainJsonObject.getJSONArray("data");
        Assert.assertEquals(5, jsonArray.length());
        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject jsonObject = jsonArray.getJSONObject(i);
            Assert.assertEquals("TA", jsonObject.getString("ref").substring(jsonObject.getString("ref").length() - 2));
        }

        // IMPACT is empty
        url = "/jbrowse/" + getProjectName() + "/luceneQuery.view?sessionId=" + sessionId + "&trackId=" + trackId + "&searchString=*%3A*%20-IMPACT%3A*";
        beginAt(url);
        waitForText("data");
        waitAndClick(Locator.tagWithId("a", "rawdata-tab"));
        jsonString = getText(Locator.tagWithClass("pre", "data"));
        mainJsonObject = new JSONObject(jsonString);
        jsonArray = mainJsonObject.getJSONArray("data");
        Assert.assertEquals(100, jsonArray.length());
        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject jsonObject = jsonArray.getJSONObject(i);
            Assert.assertFalse(jsonObject.has("IMPACT"));
        }

        // IMPACT is not empty
        url = "/jbrowse/" + getProjectName() + "/luceneQuery.view?sessionId=" + sessionId + "&trackId=" + trackId + "&searchString=IMPACT%3A*";
        beginAt(url);
        waitForText("data");
        waitAndClick(Locator.tagWithId("a", "rawdata-tab"));
        jsonString = getText(Locator.tagWithClass("pre", "data"));
        mainJsonObject = new JSONObject(jsonString);
        jsonArray = mainJsonObject.getJSONArray("data");
        Assert.assertEquals(3, jsonArray.length());
        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject jsonObject = jsonArray.getJSONObject(i);
            Assert.assertTrue(jsonObject.has("IMPACT"));
        }

        // variableSamplesType in set TestGroup
        url = "/jbrowse/" + getProjectName() + "/luceneQuery.view?sessionId=" + sessionId + "&trackId=" + trackId + "&searchString=variableSamples%3A~!TestGroup!~";
        beginAt(url);
        waitForText("data");
        waitAndClick(Locator.tagWithId("a", "rawdata-tab"));
        jsonString = getText(Locator.tagWithClass("pre", "data"));
        mainJsonObject = new JSONObject(jsonString);
        jsonArray = mainJsonObject.getJSONArray("data");
        Assert.assertEquals(100, jsonArray.length());

        // variable in m00004
        url = "/jbrowse/" + getProjectName() + "/luceneQuery.view?sessionId=" + sessionId + "&trackId=" + trackId + "&searchString=variableSamples%3Am00004";
        beginAt(url);
        waitForText("data");
        waitAndClick(Locator.tagWithId("a", "rawdata-tab"));
        jsonString = getText(Locator.tagWithClass("pre", "data"));
        mainJsonObject = new JSONObject(jsonString);
        jsonArray = mainJsonObject.getJSONArray("data");
        Assert.assertEquals(79, jsonArray.length());
        for (int i = 0; i < jsonArray.length(); i++)
        {
            JSONObject jsonObject = jsonArray.getJSONObject(i);
            boolean found = false;

            for (int j = 0; j < jsonObject.getJSONArray("variableSamples").length(); j++)
            {
                if ("m00004".equals(jsonObject.getJSONArray("variableSamples").getString(j)))
                {
                    found = true;
                }
            }

            Assert.assertTrue(found);
        }

        // not variable in m00004
        url = "/jbrowse/" + getProjectName() + "/luceneQuery.view?sessionId=" + sessionId + "&trackId=" + trackId + "&searchString=*%3A*%20-variableSamples%3Am00004";
        beginAt(url);
        waitForText("data");
        waitAndClick(Locator.tagWithId("a", "rawdata-tab"));
        jsonString = getText(Locator.tagWithClass("pre", "data"));
        mainJsonObject = new JSONObject(jsonString);
        jsonArray = mainJsonObject.getJSONArray("data");
        Assert.assertEquals(100, jsonArray.length());
        for (int i = 0; i < jsonArray.length(); i++)
        {
            JSONObject jsonObject = jsonArray.getJSONObject(i);
            boolean found = false;

            if(!jsonObject.has("variableSamples")) {
                continue;
            }

            try {
                for (int j = 0; j < jsonObject.getJSONArray("variableSamples").length(); j++)
                {
                    if ("m00004".equals(jsonObject.getJSONArray("variableSamples").getString(j)))
                    {
                        found = true;
                    }
                }
            } catch (JSONException e) {
                if ("m00004".equals(jsonObject.getString("variableSamples")))
                {
                    found = true;
                }
            }

            Assert.assertFalse(found);
        }

        // variable in all of m00004, m00013, m00029
        url = "/jbrowse/" + getProjectName() + "/luceneQuery.view?sessionId=" + sessionId + "&trackId=" + trackId + "&searchString=%252BvariableSamples%3Am00004%20%252BvariableSamples%3Am00013%20%252BvariableSamples%3Am00029";
        beginAt(url);
        waitForText("data");
        waitAndClick(Locator.tagWithId("a", "rawdata-tab"));
        jsonString = getText(Locator.tagWithClass("pre", "data"));
        mainJsonObject = new JSONObject(jsonString);
        jsonArray = mainJsonObject.getJSONArray("data");
        Assert.assertEquals(69, jsonArray.length());
        for (int i = 0; i < jsonArray.length(); i++)
        {
            JSONObject jsonObject = jsonArray.getJSONObject(i);
            boolean found1 = false, found2 = false, found3 = false;

            for (int j = 0; j < jsonObject.getJSONArray("variableSamples").length(); j++)
            {
                if ("m00004".equals(jsonObject.getJSONArray("variableSamples").getString(j))) {
                    found1 = true;
                }

                if ("m00013".equals(jsonObject.getJSONArray("variableSamples").getString(j))) {
                    found2 = true;
                }

                if ("m00029".equals(jsonObject.getJSONArray("variableSamples").getString(j))) {
                    found3 = true;
                }
            }

            Assert.assertTrue(found1);
            Assert.assertTrue(found2);
            Assert.assertTrue(found3);
        }

        // variable in any of m00004, m00013, m00029
        url = "/jbrowse/" + getProjectName() + "/luceneQuery.view?sessionId=" + sessionId + "&trackId=" + trackId + "&searchString=variableSamples%3Am00004%20OR%20variableSamples%3Am00013%20OR%20variableSamples%3Am00029";
        beginAt(url);
        waitForText("data");
        waitAndClick(Locator.tagWithId("a", "rawdata-tab"));
        jsonString = getText(Locator.tagWithClass("pre", "data"));
        mainJsonObject = new JSONObject(jsonString);
        jsonArray = mainJsonObject.getJSONArray("data");
        Assert.assertEquals(100, jsonArray.length());
        for (int i = 0; i < jsonArray.length(); i++)
        {
            JSONObject jsonObject = jsonArray.getJSONObject(i);
            boolean found1 = false, found2 = false, found3 = false;

            for (int j = 0; j < jsonObject.getJSONArray("variableSamples").length(); j++)
            {
                if ("m00004".equals(jsonObject.getJSONArray("variableSamples").getString(j))) {
                    found1 = true;
                }

                if ("m00013".equals(jsonObject.getJSONArray("variableSamples").getString(j))) {
                    found2 = true;
                }

                if ("m00029".equals(jsonObject.getJSONArray("variableSamples").getString(j))) {
                    found3 = true;
                }
            }

            Assert.assertTrue(found1 || found2 || found3);
        }

        // not variable in any of m00004, m00013, m00029
        url = "/jbrowse/" + getProjectName() + "/luceneQuery.view?sessionId=" + sessionId + "&trackId=" + trackId + "&searchString=*%3A*%20-variableSamples%3Am00004%20AND%20*%3A*%20-variableSamples%3Am00013%20AND%20*%3A*%20-variableSamples%3Am00029";
        beginAt(url);
        waitForText("data");
        waitAndClick(Locator.tagWithId("a", "rawdata-tab"));
        jsonString = getText(Locator.tagWithClass("pre", "data"));
        mainJsonObject = new JSONObject(jsonString);
        jsonArray = mainJsonObject.getJSONArray("data");
        Assert.assertEquals(100, jsonArray.length());
        for (int i = 0; i < jsonArray.length(); i++)
        {
            JSONObject jsonObject = jsonArray.getJSONObject(i);
            boolean found1 = false, found2 = false, found3 = false;

            if(!jsonObject.has("variableSamples")) {
                continue;
            }

            try {
                for (int j = 0; j < jsonObject.getJSONArray("variableSamples").length(); j++)
                {
                    if ("m00004".equals(jsonObject.getJSONArray("variableSamples").getString(j))) {
                        found1 = true;
                    }

                    if ("m00013".equals(jsonObject.getJSONArray("variableSamples").getString(j))) {
                        found2 = true;
                    }

                    if ("m00029".equals(jsonObject.getJSONArray("variableSamples").getString(j))) {
                        found3 = true;
                    }
                }
            } catch (JSONException e) {
                if ("m00004".equals(jsonObject.getString("variableSamples"))) {
                    found1 = true;
                }

                if ("m00013".equals(jsonObject.getString("variableSamples"))) {
                    found2 = true;
                }

                if ("m00029".equals(jsonObject.getString("variableSamples"))) {
                    found3 = true;
                }
            }

            Assert.assertFalse(found1);
            Assert.assertFalse(found2);
            Assert.assertFalse(found3);
        }

        // not variable in one of m00004, m00013, m00029
        url = "/jbrowse/" + getProjectName() + "/luceneQuery.view?sessionId=" + sessionId + "&trackId=" + trackId + "&searchString=*%3A*%20-variableSamples%3Am00004%20OR%20*%3A*%20-variableSamples%3Am00013%20OR%20*%3A*%20-variableSamples%3Am00029";
        beginAt(url);
        waitForText("data");
        waitAndClick(Locator.tagWithId("a", "rawdata-tab"));
        jsonString = getText(Locator.tagWithClass("pre", "data"));
        mainJsonObject = new JSONObject(jsonString);
        jsonArray = mainJsonObject.getJSONArray("data");
        Assert.assertEquals(100, jsonArray.length());
        for (int i = 0; i < jsonArray.length(); i++)
        {
            JSONObject jsonObject = jsonArray.getJSONObject(i);
            boolean found1 = false, found2 = false, found3 = false;

            if(!jsonObject.has("variableSamples")) {
                continue;
            }

            try {
                for (int j = 0; j < jsonObject.getJSONArray("variableSamples").length(); j++)
                {
                    if ("m00004".equals(jsonObject.getJSONArray("variableSamples").getString(j))) {
                        found1 = true;
                    }

                    if ("m00013".equals(jsonObject.getJSONArray("variableSamples").getString(j))) {
                        found2 = true;
                    }

                    if ("m00029".equals(jsonObject.getJSONArray("variableSamples").getString(j))) {
                        found3 = true;
                    }
                }
            } catch (JSONException e) {
                if ("m00004".equals(jsonObject.getString("variableSamples"))) {
                    found1 = true;
                }

                if ("m00013".equals(jsonObject.getString("variableSamples"))) {
                    found2 = true;
                }

                if ("m00029".equals(jsonObject.getString("variableSamples"))) {
                    found3 = true;
                }
            }

            Assert.assertFalse(found1 || found2 || found3);
        }

        // is empty
        url = "/jbrowse/" + getProjectName() + "/luceneQuery.view?sessionId=" + sessionId + "&trackId=" + trackId + "&searchString=*%3A*%20-variableSamples%3A*";
        beginAt(url);
        waitForText("data");
        waitAndClick(Locator.tagWithId("a", "rawdata-tab"));
        jsonString = getText(Locator.tagWithClass("pre", "data"));
        mainJsonObject = new JSONObject(jsonString);
        jsonArray = mainJsonObject.getJSONArray("data");
        Assert.assertEquals(5, jsonArray.length());
        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject jsonObject = jsonArray.getJSONObject(i);
            Assert.assertFalse(jsonObject.has("variableSamples"));
        }

        // is not empty
        url = "/jbrowse/" + getProjectName() + "/luceneQuery.view?sessionId=" + sessionId + "&trackId=" + trackId + "&searchString=variableSamples%3A*";
        beginAt(url);
        waitForText("data");
        waitAndClick(Locator.tagWithId("a", "rawdata-tab"));
        jsonString = getText(Locator.tagWithClass("pre", "data"));
        mainJsonObject = new JSONObject(jsonString);
        jsonArray = mainJsonObject.getJSONArray("data");
        Assert.assertEquals(100, jsonArray.length());
        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject jsonObject = jsonArray.getJSONObject(i);
            Assert.assertTrue(jsonObject.has("variableSamples"));
        }


        // numericType, int and float:
        // AC = 12
        url = "/jbrowse/" + getProjectName() + "/luceneQuery.view?sessionId=" + sessionId + "&trackId=" + trackId + "&searchString=AC%3A%5B12%20TO%2012%5D";
        beginAt(url);
        waitForText("data");
        waitAndClick(Locator.tagWithId("a", "rawdata-tab"));
        jsonString = getText(Locator.tagWithClass("pre", "data"));
        mainJsonObject = new JSONObject(jsonString);
        jsonArray = mainJsonObject.getJSONArray("data");
        Assert.assertEquals(3, jsonArray.length());
        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject jsonObject = jsonArray.getJSONObject(i);
            Assert.assertEquals(12, jsonObject.getInt("AC"));
        }

        // AC != 88
        url = "/jbrowse/" + getProjectName() + "/luceneQuery.view?sessionId=" + sessionId + "&trackId=" + trackId + "&searchString=AC%3A%5B*%20TO%2088%7D%20OR%20AC%3A%7B88%20TO%20*%5D";
        beginAt(url);
        waitForText("data");
        waitAndClick(Locator.tagWithId("a", "rawdata-tab"));
        jsonString = getText(Locator.tagWithClass("pre", "data"));
        mainJsonObject = new JSONObject(jsonString);
        jsonArray = mainJsonObject.getJSONArray("data");
        Assert.assertEquals(100, jsonArray.length());
        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject jsonObject = jsonArray.getJSONObject(i);
            Assert.assertNotEquals(88, jsonObject.getInt("AC"));
        }

        // AC > 88
        url = "/jbrowse/" + getProjectName() + "/luceneQuery.view?sessionId=" + sessionId + "&trackId=" + trackId + "&searchString=AC%3A%7B88%20TO%20*%5D";
        beginAt(url);
        waitForText("data");
        waitAndClick(Locator.tagWithId("a", "rawdata-tab"));
        jsonString = getText(Locator.tagWithClass("pre", "data"));
        mainJsonObject = new JSONObject(jsonString);
        jsonArray = mainJsonObject.getJSONArray("data");
        Assert.assertEquals(100, jsonArray.length());
        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject jsonObject = jsonArray.getJSONObject(i);
            Assert.assertTrue(jsonObject.getInt("AC") > 88);
        }

        // AC >= 88
        url = "/jbrowse/" + getProjectName() + "/luceneQuery.view?sessionId=" + sessionId + "&trackId=" + trackId + "&searchString=AC%3A%5B88%20TO%20*%5D";
        beginAt(url);
        waitForText("data");
        waitAndClick(Locator.tagWithId("a", "rawdata-tab"));
        jsonString = getText(Locator.tagWithClass("pre", "data"));
        mainJsonObject = new JSONObject(jsonString);
        jsonArray = mainJsonObject.getJSONArray("data");
        Assert.assertEquals(100, jsonArray.length());
        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject jsonObject = jsonArray.getJSONObject(i);
            Assert.assertTrue(jsonObject.getInt("AC") >= 88);
        }

        // start < 137
        url = "/jbrowse/" + getProjectName() + "/luceneQuery.view?sessionId=" + sessionId + "&trackId=" + trackId + "&searchString=start%3A%5B*%20TO%20137%7D";
        beginAt(url);
        waitForText("data");
        waitAndClick(Locator.tagWithId("a", "rawdata-tab"));
        jsonString = getText(Locator.tagWithClass("pre", "data"));
        mainJsonObject = new JSONObject(jsonString);
        jsonArray = mainJsonObject.getJSONArray("data");
        Assert.assertEquals(2, jsonArray.length());
        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject jsonObject = jsonArray.getJSONObject(i);
            Assert.assertTrue( jsonObject.getInt("start") < 137);
        }

        // end <= 440
        url = "/jbrowse/" + getProjectName() + "/luceneQuery.view?sessionId=" + sessionId + "&trackId=" + trackId + "&searchString=end%3A%5B*%20TO%20440%5D";
        beginAt(url);
        waitForText("data");
        waitAndClick(Locator.tagWithId("a", "rawdata-tab"));
        jsonString = getText(Locator.tagWithClass("pre", "data"));
        mainJsonObject = new JSONObject(jsonString);
        jsonArray = mainJsonObject.getJSONArray("data");
        Assert.assertEquals(7, jsonArray.length());
        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject jsonObject = jsonArray.getJSONObject(i);
            Assert.assertTrue(jsonObject.getInt("end") <= 440);
        }

        // AF = 0.532
        url = "/jbrowse/" + getProjectName() + "/luceneQuery.view?sessionId=" + sessionId + "&trackId=" + trackId + "&searchString=AF%3A%5B0.531999%20TO%200.5320010000000001%5D";
        beginAt(url);
        waitForText("data");
        waitAndClick(Locator.tagWithId("a", "rawdata-tab"));
        jsonString = getText(Locator.tagWithClass("pre", "data"));
        mainJsonObject = new JSONObject(jsonString);
        jsonArray = mainJsonObject.getJSONArray("data");
        Assert.assertEquals(1, jsonArray.length());
        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject jsonObject = jsonArray.getJSONObject(i);
            Assert.assertEquals(0.532, jsonObject.getDouble("AF"), 0.000001);
        }

        // AF != 0.029
        url = "/jbrowse/" + getProjectName() + "/luceneQuery.view?sessionId=" + sessionId + "&trackId=" + trackId + "&searchString=AF%3A%5B*%20TO%200.028999%5D%20OR%20AF%3A%5B0.029001000000000002%20TO%20*%5D";
        beginAt(url);
        waitForText("data");
        waitAndClick(Locator.tagWithId("a", "rawdata-tab"));
        jsonString = getText(Locator.tagWithClass("pre", "data"));
        mainJsonObject = new JSONObject(jsonString);
        jsonArray = mainJsonObject.getJSONArray("data");
        Assert.assertEquals(100, jsonArray.length());
        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject jsonObject = jsonArray.getJSONObject(i);
            Assert.assertNotEquals(0.029, jsonObject.getDouble("AF"));
        }

        // AF > 0.532
        url = "/jbrowse/" + getProjectName() + "/luceneQuery.view?sessionId=" + sessionId + "&trackId=" + trackId + "&searchString=AF%3A%5B0.5320010000000001%20TO%20*%5D";
        beginAt(url);
        waitForText("data");
        waitAndClick(Locator.tagWithId("a", "rawdata-tab"));
        jsonString = getText(Locator.tagWithClass("pre", "data"));
        mainJsonObject = new JSONObject(jsonString);
        jsonArray = mainJsonObject.getJSONArray("data");
        Assert.assertEquals(18, jsonArray.length());
        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject jsonObject = jsonArray.getJSONObject(i);
            Assert.assertTrue(jsonObject.getDouble("AF") > 0.532);
        }

        // AF >= 0.029
        url = "/jbrowse/" + getProjectName() + "/luceneQuery.view?sessionId=" + sessionId + "&trackId=" + trackId + "&searchString=AF%3A%5B0.029%20TO%20*%5D";
        beginAt(url);
        waitForText("data");
        waitAndClick(Locator.tagWithId("a", "rawdata-tab"));
        jsonString = getText(Locator.tagWithClass("pre", "data"));
        mainJsonObject = new JSONObject(jsonString);
        jsonArray = mainJsonObject.getJSONArray("data");
        Assert.assertEquals(100, jsonArray.length());
        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject jsonObject = jsonArray.getJSONObject(i);
            Assert.assertTrue(jsonObject.getDouble("AF") >= 0.029);
        }

        // AF < 0.029
        url = "/jbrowse/" + getProjectName() + "/luceneQuery.view?sessionId=" + sessionId + "&trackId=" + trackId + "&searchString=AF%3A%5B*%20TO%200.028999%5D";
        beginAt(url);
        waitForText("data");
        waitAndClick(Locator.tagWithId("a", "rawdata-tab"));
        jsonString = getText(Locator.tagWithClass("pre", "data"));
        mainJsonObject = new JSONObject(jsonString);
        jsonArray = mainJsonObject.getJSONArray("data");
        Assert.assertEquals(100, jsonArray.length());
        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject jsonObject = jsonArray.getJSONObject(i);
            Assert.assertTrue(jsonObject.getDouble("AF") < 0.029);
        }

        // AF <= 0.029
        url = "/jbrowse/" + getProjectName() + "/luceneQuery.view?sessionId=" + sessionId + "&trackId=" + trackId + "&searchString=AF%3A%5B*%20TO%200.029%5D";
        beginAt(url);
        waitForText("data");
        waitAndClick(Locator.tagWithId("a", "rawdata-tab"));
        jsonString = getText(Locator.tagWithClass("pre", "data"));
        mainJsonObject = new JSONObject(jsonString);
        jsonArray = mainJsonObject.getJSONArray("data");
        Assert.assertEquals(100, jsonArray.length());
        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject jsonObject = jsonArray.getJSONObject(i);
            Assert.assertTrue(jsonObject.getDouble("AF") <= 0.029);
        }


        // composite queries
        // contig := 1
        // ref := A
        // should be 100 results and each should be ref = A
        url = "/jbrowse/" + getProjectName() + "/luceneQuery.view?sessionId=" + sessionId + "&trackId=" + trackId + "&searchString=contig%3A%3D1%26ref%3A%3DA&pageSize=200";
        beginAt(url);
        waitForText("data");
        waitAndClick(Locator.tagWithId("a", "rawdata-tab"));
        jsonString = getText(Locator.tagWithClass("pre", "data"));
        mainJsonObject = new JSONObject(jsonString);
        jsonArray = mainJsonObject.getJSONArray("data");
        Assert.assertEquals(104, jsonArray.length());
        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject jsonObject = jsonArray.getJSONObject(i);
            Assert.assertEquals(1, jsonObject.getInt("contig"));
            Assert.assertEquals("A", jsonObject.getString("ref"));
        }

        testLuceneSearchUI(sessionId);
    }

    private void testOutputFileProcessing() throws Exception
    {
        goToProjectHome();

        //import BAM, VCF, BED, GFF
        File dataDir = TestFileUtils.getSampleData("sequenceAnalysis/genomeAnnotations");
        for (File f : dataDir.listFiles())
        {
            File target = SequenceTest.replaceContigName(f, SequenceTest.GENOME_SEQ_NAME);
            SequenceTest.addOutputFile(this, target, SequenceTest.TEST_GENOME_NAME, target.getName(), "Gene Annotations", "This is an output file", false);
        }

        File testBam = new File(SequenceTest._sampleData, "test.bam");
        SequenceTest.addOutputFile(this, testBam, SequenceTest.TEST_GENOME_NAME, "TestBAM", "BAM File", "This is an output file", false);

        //create session w/ some of these, verify
        log("creating initial jbrowse session");
        beginAt("/query/" + getProjectName() + "/executeQuery.view?query.queryName=outputfiles&schemaName=sequenceanalysis");
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

        Window<?> window = new Window.WindowFinder(getDriver()).withTitle("Create New Workbook or Add To Existing?").waitFor();
        window.clickButton("Submit", 0);

        window = new Window.WindowFinder(getDriver()).withTitle("Success").waitFor();
        window.clickButton("OK");
        waitForPipelineJobsToComplete(existingPipelineJobs + 1, "Create New Session", false);

        //add additional file to session, verify
        beginAt("/query/" + getProjectName() + "/executeQuery.view?query.queryName=outputfiles&schemaName=sequenceanalysis");

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

        beginAt("/query/" + getProjectName() + "/executeQuery.view?query.queryName=jsonfiles&schemaName=jbrowse");
        existingPipelineJobs = SequenceTest.getTotalPipelineJobs(this);
        dr = DataRegionTable.DataRegion(getDriver()).find();
        dr.checkAllOnPage();
        dr.clickHeaderMenu("More Actions", false, "Re-process Selected");
        new Window.WindowFinder(getDriver()).withTitle("Reprocess Resources").waitFor();
        waitAndClick(Ext4Helper.Locators.ext4ButtonEnabled("Submit"));
        new Window.WindowFinder(getDriver()).withTitle("Success").waitFor();
        waitAndClickAndWait(Ext4Helper.Locators.ext4ButtonEnabled("OK"));
        waitForPipelineJobsToComplete(existingPipelineJobs + 1, "Recreating Resources", false);

        beginAt("/project/" + getProjectName() + "/begin.view");
        _helper.clickNavPanelItemAndWait("JBrowse Sessions:", 1);
        waitAndClickAndWait(Locator.tagWithText("a", "View In JBrowse"));
        waitForElement(Locator.tagWithText("div", "TestGenome1"));

        beginAt("/project/" + getProjectName() + "/begin.view");
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
        beginAt("/project/" + getProjectName() + "/begin.view");
        _helper.clickNavPanelItemAndWait("JBrowse Sessions:", 1);
        waitAndClickAndWait(Locator.tagWithText("a", "View In JBrowse"));
        waitForElement(Locator.tagWithText("div", "TestGenome1"));
        waitAndClick(Locator.tagContainingText("button", "Show all regions in assembly").withClass("MuiButtonBase-root"));
        waitForElement(Locator.tagWithText("span", "fakeData.gff").withClass("MuiTypography-root"));
        waitForElement(Locator.tagWithText("span", "fakeData.bed").withClass("MuiTypography-root"));

        //Now test search
        testTrixSearch(sessionId);
    }

    private void testTrixSearch(String sessionId) throws Exception
    {
        goToProjectHome();

        PortalHelper portalHelper = new PortalHelper(this);
        portalHelper.addSideWebPart("JBrowse Search");
        _searchWebpartAdded = true;

        waitForElement(Locator.tagWithText("p", "No session Id provided. Please have your site admin use the customize icon to set the session ID for this webpart."));
        portalHelper.clickWebpartMenuItem("JBrowse Search", false, "Customize");
        Window<?> window = new Window.WindowFinder(getDriver()).withTitle("Customize Webpart").waitFor();
        Ext4FieldRef.waitForField(this, "Target JBrowse DB");
        Ext4FieldRef.getForLabel(this, "Target JBrowse DB").setValue(sessionId);
        window.clickButton("Submit", WAIT_FOR_PAGE);

        String search = "Gene";

        Locator searchLocator = Locator.tagWithClass("input", "MuiInputBase-input");
        waitForElement(searchLocator);
        WebElement searchBox = searchLocator.findElement(getDriver());
        searchBox.sendKeys(search);

        waitForElement(Locator.tagWithText("li", "Gene0"));
        waitForElement(Locator.tagWithText("li", "Gene1"));
        waitForElement(Locator.tagWithText("li", "Gene3"));
        waitForElement(Locator.tagWithText("li", "Gene4"));
        Locator optionLocator = Locator.tagWithText("li", "Gene1");
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

        Assert.assertEquals("Incorrect URL param", "SIVmac239_Test:4165..5370", getUrlParam("location", true));
    }

    private static final Locator TOP_ROW = Locator.tagWithAttribute("div", "aria-rowindex", "2");

    private void testVariantDataGrid() throws Exception
    {
        waitForTableLoadingToDisappear();

        // Test default
        testColumns("1", "2", "A", "T", "0.029", "HIGH");

        // Test sorting
        Locator referenceSort = Locator.tagWithText("div", "Ref Allele");
        waitForElement(referenceSort);
        WebElement elem = referenceSort.findElement(getDriver());
        elem.click();
        waitForElement(Locator.tagWithText("div", "3,813"));
        elem.click();
        waitForElementToDisappear(Locator.tagWithText("div", "3,813"));
        waitForElement(Locator.tagWithText("div", "6,082"));

        Locator sortedTopRow = Locator.tagWithAttribute("div", "aria-rowindex", "2");
        waitForElement(sortedTopRow);
        testColumns("1", "6,082", "TC", "T", "0.001313", "");

        // Test filtering
        waitAndClick(Locator.tagWithAttributeContaining("button", "aria-label", "Show filters"));

        waitAndClick(Locator.tagWithClass("select", "MuiNativeSelect-select").notHidden().withChild(Locator.tagWithText("option", "Chromosome")));
        waitAndClick(Locator.tagWithAttributeContaining("option", "value", "ref"));

        Locator valueSelector = Locator.tagWithAttributeContaining("input", "placeholder", "Filter value");
        waitAndClick(valueSelector);
        WebElement valueSelectorElem = valueSelector.findElement(getDriver());
        valueSelectorElem.sendKeys("GGC");
        waitForElementToDisappear(Locator.tagWithText("div", "6,082"));

        Locator filteredTopRow = Locator.tagWithAttribute("div", "aria-rowindex", "2");
        waitForElement(filteredTopRow);
        testColumns("1", "6,258", "GGCAT", "G", "0.029", "");

        // Test the table responding to the filtering backend by using the infoFilterWidget
        waitAndClick(Locator.tagWithText("button", "Filter"));
        waitAndClick(Locator.tagWithText("li", "Filter By Attributes"));
        waitAndClick(Locator.tagWithText("button", "Add Filter"));
        waitAndClick(Locator.tagWithText("li", "Allele Frequency"));
        WebElement categorySelect = Locator.tagWithId("div", "category-select").findElement(getDriver()).findElement(By.xpath("./.."));
        categorySelect.click();
        Locator operatorMenu = Locator.tagWithText("li", "=");
        waitAndClick(operatorMenu);
        WebElement value = Locator.tagWithId("input", "standard-number").findElement(getDriver());
        value.sendKeys("0.0009728");
        waitAndClick(Locator.tagWithText("button", "Apply"));
        waitForElementToDisappear(Locator.tagWithText("div", "GGCAT"));
        testColumns("1", "8,401", "ATGGCTCCTG", "A", "0.0009728", "");

        waitForElement(Locator.tagContainingText("button", "mGAP: Showing sites where:").containing("AF == 0.0009728"));

        // Test navigating back to table with InfoFilters intact
        waitAndClickAndWait(Locator.tagWithText("button", "View in Genome Browser"));
        waitForJBrowseToLoad();
        openTrackMenuItem("Filter By Attributes");
        waitForElement(Locator.tagWithText("h6", "Filter Variants"));
        WebElement filterValBrowser = Locator.tagWithId("input", "standard-number").findElement(getDriver());
        Assert.assertEquals("Incorrect filter value", "0.0009728", filterValBrowser.getAttribute("value"));
        waitAndClick(Locator.tagWithText("button", "Apply"));

        // Navigate back to table with InfoFilters intact
        waitAndClick(Locator.tagWithAttribute("button", "data-testid", "track_menu_icon"));
        waitAndClickAndWait(Locator.tagContainingText("span", "View As Table"));
        waitForTableLoadingToDisappear();
        waitForElement(Locator.tagWithText("div", "ATGGCTCCTG")); // indicates the data loaded
        waitAndClick(Locator.tagWithText("button", "Filter"));
        waitAndClick(Locator.tagWithText("li", "Filter By Attributes"));
        WebElement filterValTable = Locator.tagWithId("input", "standard-number").findElement(getDriver());
        Assert.assertEquals("Incorrect filter value", "0.0009728", filterValTable.getAttribute("value"));

        testVariantTableComparators();

        // Ensure the grid works w/o bring provided a location:
        beginAt("/" + getProjectName() + "/jbrowse-variantTable.view?session=mgap&trackId=mgap_hg38");
        waitForElement(Locator.tagWithText("div", "No rows"));
    }

    private void waitForTableLoadingToDisappear()
    {
        waitForElement(Locator.tagWithClass("div", "MuiDataGrid-root"));
        waitForElementToDisappear(Locator.tagWithClass("div", "MuiCircularProgress-root").notHidden(), WAIT_FOR_JAVASCRIPT * 3);
    }

    private void testColumns(String chromosome, String position, String reference, String alt, String af, String impact) {
        waitForElement(TOP_ROW);
        WebElement locator = TOP_ROW.findElement(getDriver());

        for (WebElement elem : locator.findElements(By.xpath("./child::*"))) {
            String value = elem.getText();
            if (StringUtils.trimToNull(value) == null)
            {
                value = "";
            }

            if (StringUtils.isEmpty(elem.getText())) {
                return;
            }

            switch(elem.getAttribute("aria-colindex"))
            {
                case "1":
                    Assert.assertEquals(value, chromosome);
                    break;
                case "2":
                    Assert.assertEquals(value, position);
                    break;
                case "3":
                    Assert.assertEquals(value, reference);
                    break;
                case "4":
                    Assert.assertEquals(value, alt);
                    break;
                case "6":
                    Assert.assertEquals(value, af);
                    break;
                case "7":
                    Assert.assertEquals(value, impact);
                    break;
            }
        }
    }

    private void testGridFailureConditions()
    {
        beginAt("/" + getProjectName() + "/jbrowse-variantTable.view?session=mgap&trackId=mgap_hg38&location=1:18465..18507");
        waitForElement(Locator.tagWithClass("div", "MuiDataGrid-root"));
        waitForElement(Locator.tagWithText("div", "18,486"));

        beginAt("/" + getProjectName() + "/jbrowse-variantTable.view?session=mgap&trackId=mgap_hg38&location=");
        waitForElement(Locator.tagWithClass("div", "MuiDataGrid-root"));
        waitForElement(Locator.tagWithText("div", "No rows").withClass("MuiDataGrid-overlay"));

        // will fail to parse, and then reload without features:
        doAndWaitForPageToLoad(() -> {
            beginAt("/" + getProjectName() + "/jbrowse-variantTable.view?session=mgap&trackId=mgap_hg38&location=1:116999.1", 0);
            assertAlert("Error: could not parse range \"116999.1\" on location \"1:116999.1\"");
        });
        waitForElement(Locator.tagWithClass("div", "MuiDataGrid-root"));
        waitForElement(Locator.tagWithText("div", "No rows").withClass("MuiDataGrid-overlay"));

        doAndWaitForPageToLoad(() -> {
            beginAt("/" + getProjectName() + "/jbrowse-variantTable.view?session=mgap&trackId=mgap_hg38&location=1", 0);
            assertAlert("Must include start/stop in location: 1");
        });
        waitForElement(Locator.tagWithClass("div", "MuiDataGrid-root"));
        waitForElement(Locator.tagWithText("div", "No rows").withClass("MuiDataGrid-overlay"));

        beginAt("/" + getProjectName() + "/jbrowse-variantTable.view?session=mgap&trackId=mgap_hg38&location=1:10-100");
        waitForElement(Locator.tagWithClass("div", "MuiDataGrid-root"));
        waitForElement(Locator.tagWithText("div", "No rows").withClass("MuiDataGrid-overlay"));
    }

    private void testVariantTableComparators() throws Exception {
        beginAt("/" + getProjectName() + "/jbrowse-variantTable.view?session=mgap&trackId=mgap_hg38&location=1:1..430419");
        waitForElement(Locator.tagWithClass("div", "MuiDataGrid-root"));
        waitForElement(Locator.tagWithText("div", "1")); //proxy for grid loading

        // Test filtering AF with wrapped comparators
        waitAndClick(Locator.tagWithAttributeContaining("button", "aria-label", "Show filters"));

        waitAndClick(Locator.tagWithClass("select", "MuiNativeSelect-select").notHidden().withChild(Locator.tagWithText("option", "Chromosome")));
        waitAndClick(Locator.tagWithAttributeContaining("option", "value", "AF"));

        Locator valueSelector = Locator.tagWithAttributeContaining("input", "placeholder", "Filter value");
        waitAndClick(valueSelector);
        WebElement valueSelectorElem = valueSelector.findElement(getDriver());
        valueSelectorElem.sendKeys("0");
        waitForElement(Locator.tagWithText("div", "4,506"));

        Locator filteredTopRow = Locator.tagWithAttribute("div", "aria-rowindex", "2");
        waitForElement(filteredTopRow);
        testColumns("1", "4,506", "GAAAA", "GAA, GAAA, GAAAAA, G, GA, GAAAAAA, GTTAAAA", "0.008258, 0.44, 0.17, 0.036, 0.005367, 0.019, 0", "");
        waitAndClick(Locator.tagWithAttributeContaining("button", "aria-label", "Show filters"));
    }

    private void clearFilterDialog(String filter_text) {
        waitForElementToDisappear(Locator.tagWithClass("div", "MuiBackdrop-root").notHidden());
        waitAndClick(Locator.tagWithText("button", filter_text));
        waitAndClick(Locator.tagWithText("button", "Remove Filter"));
        waitAndClick(Locator.tagWithText("button", "Search").index(1));
        waitForElement(Locator.tagWithText("span", "2"));
    }

    private void testLuceneSearchUI(String sessionId)
    {
        beginAt("/" + getProjectName() + "/jbrowse-jbrowse.view?session=" + sessionId);
        waitAndClick(Locator.tagContainingText("button", "Show all regions in assembly").withClass("MuiButtonBase-root"));
        waitAndClick(Locator.tagWithText("p", "No tracks active."));
        waitAndClick(Locator.tagWithText("button", "Open track selector"));

        Locator l = Locator.tagWithText("span", "TestVCF").withClass("MuiFormControlLabel-label");
        waitAndClick(l);
        getDriver().findElement(Locator.tag("body")).sendKeys(Keys.ESCAPE); //close modal

        openTrackMenuItem("Variant Search", true);

        // VariableSamples in set !TestGroup!
        waitAndClick(Locator.tagWithText("button", "Search"));
        waitForElement(Locator.tagWithAttribute("div", "aria-labelledby", "field-label")).click();
        waitForElement(Locator.tagWithText("li", "Samples With Variant")).click();
        waitForElement(Locator.tagWithAttribute("div", "aria-labelledby", "operator-label")).click();
        waitForElement(Locator.tagWithText("li", "in set")).click();
        waitForElement(Locator.tagWithId("div", "value-select-0")).click();
        waitForElement(Locator.tagWithText("li", "!TestGroup!")).click();
        waitAndClick(Locator.tagWithClass("button", "filter-form-select-button"));
        waitForElementToDisappear(Locator.tagWithText("span", "173"));
        clearFilterDialog("variableSamples in set !TestGroup!");

        // VariableSamples variable in m000001
        waitAndClick(Locator.tagWithText("button", "Search"));
        waitForElement(Locator.tagWithAttribute("div", "aria-labelledby", "field-label")).click();
        waitForElement(Locator.tagWithText("li", "Samples With Variant")).click();
        waitForElement(Locator.tagWithAttribute("div", "aria-labelledby", "operator-label")).click();
        waitForElement(Locator.tagWithText("li", "variable in")).click();
        waitForElement(Locator.tagWithId("input", "value-select-0")).sendKeys("m00001");
        waitForElement(Locator.tagWithId("input", "value-select-0")).sendKeys(Keys.ENTER);
        waitAndClick(Locator.tagWithClass("button", "filter-form-select-button"));
        waitForElement(Locator.tagWithText("span", "0.553"));
        clearFilterDialog("variableSamples variable in m00001");

        // VariableSamples not variable in m05710
        waitAndClick(Locator.tagWithText("button", "Search"));
        waitForElement(Locator.tagWithAttribute("div", "aria-labelledby", "field-label")).click();
        waitForElement(Locator.tagWithText("li", "Samples With Variant")).click();
        waitForElement(Locator.tagWithAttribute("div", "aria-labelledby", "operator-label")).click();
        waitForElement(Locator.tagWithText("li", "not variable in")).click();
        waitForElement(Locator.tagWithId("input", "value-select-0")).sendKeys("m05710");
        waitForElement(Locator.tagWithId("input", "value-select-0")).sendKeys(Keys.ENTER);
        waitAndClick(Locator.tagWithClass("button", "filter-form-select-button"));
        waitForElementToDisappear(Locator.tagWithText("span", "3.277E-4"));
        clearFilterDialog("variableSamples not variable in m05710");

        // VariableSamples li usage + variable in all of
        waitAndClick(Locator.tagWithText("button", "Search"));
        waitForElement(Locator.tagWithAttribute("div", "aria-labelledby", "field-label")).click();
        waitForElement(Locator.tagWithText("li", "Samples With Variant")).click();
        waitForElement(Locator.tagWithAttribute("div", "aria-labelledby", "operator-label")).click();
        waitForElement(Locator.tagWithText("li", "variable in all of")).click();
        waitForElement(Locator.tagWithId("input", "value-select-0")).sendKeys("m000");
        waitForElement(Locator.tagWithText("div", "m00005")).click();
        waitForElement(Locator.tagWithId("input", "value-select-0")).sendKeys("m000");
        waitForElement(Locator.tagWithText("div", "m00004")).click();
        waitAndClick(Locator.tagWithClass("button", "filter-form-select-button"));
        waitForElement(Locator.tagWithText("span", "0.3"));
        clearFilterDialog("variableSamples variable in all of m00005,m00004");

        // VariableSamples variable in any of m00004,m00007
        waitAndClick(Locator.tagWithText("button", "Search"));
        waitForElement(Locator.tagWithAttribute("div", "aria-labelledby", "field-label")).click();
        waitForElement(Locator.tagWithText("li", "Samples With Variant")).click();
        waitForElement(Locator.tagWithAttribute("div", "aria-labelledby", "operator-label")).click();
        waitForElement(Locator.tagWithText("li", "variable in any of")).click();
        waitForElement(Locator.tagWithId("input", "value-select-0")).sendKeys("m000");
        waitForElement(Locator.tagWithText("div", "m00004")).click();
        waitForElement(Locator.tagWithId("input", "value-select-0")).sendKeys("m000");
        waitForElement(Locator.tagWithText("div", "m00007")).click();
        waitAndClick(Locator.tagWithClass("button", "filter-form-select-button"));
        waitForElement(Locator.tagWithText("span", "2"));
        clearFilterDialog("variableSamples variable in any of m00004,m00007");

        // VariableSamples not variable in any of m00005,m00004
        waitAndClick(Locator.tagWithText("button", "Search"));
        waitForElement(Locator.tagWithAttribute("div", "aria-labelledby", "field-label")).click();
        waitForElement(Locator.tagWithText("li", "Samples With Variant")).click();
        waitForElement(Locator.tagWithAttribute("div", "aria-labelledby", "operator-label")).click();
        waitForElement(Locator.tagWithText("li", "not variable in any of")).click();
        waitForElement(Locator.tagWithId("input", "value-select-0")).sendKeys("m000");
        waitForElement(Locator.tagWithText("div", "m00005")).click();
        waitForElement(Locator.tagWithId("input", "value-select-0")).sendKeys("m000");
        waitForElement(Locator.tagWithText("div", "m00004")).click();
        waitAndClick(Locator.tagWithClass("button", "filter-form-select-button"));
        waitForElementToDisappear(Locator.tagWithText("span", "2"));
        clearFilterDialog("variableSamples not variable in any of m00005,m00004");

        // VariableSamples not variable in one of m03660,m00001
        waitAndClick(Locator.tagWithText("button", "Search"));
        waitForElement(Locator.tagWithAttribute("div", "aria-labelledby", "field-label")).click();
        waitForElement(Locator.tagWithText("li", "Samples With Variant")).click();
        waitForElement(Locator.tagWithAttribute("div", "aria-labelledby", "operator-label")).click();
        waitForElement(Locator.tagWithText("li", "not variable in one of")).click();
        waitForElement(Locator.tagWithId("input", "value-select-0")).sendKeys("m036");
        waitForElement(Locator.tagWithText("div", "m03660")).click();
        waitForElement(Locator.tagWithId("input", "value-select-0")).sendKeys("m000");
        waitForElement(Locator.tagWithText("div", "m00001")).click();
        waitAndClick(Locator.tagWithClass("button", "filter-form-select-button"));
        waitForElementToDisappear(Locator.tagWithText("span", "609"));
        clearFilterDialog("variableSamples not variable in one of m03660,m00001");

        // samples with variant isEmpty
        waitAndClick(Locator.tagWithText("button", "Search"));
        waitForElement(Locator.tagWithAttribute("div", "aria-labelledby", "field-label")).click();
        waitForElement(Locator.tagWithText("li", "Samples With Variant")).click();
        waitForElement(Locator.tagWithAttribute("div", "aria-labelledby", "operator-label")).click();
        waitForElement(Locator.tagWithText("li", "is empty")).click();
        waitAndClick(Locator.tagWithClass("button", "filter-form-select-button"));
        waitForElementToDisappear(Locator.tagWithText("span", "2"));
        clearFilterDialog("variableSamples is empty");

        // Start = 2
        waitAndClick(Locator.tagWithText("button", "Search"));
        waitForElement(Locator.tagWithAttribute("div", "aria-labelledby", "field-label")).click();
        waitForElement(Locator.tagWithText("li", "Start")).click();
        waitForElement(Locator.tagWithAttribute("div", "aria-labelledby", "operator-label")).click();
        waitForElement(Locator.tagWithText("li", "=")).click();
        waitForElement(Locator.tagWithId("input", "value-select-0")).sendKeys("2");
        waitAndClick(Locator.tagWithClass("button", "filter-form-select-button"));
        waitForElementToDisappear(Locator.tagWithText("span", "4"));
        clearFilterDialog("start = 2");

        // Start != 2
        waitAndClick(Locator.tagWithText("button", "Search"));
        waitForElement(Locator.tagWithAttribute("div", "aria-labelledby", "field-label")).click();
        waitForElement(Locator.tagWithText("li", "Start")).click();
        waitForElement(Locator.tagWithAttribute("div", "aria-labelledby", "operator-label")).click();
        waitForElement(Locator.tagWithText("li", "!=")).click();
        waitForElement(Locator.tagWithId("input", "value-select-0")).sendKeys("2");
        waitAndClick(Locator.tagWithClass("button", "filter-form-select-button"));
        waitForElementToDisappear(Locator.tagWithText("span", "2"));
        clearFilterDialog("start != 2");

        // Start < 173
        waitAndClick(Locator.tagWithText("button", "Search"));
        waitForElement(Locator.tagWithAttribute("div", "aria-labelledby", "field-label")).click();
        waitForElement(Locator.tagWithText("li", "Start")).click();
        waitForElement(Locator.tagWithAttribute("div", "aria-labelledby", "operator-label")).click();
        waitForElement(Locator.tagWithText("li", "<")).click();
        waitForElement(Locator.tagWithId("input", "value-select-0")).sendKeys("173");
        waitAndClick(Locator.tagWithClass("button", "filter-form-select-button"));
        waitForElementToDisappear(Locator.tagWithText("span", "173"));
        clearFilterDialog("start < 173");

        // Start > 502
        waitAndClick(Locator.tagWithText("button", "Search"));
        waitForElement(Locator.tagWithAttribute("div", "aria-labelledby", "field-label")).click();
        waitForElement(Locator.tagWithText("li", "Start")).click();
        waitForElement(Locator.tagWithAttribute("div", "aria-labelledby", "operator-label")).click();
        waitForElement(Locator.tagWithText("li", ">")).click();
        waitForElement(Locator.tagWithId("input", "value-select-0")).sendKeys("502");
        waitAndClick(Locator.tagWithClass("button", "filter-form-select-button"));
        waitForElementToDisappear(Locator.tagWithText("span", "502"));
        clearFilterDialog("start > 502");

        // Start <= 440
        waitAndClick(Locator.tagWithText("button", "Search"));
        waitForElement(Locator.tagWithAttribute("div", "aria-labelledby", "field-label")).click();
        waitForElement(Locator.tagWithText("li", "Start")).click();
        waitForElement(Locator.tagWithAttribute("div", "aria-labelledby", "operator-label")).click();
        waitForElement(Locator.tagWithText("li", "<=")).click();
        waitForElement(Locator.tagWithId("input", "value-select-0")).sendKeys("440");
        waitAndClick(Locator.tagWithClass("button", "filter-form-select-button"));
        waitForElementToDisappear(Locator.tagWithText("span", "502"));
        clearFilterDialog("start <= 440");

        // Start >= 609
        waitAndClick(Locator.tagWithText("button", "Search"));
        waitForElement(Locator.tagWithAttribute("div", "aria-labelledby", "field-label")).click();
        waitForElement(Locator.tagWithText("li", "Start")).click();
        waitForElement(Locator.tagWithAttribute("div", "aria-labelledby", "operator-label")).click();
        waitForElement(Locator.tagWithText("li", ">=")).click();
        waitForElement(Locator.tagWithId("input", "value-select-0")).sendKeys("609");
        waitAndClick(Locator.tagWithClass("button", "filter-form-select-button"));
        waitForElementToDisappear(Locator.tagWithText("span", "2"));
        clearFilterDialog("start >= 609");

        // Ref Allele equals GAAAA
        waitForElement(Locator.tagWithText("span", "0.029"));
        waitAndClick(Locator.tagWithText("button", "Search"));
        waitForElement(Locator.tagWithAttribute("div", "aria-labelledby", "field-label")).click();
        waitForElement(Locator.tagWithText("li", "Ref Allele")).click();
        waitForElement(Locator.tagWithAttribute("div", "aria-labelledby", "operator-label")).click();
        waitForElement(Locator.tagWithText("li", "equals")).click();
        waitForElement(Locator.tagWithId("input", "value-select-0")).sendKeys("GAAAA");
        waitAndClick(Locator.tagWithClass("button", "filter-form-select-button"));
        waitForElement(Locator.tagWithText("span", "4506"));
        clearFilterDialog("ref equals GAAAA");

        // Impact equals HIGH
        waitForElement(Locator.tagWithText("span", "0.029"));
        waitAndClick(Locator.tagWithText("button", "Search"));
        waitForElement(Locator.tagWithAttribute("div", "aria-labelledby", "field-label")).click();
        waitForElement(Locator.tagWithText("li", "Impact on Protein Coding")).click();
        waitForElement(Locator.tagWithAttribute("div", "aria-labelledby", "operator-label")).click();
        waitForElement(Locator.tagWithText("li", "equals")).click();
        waitForElement(Locator.tagWithId("input", "value-select-0")).sendKeys("HI");
        waitForElement(Locator.tagWithId("input", "value-select-0")).sendKeys(Keys.ENTER);
        waitAndClick(Locator.tagWithClass("button", "filter-form-select-button"));
        waitForElementToDisappear(Locator.tagWithText("span", "4"));
        clearFilterDialog("IMPACT equals HIGH");

        // Ref Allele does not equal A
        waitForElement(Locator.tagWithText("span", "0.029"));
        waitAndClick(Locator.tagWithText("button", "Search"));
        waitForElement(Locator.tagWithAttribute("div", "aria-labelledby", "field-label")).click();
        waitForElement(Locator.tagWithText("li", "Ref Allele")).click();
        waitForElement(Locator.tagWithAttribute("div", "aria-labelledby", "operator-label")).click();
        waitForElement(Locator.tagWithText("li", "does not equal")).click();
        waitForElement(Locator.tagWithId("input", "value-select-0")).sendKeys("A");
        waitAndClick(Locator.tagWithClass("button", "filter-form-select-button"));
        waitForElement(Locator.tagWithText("span", "858"));
        clearFilterDialog("ref does not equal A");

        // Alt Allele contains TT
        waitForElement(Locator.tagWithText("span", "0.029"));
        waitAndClick(Locator.tagWithText("button", "Search"));
        waitForElement(Locator.tagWithAttribute("div", "aria-labelledby", "field-label")).click();
        waitForElement(Locator.tagWithText("li", "Alt Allele")).click();
        waitForElement(Locator.tagWithAttribute("div", "aria-labelledby", "operator-label")).click();
        waitForElement(Locator.tagWithText("li", "contains")).click();
        waitForElement(Locator.tagWithId("input", "value-select-0")).sendKeys("TT");
        waitAndClick(Locator.tagWithClass("button", "filter-form-select-button"));
        waitForElement(Locator.tagWithText("span", "22559"));
        clearFilterDialog("alt contains TT");

        // Alt Allele does not contain T
        waitForElement(Locator.tagWithText("span", "0.029"));
        waitAndClick(Locator.tagWithText("button", "Search"));
        waitForElement(Locator.tagWithAttribute("div", "aria-labelledby", "field-label")).click();
        waitForElement(Locator.tagWithText("li", "Alt Allele")).click();
        waitForElement(Locator.tagWithAttribute("div", "aria-labelledby", "operator-label")).click();
        waitForElement(Locator.tagWithText("li", "does not contain")).click();
        waitForElement(Locator.tagWithId("input", "value-select-0")).sendKeys("T");
        waitAndClick(Locator.tagWithClass("button", "filter-form-select-button"));
        waitForElementToDisappear(Locator.tagWithText("span", "2"));
        clearFilterDialog("alt does not contain T");

        // Ref Allele starts with GA
        waitForElement(Locator.tagWithText("span", "0.029"));
        waitAndClick(Locator.tagWithText("button", "Search"));
        waitForElement(Locator.tagWithAttribute("div", "aria-labelledby", "field-label")).click();
        waitForElement(Locator.tagWithText("li", "Ref Allele")).click();
        waitForElement(Locator.tagWithAttribute("div", "aria-labelledby", "operator-label")).click();
        waitForElement(Locator.tagWithText("li", "starts with")).click();
        waitForElement(Locator.tagWithId("input", "value-select-0")).sendKeys("GA");
        waitAndClick(Locator.tagWithClass("button", "filter-form-select-button"));
        waitForElement(Locator.tagWithText("span", "14716"));
        clearFilterDialog("ref starts with GA");

        // Ref Allele ends with AAA
        waitForElement(Locator.tagWithText("span", "0.029"));
        waitAndClick(Locator.tagWithText("button", "Search"));
        waitForElement(Locator.tagWithAttribute("div", "aria-labelledby", "field-label")).click();
        waitForElement(Locator.tagWithText("li", "Ref Allele")).click();
        waitForElement(Locator.tagWithAttribute("div", "aria-labelledby", "operator-label")).click();
        waitForElement(Locator.tagWithText("li", "ends with")).click();
        waitForElement(Locator.tagWithId("input", "value-select-0")).sendKeys("AAA");
        waitAndClick(Locator.tagWithClass("button", "filter-form-select-button"));
        waitForElement(Locator.tagWithText("span", "4506"));
        clearFilterDialog("ref ends with AAA");

        // Alt Allele is empty
        waitAndClick(Locator.tagWithText("button", "Search"));
        waitForElement(Locator.tagWithAttribute("div", "aria-labelledby", "field-label")).click();
        waitForElement(Locator.tagWithText("li", "Alt Allele")).click();
        waitForElement(Locator.tagWithAttribute("div", "aria-labelledby", "operator-label")).click();
        waitForElement(Locator.tagWithText("li", "is empty")).click();
        waitAndClick(Locator.tagWithClass("button", "filter-form-select-button"));
        waitForElement(Locator.tagWithText("span", "18235"));
        clearFilterDialog("alt is empty");

        // IMPACT is not empty
        waitAndClick(Locator.tagWithText("button", "Search"));
        waitForElement(Locator.tagWithAttribute("div", "aria-labelledby", "field-label")).click();
        waitForElement(Locator.tagWithText("li", "Impact on Protein Coding")).click();
        waitForElement(Locator.tagWithAttribute("div", "aria-labelledby", "operator-label")).click();
        waitForElement(Locator.tagWithText("li", "is not empty")).click();
        waitAndClick(Locator.tagWithClass("button", "filter-form-select-button"));
        waitForElement(Locator.tagWithText("span", "914"));
        clearFilterDialog("IMPACT is not empty");

        // IMPACT HIGH+MODERATE
        waitAndClick(Locator.tagWithText("button", "Search"));
        waitForElement(Locator.tagWithAttribute("div", "aria-labelledby", "field-label")).click();
        waitForElement(Locator.tagWithText("li", "Impact on Protein Coding")).click();
        waitForElement(Locator.tagWithAttribute("div", "aria-labelledby", "operator-label")).click();
        waitForElement(Locator.tagWithText("li", "equals")).click();
        waitForElement(Locator.tagWithId("input", "value-select-0")).sendKeys("HI");
        waitForElement(Locator.tagWithId("input", "value-select-0")).sendKeys(Keys.ENTER);
        waitForElement(Locator.tagWithId("input", "value-select-0")).sendKeys("MO");
        waitForElement(Locator.tagWithId("input", "value-select-0")).sendKeys(Keys.ENTER);
        waitAndClick(Locator.tagWithClass("button", "filter-form-select-button"));
        waitForElement(Locator.tagWithText("span", "0.029"));

        clearFilterDialog("IMPACT equals HIGH,MODERATE");
    }
}