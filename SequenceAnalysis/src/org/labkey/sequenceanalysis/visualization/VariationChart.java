package org.labkey.sequenceanalysis.visualization;

import org.apache.batik.dom.GenericDOMImplementation;
import org.apache.batik.svggen.SVGGraphics2D;
import org.biojava.nbio.genome.parsers.gff.FeatureI;
import org.biojava.nbio.genome.parsers.gff.FeatureList;
import org.biojava.nbio.genome.parsers.gff.GFF3Reader;
import org.biojava.nbio.genome.parsers.gff.Location;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.annotations.XYShapeAnnotation;
import org.jfree.chart.annotations.XYTextAnnotation;
import org.jfree.chart.axis.AxisSpace;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.NumberTickUnit;
import org.jfree.chart.axis.SymbolAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XYDataItem;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.jfree.ui.RectangleEdge;
import org.jfree.ui.TextAnchor;
import org.json.old.JSONObject;
import org.labkey.api.util.FileUtil;
import org.labkey.api.writer.PrintWriters;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: bimber
 * Date: 2/23/14
 * Time: 8:34 AM
 */
public class VariationChart
{
    public VariationChart()
    {

    }

    public List<JFreeChart> createChart(String[] series, String gff, int maxBasesPerRow) throws IOException
    {
        FeatureList features = readGff(gff);
        List<XYSeries> datasets = parseVariscanData(series);

        NumberAxis xAxis = new NumberAxis("Position");
        xAxis.setAutoRangeIncludesZero(true);

        NumberAxis yAxis = new NumberAxis("Changes Per 100bp");
        yAxis.setAutoRangeIncludesZero(true);

        List<JFreeChart> charts = new ArrayList<>();
        List<XYPlot> plots = getPlots(maxBasesPerRow, datasets, features);

        for (XYPlot plot : plots)
        {
            JFreeChart chart = new JFreeChart(null, JFreeChart.DEFAULT_TITLE_FONT, plot, false);
            chart.setBackgroundPaint(Color.white);

            charts.add(chart);
        }

        return charts;
    }

    private List<XYPlot> getPlots(int maxBasesPerRow, List<XYSeries> series, FeatureList features)
    {
        List<XYPlot> ret = new ArrayList<>();
        int rangeStart = 0;
        int rangeEnd = 0;
        int minY = 0;
        int maxY = 0;

        for (XYSeries s : series)
        {
            if (((Double)s.getMinX()).intValue() < rangeStart)
                rangeStart = ((Double)s.getMinX()).intValue();

            if (((Double)s.getMaxX()).intValue() > rangeEnd)
                rangeEnd = ((Double)s.getMaxX()).intValue();

            if (((Double)s.getMinY()).intValue() < minY)
                minY = ((Double)s.getMinY()).intValue();

            if (((Double)s.getMaxY()).intValue() > maxY)
                maxY = ((Double)s.getMaxY()).intValue();
        }

        int windowStart = rangeStart;
        while (windowStart < rangeEnd)
        {
            int windowEnd = Math.min(windowStart + maxBasesPerRow, rangeEnd);

            //build annotations
            XYPlot annotationPlot = createAnnotationPlot(features, windowStart, windowEnd);
            annotationPlot.getDomainAxis().setVisible(false);
            annotationPlot.getDomainAxis().setRange(windowStart, windowStart + maxBasesPerRow);
            ret.add(annotationPlot);

            //then data
            int idx = 0;
            for (XYSeries s : series)
            {
                XYDataset newDataset = getDatasetSubset(s, windowStart, windowEnd);
                NumberAxis rangeAxis = new NumberAxis(s.getKey().toString());
                rangeAxis.setRange(minY, maxY);
                rangeAxis.setTickUnit(new NumberTickUnit(10));

                XYPlot dataPlot = new XYPlot(newDataset, new NumberAxis(), rangeAxis, new XYLineAndShapeRenderer(true, false));
                dataPlot.getDomainAxis().setRange(windowStart, (windowStart + maxBasesPerRow) + (maxBasesPerRow * .01));
                ((NumberAxis)dataPlot.getDomainAxis()).setTickUnit(new NumberTickUnit(1000));
                dataPlot.setWeight(4);

                ret.add(dataPlot);
                idx++;

                if (idx < series.size())
                {
                    dataPlot.getDomainAxis().setVisible(false);
                }
            }

            //add a spacer
            XYPlot p = new XYPlot();
            p.setWeight(1);
            p.setOutlineVisible(false);
            ret.add(p);

            windowStart = windowEnd + 1;
        }

        return ret;
    }

    private XYDataset getDatasetSubset(XYSeries series, int windowStart, int windowEnd)
    {
        XYSeries newSeries = new XYSeries(series.getKey());
        for (Object item : series.getItems())
        {
            double x = ((XYDataItem)item).getX().doubleValue();
            if (x >= windowStart && x <= windowEnd)
            {
                newSeries.add((XYDataItem)item);
            }
        }

        XYSeriesCollection ret = new XYSeriesCollection();
        ret.addSeries(newSeries);

        return ret;
    }

