
import java.io.*;
import java.util.*;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.DateUtil;

import com.google.common.collect.*;

public class GeneticsTransform extends AbstractAssayValidator
{
    private File _errorFile;
    private Map<String, String> _runProperties = new HashMap<>();
    private Map<String, String> _transformFile = new HashMap<>();
    private List<String> _errors = new ArrayList<>();
    private ListMultimap<Integer, String> _columnMap = Multimaps.newListMultimap(new TreeMap<>(), ArrayList::new);

    private String LOCUS = "Locus";
    private String ALLELE1 = "Allele_1";
    private String ALLELE2 = "Allele_2";

    public static void main(String[] args)
    {

        if (args.length < 1)
            throw new IllegalArgumentException("Run properties file not passed in.");

        File runProperties = new File(args[0]);
        if (runProperties.exists())
        {
            GeneticsTransform transform = new GeneticsTransform();

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

        // Populate column map
        while (cellIter.hasNext())
        {
            Cell cell = cellIter.next();

            // Special case for blank first column
            if(col == 0 && getValue(cell).isEmpty())
            {
                _columnMap.put(col++, "File");
            }
            else if(getValue(cell).isEmpty() || getValue(cell).equals("Type"))
            {
                _columnMap.put(col, LOCUS);
                _columnMap.put(col, ALLELE1);
                _columnMap.put(col++, ALLELE2);
            }
            else
            {
                _columnMap.put(col++, getValue(cell));
            }
        }

        // Print column titles. Don't replicate repeated columns
        boolean locusFound = false, allele1Found = false, allele2Found = false, noteFound = false;
        for(Map.Entry<Integer, String> entry : _columnMap.entries())
        {
            if(LOCUS.equals(entry.getValue()))
            {
                if (locusFound)
                    continue;
                else
                    locusFound = true;
            }

            if(ALLELE1.equals(entry.getValue()))
            {
                if (allele1Found)
                    continue;
                else
                    allele1Found = true;
            }

            if(ALLELE2.equals(entry.getValue()))
            {
                if (allele2Found)
                    continue;
                else
                    allele2Found = true;
            }

            if("Note".equals(entry.getValue()))
            {
                if (noteFound)
                    continue;
                else
                    noteFound = true;
            }

            sb.append(entry.getValue().replace(' ', '_'));
            sb.append('\t');
        }
        sb.append('\n');

        return sb.toString();
    }

    // Check if column correlates to the type column in the input
    private boolean isType(int col)
    {
        Collection<String> colValues = _columnMap.get(col);
        return colValues.contains(LOCUS) || colValues.contains(ALLELE1) || colValues.contains(ALLELE2);
    }

    // Can add more checking here
    private boolean isValid(String common)
    {
        return !common.isEmpty() && common.trim().length() > 0;
    }

    private String getLocus(String type)
    {
        if(type.contains(":"))
            return type.substring(0,type.indexOf(':')) + '\t';
        else
            return type + '\t';
    }

    private String getAllele(String type, int allele)
    {
        String value = "";

        if(type.contains(":") && (allele == 1 || allele == 2) )
        {
            String alleles = type.substring(type.indexOf(':') + 1, type.length());
            if(allele == 1)
            {
                if (alleles.contains("/"))
                    value = alleles.substring(0,alleles.indexOf('/'));
                else
                    value = alleles;
            }
            else
            {
                if (alleles.contains("/"))
                    value = alleles.substring(alleles.indexOf('/') + 1, alleles.length());
            }
        }

        value += '\t';
        return value;
    }

    private String processRow(Row row)
    {
        StringBuilder rows = new StringBuilder();
        StringBuilder common = new StringBuilder();
        StringBuilder notes = new StringBuilder();
        int note = 0, col;
        Cell cell;

        // Concat notes into one column
        for(Map.Entry<Integer, String> entry : _columnMap.entries())
        {
            col = entry.getKey();
            cell = row.getCell(col);

            if(null != cell)
            {
                if ("Note".equals(entry.getValue()))
                {
                    String strNote = getValue(cell);

                    // Check if note is empty or all whitespace
                    if(!strNote.isEmpty() && strNote.trim().length() > 0)
                    {
                        notes.append(strNote);
                        notes.append("\\n");
                    }
                }
            }
        }

        // Get non-type and non-note columns
        for(Map.Entry<Integer, String> entry : _columnMap.entries())
        {
            col = entry.getKey();
            cell = row.getCell(col);

            if(null != cell)
            {
                if ("Note".equals(entry.getValue()) && notes.length() > 0)
                {
                    common.append(notes.toString());
                    common.append('\t');
                }
                else if (!isType(col))
                {
                    common.append(getValue(cell));
                    common.append('\t');
                }
            }
        }

        // Get Types
        if(isValid(common.toString()))
        {
            for(Map.Entry<Integer, String> entry : _columnMap.entries())
            {
                col = entry.getKey();
                if (isType(col))
                {
                    String value = getValue(row.getCell(col));
                    if(!value.equals("-"))
                    {
                        rows.append(common);
                        rows.append(getLocus(value));
                        rows.append(getAllele(value, 1));
                        rows.append(getAllele(value, 2));
                        rows.append('\n');
                    }
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
            if (getRunProperties().containsKey(Props.runDataFile.name()))
            {
                File inputDataFile = new File(getRunProperty(Props.runDataUploadedFile));
                File transformFile = new File(getTransformFile().get(getRunProperty(Props.runDataFile)));

                try (PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(transformFile))))
                {
                    try (FileInputStream stream = new FileInputStream(inputDataFile))
                    {
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
                                first = false;
                            }
                            else
                            {
                                sb.append(processRow(nextRow));
                            }

                            writer.print(sb.toString());
                        }
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
