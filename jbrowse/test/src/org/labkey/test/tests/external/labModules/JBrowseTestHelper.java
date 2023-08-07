package org.labkey.test.tests.external.labModules;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.Assert;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.TestFileUtils;
import org.labkey.test.WebDriverWrapper;
import org.labkey.test.components.ext4.Window;
import org.labkey.test.util.DataRegionTable;
import org.labkey.test.util.Ext4Helper;
import org.labkey.test.util.ext4cmp.Ext4CmpRef;
import org.labkey.test.util.ext4cmp.Ext4FieldRef;
import org.labkey.test.util.ext4cmp.Ext4GridRef;
import org.openqa.selenium.By;
import org.openqa.selenium.Point;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebElement;

import java.io.File;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public class JBrowseTestHelper
{
    public static final File MGAP_TEST_VCF = new File(TestFileUtils.getLabKeyRoot(), "server/modules/DiscvrLabKeyModules/jbrowse/resources/web/jbrowse/mgap/mGap.v2.1.subset.vcf.gz");
    public static final File GRCH37_GENOME = new File(TestFileUtils.getLabKeyRoot(), "server/modules/DiscvrLabKeyModules/jbrowse/resources/web/jbrowse/mgap/GRCh37_small.fasta");

    public static Collector<WebElement, ?, WebElement> toSingleton() {
        return Collectors.collectingAndThen(
                Collectors.toList(),
                list -> {
                    if (list.size() != 1) {
                        long uniqueLocations = list.stream().map(WebElement::getLocation).distinct().count();
                        if (uniqueLocations == 1) {
                            return list.get(0);
                        }

                        throw new IllegalStateException("Expected single element, found: " + list.size() + ", " + list.stream().map(WebElement::getLocation).map(Point::toString).collect(Collectors.joining(" / ")));
                    }

                    return list.get(0);
                }
        );
    }

    public static void prepareSearchSession(BaseWebDriverTest test, String projectName) throws Exception
    {
        //create session w/ some of these, verify
        test.log("creating initial jbrowse session");
        test.beginAt("/query/" + projectName + "/executeQuery.view?query.queryName=outputfiles&schemaName=sequenceanalysis&query.category~eq=VCF File");
        DataRegionTable dr = DataRegionTable.DataRegion(test.getDriver()).find();
        dr.uncheckAllOnPage();
        dr.checkCheckbox(0);
        dr.clickHeaderButton("Visualize/Analyze Data");
        new Window.WindowFinder(test.getDriver()).withTitle("Visualize/Analyze Files").waitFor();
        test.waitForElement(Locator.tagWithText("div", "View In JBrowse"));
        test.click(Locator.tagWithText("div", "View In JBrowse"));
        test.waitAndClick(Ext4Helper.Locators.ext4Button("Submit"));

        String sessionName = "SearchSession";
        int existingPipelineJobs = SequenceTest.getTotalPipelineJobs(test);
        new Window.WindowFinder(test.getDriver()).withTitle("Create/Modify JBrowse Session").waitFor();
        Ext4FieldRef.getForLabel(test, "Name").setValue(sessionName);
        Ext4FieldRef.getForLabel(test, "Description").setValue("This is a session to test full-text search");
        test.waitAndClick(Ext4Helper.Locators.ext4ButtonEnabled("Submit"));

        Window<?> window = new Window.WindowFinder(test.getDriver()).withTitle("Create New Workbook or Add To Existing?").waitFor();
        window.clickButton("Submit", 0);

        window = new Window.WindowFinder(test.getDriver()).withTitle("Success").waitFor();
        window.clickButton("OK");
        test.waitForPipelineJobsToComplete(existingPipelineJobs + 1, "Create New Session", false);
    }

    public static Pair<String, String> configureSearchSession(BaseWebDriverTest test, String projectName) throws Exception
    {
        test.beginAt("/" + projectName + "/query-executeQuery.view?schemaName=jbrowse&queryName=databases");

        DataRegionTable dr = DataRegionTable.DataRegion(test.getDriver()).find();
        dr.clickRowDetails(0);

        // Find trackId:
        test.waitForElement(Locator.tagWithText("span", "Resources Displayed In This Session"));
        dr = DataRegionTable.findDataRegionWithinWebpart(test, "Resources Displayed In This Session");
        String trackId = dr.getDataAsText(0, "Resource");
        String sessionId = test.getUrlParam("databaseId");

        test.waitForElement(Locator.tagWithText("span", "Tracks Provided By This Session"));
        dr = DataRegionTable.findDataRegionWithinWebpart(test, "Tracks Provided By This Session");
        Assert.assertEquals("Incorrect row count", 1, dr.getDataRowCount());
        dr.checkCheckbox(dr.getRowIndex("File Id", "TestVCF"));
        dr.clickHeaderMenu("More Actions", false, "Modify Track Config");
        new Window.WindowFinder(test.getDriver()).withTitle("Modify Track Config").waitFor();
        Ext4CmpRef.waitForComponent(test, "grid");
        Ext4GridRef grid = test._ext4Helper.queryOne("grid", Ext4GridRef.class);
        test.waitAndClick(Ext4Helper.Locators.ext4ButtonEnabled("Add"));
        test.waitAndClick(Ext4Helper.Locators.menuItem("Create Full Text Index?"));
        test.waitForElement(Locator.tagWithText("div", "createFullTextIndex"));
        grid.completeEdit();

        test.waitAndClick(Ext4Helper.Locators.ext4ButtonEnabled("Add"));
        test.waitAndClick(Ext4Helper.Locators.menuItem("Info Fields For Full Text Search"));
        test.waitForElement(Locator.tagWithText("div", "infoFieldsForFullTextSearch"));
        grid.setGridCell(2, "value", "AF,AC,CADD_PH,CLN_ALLELE,OMIMD,IMPACT");

        test.waitAndClick(Ext4Helper.Locators.ext4ButtonEnabled("Submit"));
        new Window.WindowFinder(test.getDriver()).withTitle("Success").waitFor();
        test.waitAndClick(Ext4Helper.Locators.ext4ButtonEnabled("OK"));

        test.beginAt("/query/" + projectName + "/executeQuery.view?query.queryName=jsonfiles&schemaName=jbrowse");
        int existingPipelineJobs = SequenceTest.getTotalPipelineJobs(test);
        dr = DataRegionTable.DataRegion(test.getDriver()).find();
        dr.checkCheckbox(dr.getRowIndex("File Id", "TestVCF"));
        dr.clickHeaderMenu("More Actions", false, "Re-process Selected");
        new Window.WindowFinder(test.getDriver()).withTitle("Reprocess Resources").waitFor();
        test.waitAndClick(Ext4Helper.Locators.ext4ButtonEnabled("Submit"));
        new Window.WindowFinder(test.getDriver()).withTitle("Success").waitFor();
        test.waitAndClickAndWait(Ext4Helper.Locators.ext4ButtonEnabled("OK"));
        test.waitForPipelineJobsToComplete(existingPipelineJobs + 1, "Recreating Resources", false);

        return Pair.of(sessionId, trackId);
    }

    public static Locator.XPathLocator getTrackLocator(BaseWebDriverTest test, String trackId, boolean waitFor)
    {
        trackId = "trackRenderingContainer-mgap-" + trackId;
        Locator.XPathLocator l = Locator.tagWithAttributeContaining("div", "data-testid", trackId);
        if (waitFor)
        {
            test.waitForElement(l);
        }

        return l;
    }

    public static By getVariantWithinTrack(BaseWebDriverTest test, String trackId, String variantText)
    {
        return getVariantWithinTrack(test, trackId, variantText, true);
    }

    public static By getVariantWithinTrack(BaseWebDriverTest test, String trackId, String variantText, boolean appendPolygon)
    {
        Locator.XPathLocator l = getTrackLocator(test, trackId, true);
        test.waitForElementToDisappear(Locator.tagWithText("p", "Loading"));
        l = l.append(Locator.xpath("//*[name()='text' and contains(text(), '" + variantText + "')]")).notHidden().parent();
        if (appendPolygon){
            l = l.append("/*[name()='polygon']");
        }

        test.waitForElement(l);

        return By.xpath(l.toXpath());
    }

    public static void waitForJBrowseToLoad(BaseWebDriverTest test)
    {
        test.waitForElementToDisappear(Locator.tagWithText("p", "Loading...")); //the initial message before getSession
        test.waitForElement(Locator.tagWithClass("span", "MuiTouchRipple-root").notHidden()); //this is the top-left icon
        test.waitForElement(Locator.tagWithAttribute("button", "title", "close this track").notHidden());
        test.waitForElement(Locator.tagWithClassContaining("button", "MuiButtonBase-root").notHidden(), WebDriverWrapper.WAIT_FOR_PAGE); //this is the icon from the track label

        test.waitForElementToDisappear(Locator.tagWithText("div", "Loading")); //track data
        test.waitForElementToDisappear(Locator.tagWithText("p", "Loading").withClass("MuiTypography-root")); // the track data
    }

    public static long getTotalVariantFeatures(BaseWebDriverTest test)
    {
        Locator l = Locator.tagWithAttribute("svg", "data-testid", "svgfeatures").append(Locator.tag("polygon"));
        try
        {
            // NOTE: JBrowse renders features using multiple blocks per track, and these tracks can redundantly render identical features on top of one another.
            // Counting unique locations is indirect, but should result in unique features
            return Locator.findElements(test.getDriver(), l).stream().filter(WebElement::isDisplayed).map(WebElement::getLocation).distinct().count();
        }
        catch (StaleElementReferenceException e)
        {
            test.log("Stale elements, retrying");
            WebDriverWrapper.sleep(5000);

            return Locator.findElements(test.getDriver(), l).stream().filter(WebElement::isDisplayed).map(WebElement::getLocation).distinct().count();
        }
    }
}
