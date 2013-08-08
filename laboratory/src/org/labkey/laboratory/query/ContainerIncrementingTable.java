package org.labkey.laboratory.query;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Aggregate;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.SchemaTableInfo;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.etl.DataIterator;
import org.labkey.api.etl.DataIteratorBuilder;
import org.labkey.api.etl.DataIteratorContext;
import org.labkey.api.etl.LoggingDataIterator;
import org.labkey.api.etl.SimpleTranslator;
import org.labkey.api.query.DuplicateKeyException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.SimpleQueryUpdateService;
import org.labkey.api.query.SimpleUserSchema;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.view.NotFoundException;
import org.labkey.laboratory.LaboratoryManager;
import org.labkey.laboratory.LaboratorySchema;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * User: bimber
 * Date: 3/12/13
 * Time: 12:59 PM
 */
public class ContainerIncrementingTable extends SimpleUserSchema.SimpleTable
{
    private String _incrementingCol;
    private static final String SELF_ASSIGNED_COL = "_selfAssignedId_";
    private static final String PROPERT_CATEGORY_BASE = "laboratory.tableRowId";

    public ContainerIncrementingTable(UserSchema us, TableInfo st, String incrementingCol)
    {
        super(us, st);
        _incrementingCol = incrementingCol;
    }

    public SimpleUserSchema.SimpleTable init()
    {
        super.init();

        ColumnInfo col = getColumn(_incrementingCol);
        if (col == null)
            throw new IllegalArgumentException("Unable to find column: " + _incrementingCol);

        col.setUserEditable(false);
        col.setShownInInsertView(false);
        return this;
    }

    public String getIncrementingCol()
    {
        return _incrementingCol;
    }

    @Override
    public QueryUpdateService getUpdateService()
    {
        return new UpdateSerivce(this);
    }

    private class UpdateSerivce extends SimpleQueryUpdateService
    {
        public UpdateSerivce(SimpleUserSchema.SimpleTable ti)
        {
            super(ti, ti.getRealTable());
        }

        //NOTE: this code should never be encountered now that hard tables use ETL; however, I have kept it in place for the time being.
        @Override
        protected Map<String, Object> insertRow(User user, Container container, Map<String, Object> row) throws DuplicateKeyException, ValidationException, QueryUpdateServiceException, SQLException
        {
            Integer rowId = null;
            boolean hasSelfAssignedId = false;
            // This idea here is that the majority of the time the ID will auto-increment within this container (defined as parent + workbooks)
            // however, there is a backdoor that lets the IDs get set manually.  An example of this would be when a row was deleted accidentally or otherwise, and you want
            // to re-upload with the same key.  we still enforce uniqueness within this container
            if (row.get(_incrementingCol) != null)
            {
                rowId = (Integer)row.get(_incrementingCol);

                hasSelfAssignedId = true;
                if (hasRowWithId(container, rowId))
                    throw new ValidationException("A record is already present with ID: " + rowId);
            }
            else
            {
                rowId = getCurrentId(container);
                assert rowId != null;
                rowId++;

                row.put(_incrementingCol, rowId);
            }

            Map<String, Object> ret = super.insertRow(user, container, row);

            //if the incoming row has a self-assigned ID, and that ID is higher than the table's existing value, we update the table
            if (hasSelfAssignedId)
            {
                Integer currentValue = getCurrentId(container);
                Integer rowValue = (Integer)row.get(_incrementingCol);
                if (currentValue == null || currentValue < rowValue)
                    saveId(container, rowValue);
            }
            else if (rowId != null)
            {
                saveId(container, rowId);
            }

            return  ret;
        }

        @Override
        protected Map<String, Object> updateRow(User user, Container container, Map<String, Object> row, Map<String, Object> oldRow) throws InvalidKeyException, ValidationException, QueryUpdateServiceException, SQLException
        {
            Integer oldValue = getInteger(oldRow.get(_incrementingCol));
            Integer newValue = getInteger(row.get(_incrementingCol));

            if (oldValue != null && newValue != null && !oldValue.equals(newValue))
                throw new ValidationException("Cannot change the value of the column: " + _incrementingCol);

            return super.updateRow(user, container, row, oldRow);
        }
    }

