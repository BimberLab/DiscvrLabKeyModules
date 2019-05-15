package org.labkey.laboratory.query;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.dataiterator.DataIterator;
import org.labkey.api.dataiterator.DataIteratorBuilder;
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.dataiterator.LoggingDataIterator;
import org.labkey.api.dataiterator.SimpleTranslator;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.DuplicateKeyException;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.SimpleQueryUpdateService;
import org.labkey.api.query.SimpleUserSchema;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.util.ExceptionUtil;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * User: bimber
 * Date: 1/31/13
 * Time: 9:30 PM
 */
public class LaboratoryWorkbooksTable extends SimpleUserSchema.SimpleTable
{
    public static final String WORKBOOK_COL = "container";
    public static final String PARENT_COL = "parentContainer";
    public static final String WORKBOOK_ID_COl = "workbookId";
    public static final String CONTAINER_ROWID_COL = "containerRowId";

    private static final Logger _log = Logger.getLogger(LaboratoryWorkbooksTable.class);

    public LaboratoryWorkbooksTable(UserSchema us, TableInfo st, ContainerFilter cf)
    {
        super(us, st, cf);
    }

    public SimpleUserSchema.SimpleTable init()
    {
        super.init();

        setTitleColumn(WORKBOOK_ID_COl);
        setDetailsURL(DetailsURL.fromString("/project/start.view"));

        var col = getMutableColumn(WORKBOOK_ID_COl);
        col.setURL(DetailsURL.fromString("/project/start.view"));

        String chr = getSqlDialect().isPostgreSQL() ? "chr" : "char";
        ExprColumn tagsCol = new ExprColumn(this, "tags", new SQLFragment("(SELECT ").append(getSqlDialect().getGroupConcat(new SQLFragment("tag"), true, true, getSqlDialect().concatenate("','", chr + "(10)"))).append(" FROM laboratory.workbook_tags wt WHERE wt.container = " + ExprColumn.STR_TABLE_ALIAS + ".container)"), JdbcType.VARCHAR, getColumn("container"));
        tagsCol.setLabel("Tags");
        tagsCol.setDisplayWidth("200");
        addColumn(tagsCol);

        ExprColumn nameCol = new ExprColumn(this, "rowIdAndName", new SQLFragment("(" + getSqlDialect().concatenate("CAST(" + ExprColumn.STR_TABLE_ALIAS + ".workbookId AS VARCHAR)", "': '", "(SELECT title FROM core.containers c WHERE c.entityid = " + ExprColumn.STR_TABLE_ALIAS + ".container)") + ")"), JdbcType.VARCHAR, getColumn("container"));
        nameCol.setLabel("Name");
        addColumn(nameCol);

        return this;
    }

    @Override
    protected String getContainerFilterColumn()
    {
        return WORKBOOK_COL;
    }

    @Override
    public QueryUpdateService getUpdateService()
    {
        return new UpdateService(this);
    }

    private class UpdateService extends SimpleQueryUpdateService
    {
        public UpdateService(SimpleUserSchema.SimpleTable ti)
        {
            super(ti, ti.getRealTable());
        }

        @Override
        protected Map<String, Object> getRow(User user, Container container, Map<String, Object> keys) throws InvalidKeyException, QueryUpdateServiceException, SQLException
        {
            return super.getRow(user, container, keys);
        }

        //NOTE: this should no longer be called (see persistRows() for DataIterator implementation); however, I have left this code in place for now
        @Override
        protected Map<String, Object> insertRow(User user, Container container, Map<String, Object> row) throws DuplicateKeyException, ValidationException, QueryUpdateServiceException, SQLException
        {
            if (container.isWorkbook())
            {
                Integer rowId;
                try
                {
                    rowId = Integer.parseInt(container.getName());
                }
                catch (NumberFormatException e)
                {
                    ExceptionUtil.logExceptionToMothership(null, new Exception("LaboratoryWorkbookTable is being passed a workbook with a non-numeric name: " + container.getName()));

                    rowId = -1;  //should never happen, unsure what to do
                }

                row.put(WORKBOOK_ID_COl, rowId);
                if (!container.isWorkbook())
                    throw new ValidationException("Workbook rows can only be created for workbooks.  Container is not a workbook: " + container.getPath());

                //this is cached here since we retain these rows even if the workbook is deleted.  this allows us to clean up these rows
                //when the parent is deleted
                row.put(LaboratoryWorkbooksTable.PARENT_COL, container.getParent().getId());
                row.put(LaboratoryWorkbooksTable.WORKBOOK_COL, container.getId());
                row.put(LaboratoryWorkbooksTable.CONTAINER_ROWID_COL, container.getRowId());
            }

            return super.insertRow(user, container, row);
        }

        @Override
        protected Map<String, Object> updateRow(User user, Container container, Map<String, Object> row, @NotNull Map<String, Object> oldRow) throws InvalidKeyException, ValidationException, QueryUpdateServiceException, SQLException
        {
            Object oldValue = oldRow.get(WORKBOOK_ID_COl);
            Object newValue = row.get(WORKBOOK_ID_COl);

            if (oldValue != null && newValue != null && !oldValue.equals(newValue))
                throw new ValidationException("Cannot change the workbook Id");

            return super.updateRow(user, container, row, oldRow);
        }