    private XYPlot createAnnotationPlot(FeatureList features, int windowStart, int windowEnd)
    {
        NumberAxis xAxis = new NumberAxis("Position");
        xAxis.setAutoRangeIncludesZero(false);
        SymbolAxis yAxis = new SymbolAxis("", new String[]{""});
        yAxis.setGridBandsVisible(false);
        yAxis.setAxisLineVisible(false);
        yAxis.setTickMarksVisible(false);
        XYItemRenderer renderer = new XYLineAndShapeRenderer(true, false);
        XYPlot plot = new XYPlot(new XYSeriesCollection(), xAxis, yAxis, renderer);
        plot.setDomainGridlinesVisible(false);
        plot.setRangeGridlinesVisible(false);
        plot.setOutlineVisible(false);

        features.sort((o1, o2) ->
        {
            if (o1 == null)
            {
                return -1;
            }
            else if (o2 == null)
            {
                return 1;
            }

            return Integer.compare(o1.location().plus().bioStart(), o2.location().plus().bioStart());
        });

        Iterator <FeatureI> i = features.iterator();
        Map<Integer, Set<Integer>> occupied = new HashMap<>();
        int maxTrackNumber = 0;

        while (i.hasNext())
        {
            FeatureI f = i.next();
            Location loc = f.location().plus();
            if (loc.bioEnd() < windowStart || loc.bioStart() > windowEnd)
                continue;

            int start = Math.max(loc.bioStart(), windowStart);
            int end = Math.min(loc.bioEnd(), windowEnd);

            int trackNumber = getTrackNumber(loc, occupied);
            if (trackNumber > maxTrackNumber)
                maxTrackNumber = trackNumber;

            int x = start;
            int y = ((trackNumber * -4));
            XYShapeAnnotation annot = new XYShapeAnnotation(new Rectangle2D.Double(x, y, (end - start), 4.0), new BasicStroke(1.0f), Color.black, Color.lightGray);
            plot.addAnnotation(annot);

            XYTextAnnotation annot2 = new XYTextAnnotation(f.getAttribute("Name"), x + 10, y + 1.5);
            annot2.setTextAnchor(TextAnchor.CENTER_LEFT);
            plot.addAnnotation(annot2);
        }

        double height = (maxTrackNumber * -4) - 0.5;
        plot.getRangeAxis().setRange(height, 0.5);

        plot.setWeight((int)(0.25 * (-1 * height + 1)));

        return plot;
    }

    private int getTrackNumber(Location loc, Map<Integer, Set<Integer>> occupied)
    {
        int trackNumber = 1;
        while (trackNumber < 30)
        {
            Set<Integer> occupiedSpaces = occupied.get(trackNumber);
            if (occupiedSpaces == null)
            {
                occupied.put(trackNumber, new HashSet<Integer>());
                return trackNumber;
            }

            boolean found = false;
            for (int pos = loc.getBegin();pos < loc.bioEnd();pos++)
            {
                if (occupiedSpaces.contains(pos))
                {
                    found = true;
                    break;
                }
            }

            //if not found, claim these positions
            if (!found)
            {
                for (int pos = loc.getBegin();pos < loc.bioEnd();pos++)
                {
                    occupiedSpaces.add(pos);
                }

                occupied.put(trackNumber, occupiedSpaces);

                return trackNumber;
            }
            else
            {
                trackNumber++;
            }
        }

        throw new IllegalArgumentException("Unable to create sequence track");
    }

    private List<XYSeries> parseVariscanData(String[] series) throws IOException
    {
        List<XYSeries> ret = new ArrayList<>();
        for (String s : series)
        {
            JSONObject json = new JSONObject(s);
            String name = json.getString("name");
            try (BufferedReader reader = new BufferedReader(new StringReader(json.getString("data"))))
            {
                String line;
                XYSeries series1 = new XYSeries(name);
                while ((line = reader.readLine()) != null)
                {
                    if (line == null || line.startsWith("#"))
                        continue;

                    String[] tokens = line.split("\\s+");

                    if (tokens.length < 9)
                        continue;

                    series1.add(Double.parseDouble(tokens[3]), Double.parseDouble(tokens[9]));
                }

                ret.add(series1);
            }
        }

        return ret;
    }

    private FeatureList readGff(String gff) throws IOException
    {
        if (gff == null)
            return new FeatureList();

        File f = FileUtil.createTempFile("variationChart", ".gff");
        try (PrintWriter writer = PrintWriters.getPrintWriter(f))
        {
            writer.write(gff);
        }

        FeatureList ret = GFF3Reader.read(f.getPath());
        f.delete();

        return ret;
    }

    public Map<String, Object> toSVG(List<JFreeChart> charts, int width, int height) throws IOException
    {
        Map<String, Object> props = new HashMap<>();
        DOMImplementation mySVGDOM = GenericDOMImplementation.getDOMImplementation();
        Document document = mySVGDOM.createDocument(null, "svg", null);
        SVGGraphics2D generator = new SVGGraphics2D(document);

        //normalize range axis
        AxisSpace space = new AxisSpace();
        for (JFreeChart chart : charts)
        {
            AxisSpace subSpace = new AxisSpace();
            if (chart.getXYPlot().getRangeAxis() != null)
            {
                chart.getXYPlot().getRangeAxis().reserveSpace(generator, chart.getXYPlot(), new Rectangle2D.Double(0, 0, width, height), RectangleEdge.LEFT, subSpace);
                space.ensureAtLeast(subSpace);
            }

            chart.getXYPlot().setFixedRangeAxisSpace(space);
        }

        int offset = 0;
        int i = 0;
        for (JFreeChart chart : charts)
        {
            double weight = ((XYPlot)chart.getPlot()).getWeight() / 4.0;
            chart.draw(generator, new Rectangle2D.Double(0, offset, width, weight * height), null);
            offset += (weight * height);
            i++;
        }

        File output = FileUtil.createTempFile("sequenceGraph", ".svg");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(output)))
        {
            generator.stream(writer);
        }

        props.put("filePath", output.getPath());
        props.put("fileName", output.getName());
        props.put("width", width);
        props.put("height", offset);

        return props;
    }
}