    private Integer getInteger(Object value) throws ValidationException
    {
        if (value == null)
            return null;
        else if (value instanceof Integer)
            return (Integer)value;
        else if (value instanceof Double)
            return ((Double)value).intValue();
        try
        {
            return Integer.getInteger(value.toString());
        }
        catch (NumberFormatException e)
        {
            throw new ValidationException("Value for column " + _incrementingCol + " was not an integer: " + value);
        }
    }

    @NotNull
    public Integer getCurrentId(Container c)
    {
        //lookup from propertyManager
        Container target = c.isWorkbook() ? c.getParent() : c;
        Map<String, String> props = PropertyManager.getProperties(target, PROPERT_CATEGORY_BASE);
        if (props.containsKey(getPropertyKey()))
        {
            return Integer.parseInt(props.get(getPropertyKey()));
        }
        else
        {
            Integer id = findHighestId(target);
            return id == null ? 0 : id;
        }
    }

    public void saveId(Container c, Integer value)
    {
        Container target = c.isWorkbook() ? c.getParent() : c;
        PropertyManager.PropertyMap map = PropertyManager.getWritableProperties(target, PROPERT_CATEGORY_BASE, true);
        map.put(getPropertyKey(), value.toString());
        PropertyManager.saveProperties(map);
    }

    private String getPropertyKey()
    {
        return _schema.getName() + "||" + _rootTable.getSelectName();
    }

    private Integer findHighestId(Container c)
    {
        Container target = c.isWorkbook() ? c.getParent() : c;
        TableInfo ti = QueryService.get().getUserSchema(getUserSchema().getUser(), target, _rootTable.getSchema().getName()).getTable(_rootTable.getName());
        assert ti != null;

        TableSelector ts = new TableSelector(ti, Collections.singleton(_incrementingCol), null, null);
        Map<String, List<Aggregate.Result>> aggs = ts.getAggregates(Collections.singletonList(new Aggregate(FieldKey.fromString(_incrementingCol), Aggregate.Type.MAX)));
        for (List<Aggregate.Result> ag : aggs.values())
        {
            for (Aggregate.Result r : ag)
            {
                //did the return type of aggregates change between 12.3 -> 13.1??
                Integer rowId;
                if (r.getValue() instanceof Long)
                {
                    Long val = (Long)r.getValue();
                    rowId = val == null ? null : val.intValue();
                }
                else
                {
                    rowId = (Integer)r.getValue();
                }

                return rowId;
            }
        }

        return 0;
    }

    private boolean hasRowWithId(Container c, Integer rowId)
    {
        Container target = c.isWorkbook() ? c.getParent() : c;
        TableInfo ti = QueryService.get().getUserSchema(getUserSchema().getUser(), target, _rootTable.getSchema().getName()).getTable(_rootTable.getName());
        assert ti != null;

        TableSelector ts = new TableSelector(ti, Collections.singleton(_incrementingCol), new SimpleFilter(FieldKey.fromString(_incrementingCol), rowId, CompareType.EQUAL), null);
        return ts.getRowCount() != 0;
    }

    @Override
    public DataIteratorBuilder persistRows(DataIteratorBuilder data, DataIteratorContext context)
    {
        data = new IteratingDataIteratorBuilder(data, context);
        return super.persistRows(data, context);
    }

    /**
     * Incoming rows are assigned incrementing rowIds, with a unique sequence per parent container
     * However, in some cases we do let rows self-assign their IDs, provided those IDs are unique within the series
     */
    private class IteratingDataIteratorBuilder implements DataIteratorBuilder
    {
        DataIteratorContext _context;
        final DataIteratorBuilder _in;

        IteratingDataIteratorBuilder(@NotNull DataIteratorBuilder in, DataIteratorContext context)
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

            final String containerColName = getContainerFilterColumn();
            final IncrementIdGenerator idGen = new IncrementIdGenerator();

            final SimpleTranslator it = new SimpleTranslator(input, context)
            {
                @Override
                public void close() throws IOException
                {
                    super.close();
                    if (!_context.getErrors().hasErrors())
                        idGen.commit();
                }
            };