        /**
         * Note: removed since I think this can cause a recursion
         * if you delete from the QWP, since we delete this row, and the
         * container delete listeners will attempt to re-delete this row
         * Instead redirect this table's delete to core.workbooks
        @Override
        protected Map<String, Object> deleteRow(User user, Container container, Map<String, Object> oldRow) throws InvalidKeyException, QueryUpdateServiceException, SQLException
        {
            String containerId = oldRow.get(WORKBOOK_COL) == null ? null : oldRow.get(WORKBOOK_COL).toString();
            Container workbook = null;
            if (containerId != null)
                workbook = ContainerManager.getForId(containerId);

            if (null == workbook || !workbook.isWorkbook())
                throw new NotFoundException("Could not find a workbook with id '" + containerId + "'");

            //this might create a circular delete problem
            ContainerManager.delete(workbook, user);

            return oldRow;
        }
        */
    }

    @Override
    public DataIteratorBuilder persistRows(DataIteratorBuilder data, DataIteratorContext context)
    {
        data = new WorkbookDataIteratorBuilder(data, context);
        return super.persistRows(data, context);
    }

    private class WorkbookDataIteratorBuilder implements DataIteratorBuilder
    {
        DataIteratorContext _context;
        final DataIteratorBuilder _in;

        WorkbookDataIteratorBuilder(@NotNull DataIteratorBuilder in, DataIteratorContext context)
        {
            _context = context;
            _in = in;
        }

        @Override
        public DataIterator getDataIterator(DataIteratorContext context)
        {
            _context = context;
            DataIterator input = _in.getDataIterator(context);
            if (null == input)
                return null;           // Can happen if context has errors

            final SimpleTranslator it = new SimpleTranslator(input, context);

            final Map<String, Integer> inputColMap = new HashMap<String, Integer>();
            final Map<String, Integer> outputColMap = new HashMap<String, Integer>();

            for (int idx = 1; idx <= input.getColumnCount(); idx++)
            {
                ColumnInfo col = input.getColumnInfo(idx);
                if (StringUtils.equalsIgnoreCase(WORKBOOK_ID_COl, col.getName()))
                {
                    inputColMap.put(WORKBOOK_ID_COl, idx);
                    continue;
                }

                if (StringUtils.equalsIgnoreCase(LaboratoryWorkbooksTable.PARENT_COL, col.getName()))
                {
                    inputColMap.put(PARENT_COL, idx);
                    continue;
                }

                if (StringUtils.equalsIgnoreCase(LaboratoryWorkbooksTable.CONTAINER_ROWID_COL, col.getName()))
                {
                    inputColMap.put(CONTAINER_ROWID_COL, idx);
                    continue;
                }

                if (StringUtils.equalsIgnoreCase(LaboratoryWorkbooksTable.WORKBOOK_COL, col.getName()))
                {
                    outputColMap.put(WORKBOOK_COL, inputColMap.put(WORKBOOK_COL, idx));
                }

                it.addColumn(idx);
            }

            //append parent container col
            ColumnInfo parentCol = getColumn(PARENT_COL);
            it.addColumn(parentCol, new Callable()
            {
                @Override
                public Object call() throws Exception
                {
                    Container c = null;
                    if (inputColMap.containsKey(WORKBOOK_COL))
                    {
                        String containerId = (String)it.get(inputColMap.get(WORKBOOK_COL));
                        if (containerId != null)
                        {
                            c = ContainerManager.getForId(containerId);
                        }
                    }

                    if (c == null)
                        c = getContainer();

                    return c.getParent().getId();
                }
            });

            //append container rowId
            ColumnInfo containerRowIdCol = getColumn(CONTAINER_ROWID_COL);
            it.addColumn(containerRowIdCol, new Callable()
            {
                @Override
                public Object call() throws Exception
                {
                    Container c = null;
                    if (inputColMap.containsKey(WORKBOOK_COL))
                    {
                        String containerId = (String)it.get(inputColMap.get(WORKBOOK_COL));
                        if (containerId != null)
                        {
                            c = ContainerManager.getForId(containerId);
                        }
                    }

                    if (c == null)
                        c = getContainer();

                    return c.getRowId();
                }
            });

            //append workbookId col.  this codepath is insert-only, and so we always want to auto-assign an incrementing value
            final WorkbookIdGenerator idGen = new WorkbookIdGenerator();
            ColumnInfo keyCol = getColumn(WORKBOOK_ID_COl);
            it.addColumn(keyCol, new Callable()
            {
                @Override
                public Object call() throws Exception
                {
                    Object workbookId = null;

                    //if the input data lacks a container, we dont know what to set for workbookId
                    if (inputColMap.containsKey(WORKBOOK_COL))
                    {
                        String containerId = (String)it.get(inputColMap.get(WORKBOOK_COL));
                        if (containerId != null)
                        {
                            workbookId = idGen.getNextId(containerId);
                        }
                    }

                    return workbookId;
                }
            });

            return LoggingDataIterator.wrap(it);
        }
    }

    /**
     * Workbook IDs are assigned using a single sequence per parent container.
     * The IDs are determined in LaboratoryManager, but this code will increment them within the rows
     * of a set of incoming records.
     */
    private class WorkbookIdGenerator
    {
        private Map<String, Integer> _idMap = new HashMap<String, Integer>();

        public WorkbookIdGenerator()
        {

        }

        public Integer getNextId(String containerId)
        {
            Container c = ContainerManager.getForId(containerId);
            if (c == null)
                throw new IllegalArgumentException("Unknown container: " + containerId);

            if (!c.isWorkbook())
            {
                return null;
            }

            try
            {
                return Integer.parseInt(c.getName());
            }
            catch (NumberFormatException e)
            {
                ExceptionUtil.logExceptionToMothership(null, new Exception("LaboratoryWorkbookTable is being passed a workbook with a non-numeric name: " + c.getName()));
                return null;
            }
        }
    }
}
