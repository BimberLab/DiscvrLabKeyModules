
import java.io.*;
import java.util.*;


import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.DateUtil;

import com.google.common.collect.*;

public class MicrobiomeTransform extends AbstractAssayValidator
{
    private File _errorFile;
    private Map<String, String> _runProperties = new HashMap<>();
    private Map<Integer, String> _colMap = new HashMap<>();
    private Map<String, String> _dictionary = new HashMap<>();
    private final int numHeaderCols = 4;

    public static void main(String[] args)
    {

        if (args.length < 1)
            throw new IllegalArgumentException("Run properties file not passed in.");

        File runProperties = new File(args[0]);
        if (runProperties.exists())
        {

            MicrobiomeTransform transform = new MicrobiomeTransform();
            transform.runTransform(runProperties);

        }
        else
            throw new IllegalArgumentException("Input data file does not exist");

    }

    private String getValue(Cell cell)
    {
        StringBuilder value = new StringBuilder();

        if(null != cell)
        {
            switch (cell.getCellType())
            {
                case Cell.CELL_TYPE_STRING:
                    value.append(cell.getStringCellValue());
                    break;
                case Cell.CELL_TYPE_NUMERIC:
                    if (DateUtil.isCellDateFormatted(cell))
                    {
                        value.append(cell.getDateCellValue());
                    }
                    else
                    {
                        value.append(((Double)cell.getNumericCellValue()).intValue());
                    }
                    break;
                case Cell.CELL_TYPE_BOOLEAN:
                    value.append(cell.getBooleanCellValue());
                    break;
            }
        }

        return value.toString();
    }

    private String getColumns(Row row)
    {
        Iterator<Cell> cellIter = row.iterator();
        StringBuilder sb = new StringBuilder();

        int col = 0;
        int i = 0;
        Cell cell;
        String s;

        // column header lookup HashMap
        _dictionary.put("Sample Id", "SampleId\t");
        _dictionary.put("Marmoset ID", "ParticipantId\t");
        _dictionary.put("Sample Date", "Date\t");
        //_dictionary.put("UI ID", "UiId\t");
        _dictionary.put("Barcode", "Barcode\t");

        try
        {

            // Populate column map
            while (cellIter.hasNext())
            {

                cell = cellIter.next();
                _colMap.put(col++, getValue(cell));

                // Get header columns
                if (i < numHeaderCols)
                {
                    s = _dictionary.get(getValue(cell));
                    if (s != null)
                    {
                        sb.append(s);
                    }
                    else
                    {
                        writeError("Encountered Invalid column header: " + getValue(cell) + " " + sb.toString(), "");
                       //throw new IllegalArgumentException("Encountered Invalid column header: " + getValue(cell) + "\n");
                    }

                }
                i++;

            }
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }

        //i++;

        sb.append("OTU\tValue\n");

        return sb.toString();
    }


    // Can add more checking here
    private boolean isValid(String common)
    {
        return !common.isEmpty() && common.trim().length() > 0;
    }


    private String processRow(Row row)
    {
        StringBuilder rows = new StringBuilder();
        StringBuilder common = new StringBuilder();
        StringBuilder notes = new StringBuilder();
        int note = 0, col;
        Cell cell;

        int i = 0;
        Integer rowNum = 0;

        for (Map.Entry<Integer, String> entry : _colMap.entrySet())
        {
            // get columns that will be used as common values for each unpivoted row
            if (i < numHeaderCols)
            {
                i++;
                col = entry.getKey();
                cell = row.getCell(col);
                try
                {
                    if (cell != null)
                    {
                        common.append(getValue(cell));
                        common.append('\t');
                    }
                    else
                    {
                        rowNum = row.getRowNum();
                        writeError("Missing row identifyer informaion on row: " + rowNum.toString(), "");
                    }
                }
                catch (IOException e)
                {
                    throw new RuntimeException(e);
                }
            }

            else
            {
                // Get OTUs
                col = entry.getKey();
                String value = getValue(row.getCell(col));
                try
                {
                    // only store rows with valued > 0 10/31/2017 tjh
                    int intValue = Integer.parseInt(value);
                    // is an integer!

                    if (intValue > 0)
                    {
                        rows.append(common);
                        rows.append(entry.getValue() + "\t");
                        rows.append(value);
                        rows.append('\n');
                    }
                } catch (NumberFormatException e) {
                    // not an integer!
                }
            }
        }
        return rows.toString();
    }

    public void runTransform(File inputFile)
    {



        parseRunProperties(inputFile);

        try
        {


            //writeError("runfile name: " + inputFile, "");

            if (getRunProperties().containsKey(Props.runDataFile.name()))
            {

                File inputDataFile = new File(getRunProperty(Props.runDataUploadedFile));
                File transformFile = new File(getTransformFile().get(getRunProperty(Props.runDataFile)));

                try (PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(transformFile))))
                {
                    try {
                        //writeError("inputDataFile  " + inputDataFile.getName() , "");
                        FileInputStream stream = new FileInputStream(inputDataFile);

                        Workbook workbook = new XSSFWorkbook(stream);

                        Sheet firstSheet = workbook.getSheetAt(0);

                        Iterator<Row> rowIter = firstSheet.iterator();

                        boolean first = true;
                        StringBuilder sb;

                        while (rowIter.hasNext())
                        {
                            Row nextRow = rowIter.next();
                            Iterator<Cell> cellIter = nextRow.iterator();

                            sb = new StringBuilder();
                            if(first)
                            {

                                sb.append(getColumns(nextRow));
                                System.out.println(sb.toString());
                                first = false;

                            }
                            else
                            {
                                sb.append(processRow(nextRow));
                            }

                            writer.print(sb.toString());
                        }

                    }
                    catch (Exception e)
                    {
                        writeError("Error opening Excel workbook: " + inputDataFile, "");
                    }
                }
            }

            else
                writeError("Unable to locate the runDataFile", "runDataFile");
        }

        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }
}