            final Map<String, Integer> inputColMap = new HashMap<String, Integer>();
            for (int idx = 1; idx <= input.getColumnCount(); idx++)
            {
                ColumnInfo col = input.getColumnInfo(idx);
                if (StringUtils.equalsIgnoreCase(_incrementingCol, col.getName()))
                {
                    inputColMap.put(_incrementingCol, idx);
                    continue;
                }

                if (StringUtils.equalsIgnoreCase(getContainerFilterColumn(), col.getName()))
                {
                    inputColMap.put(getContainerFilterColumn(), idx);
                }

                it.addColumn(idx);
            }

            //set the value of the RowId column
            ColumnInfo incrementCol = getColumn(_incrementingCol);
            it.addColumn(incrementCol, new Callable()
            {
                @Override
                public Object call() throws Exception
                {
                    Container c = null;
                    if (inputColMap.containsKey(containerColName))
                    {
                        String containerId = (String)it.getInputColumnValue(inputColMap.get(containerColName));
                        if (containerId != null)
                            c = ContainerManager.getForId(containerId);
                    }

                    if (c == null)
                    {
                        c = getContainer();
                    }

                    assert c != null;

                    //allow self-assigned IDs.
                    Integer rowId = null;
                    if (inputColMap.containsKey(_incrementingCol))
                    {
                        Object selfAssignedId = it.getInputColumnValue(inputColMap.get(_incrementingCol));
                        if (selfAssignedId != null)
                        {
                            if (selfAssignedId instanceof Integer)
                            {
                                rowId = (Integer)selfAssignedId;

                                if (idGen.hasRowWithId(c, rowId))
                                    throw new ValidationException("A record is already present with ID: " + rowId);

                                //NOTE: if the self-assigned ID is higher than the existing value we have for this table, we increment to that value
                                idGen.incrementId(c, rowId);
                            }
                        }
                    }

                    if (rowId == null)
                        rowId = idGen.getNextId(c);

                    return rowId;
                }
            });

            return LoggingDataIterator.wrap(it);
        }
    }

    /**
     * The rowIDs should increment using a single sequence per parent container, defined as parent + child workbooks
     * Currently this sequence is stored in property manager; however, we should migrate this to DbSequenceManager once implemented
     */
    private class IncrementIdGenerator
    {
        private Map<Container, Integer> _idMap = new HashMap<Container, Integer>();
        private Map<Container, Set<Integer>> _existingIdsMap = new HashMap<Container, Set<Integer>>();

        public IncrementIdGenerator()
        {

        }

        public int getNextId(Container c)
        {
            Container target = c.isWorkbook() ? c.getParent() : c;
            Integer key = getCurrentRowId(target);

            key++;
            _idMap.put(target, key);

            return key;
        }

        public Integer getCurrentRowId(Container c)
        {
            if (c == null)
                throw new IllegalArgumentException("Container cannot be null");

            Container target = c.isWorkbook() ? c.getParent() : c;

            Integer key;
            if (_idMap.containsKey(target))
            {
                key = _idMap.get(target);
            }
            else
            {
                key = getCurrentId(c);
            }

            return key;
        }

        public void commit()
        {
            for (Container c : _idMap.keySet())
            {
                saveId(c, _idMap.get(c));
            }
        }

        public boolean hasRowWithId(Container c, Integer rowId)
        {
            Container target = c.isWorkbook() ? c.getParent() : c;

            Set<Integer> set = _existingIdsMap.get(target);
            if (set == null)
                set = new HashSet<Integer>();

            //if we have already tested this ID, it is assumed to exist
            if (set.contains(rowId))
                return true;

            //if we have not encountered this ID, query the table to determine if there is an existing row
            TableInfo ti = QueryService.get().getUserSchema(getUserSchema().getUser(), target, _rootTable.getSchema().getName()).getTable(_rootTable.getName());
            assert ti != null;

            TableSelector ts = new TableSelector(ti, Collections.singleton(_incrementingCol), new SimpleFilter(FieldKey.fromString(_incrementingCol), rowId, CompareType.EQUAL), null);

            //place the rowId in the set, in case we encounter it again
            set.add(rowId);
            _existingIdsMap.put(target, set);

            return ts.getRowCount() != 0;
        }

        public void incrementId(Container c, Integer rowId)
        {
            Container target = c.isWorkbook() ? c.getParent() : c;
            Integer existingId = getCurrentRowId(target);
            if (rowId > existingId)
                _idMap.put(target, rowId);
        }
    }
}
