package org.labkey.discvrcore;

import org.jetbrains.annotations.Nullable;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryForeignKey;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.SimpleUserSchema;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.MutableSecurityPolicy;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.SecurityPolicyManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.security.roles.ReaderRole;
import org.labkey.api.study.Dataset;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.TimepointType;
import org.labkey.api.util.GUID;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.TestContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AuditSummaryUserSchema extends SimpleUserSchema
{
    public static final String NAME = "AuditSummary";
    public static final String QUERY_AUDIT = "QueryUpdateAuditLog";
    public static final String DATASET_AUDIT = "DatasetUpdateAuditLog";

    private AuditSummaryUserSchema(User user, Container container, DbSchema schema)
    {
        super(DiscvrCoreModule.NAME, null, user, container, schema);
    }

    public static void register(final Module m)
    {
        final DbSchema dbSchema = DbSchema.get(DiscvrCoreModule.NAME, DbSchemaType.Module);

        DefaultSchema.registerProvider(DiscvrCoreModule.NAME, new DefaultSchema.SchemaProvider(m)
        {
            @Override
            public QuerySchema createSchema(final DefaultSchema schema, Module module)
            {
                return new AuditSummaryUserSchema(schema.getUser(), schema.getContainer(), dbSchema);
            }
        });
    }

    @Override
    public @Nullable String getDescription()
    {
        return "Contains a summary view of audit records, without record detail, primarily for use in ETLs ";
    }

    @Override
    public TableInfo createTable(String name, ContainerFilter cf)
    {
        if (name.equalsIgnoreCase(QUERY_AUDIT))
        {
            return generateQueryAuditSql(name, cf);
        }
        else if (name.equalsIgnoreCase(DATASET_AUDIT))
        {
            return generateDatasetAuditSql(name, cf);
        }

        return super.createTable(name, cf);
    }

    @Override
    public Set<String> getTableNames()
    {
        Set<String> tables = new HashSet<>(super.getTableNames());
        tables.add(QUERY_AUDIT);
        tables.add(DATASET_AUDIT);

        return Collections.unmodifiableSet(tables);
    }

    @Override
    public synchronized Set<String> getVisibleTableNames()
    {
        return getTableNames();
    }

    private SimpleTable<?> generateAuditSql(String tableName, String eventType, ContainerFilter cf)
    {
        Container target = getContainer().isWorkbookOrTab() ? getContainer().getParent() : getContainer();
        String storageTableName = AuditLogService.get().getAuditProvider(eventType).getDomain().getStorageTableName();
        DbSchema auditSchema = DbSchema.get("audit", DbSchemaType.Module);
        TableInfo ti = auditSchema.getTable(storageTableName);

        SimpleTable<?> st = new SimpleTable<>(this, ti, ContainerFilter.current(target));
        st.setName(tableName);
        st.init();

        // Don't expose more information than needed:
        st.removeColumn(st.getColumn("CreatedBy"));
        st.removeColumn(st.getColumn("ImpersonatedBy"));
        st.removeColumn(st.getColumn("oldRecordMap"));
        st.removeColumn(st.getColumn("newRecordMap"));

        ColumnInfo lsid = st.getColumn("lsid");
        if (lsid != null)
        {
            if (st.getSqlDialect().isPostgreSQL())
            {
                st.addColumn(new ExprColumn(st, FieldKey.fromString("primaryKey"), new SQLFragment("right(" + ExprColumn.STR_TABLE_ALIAS + ".lsid, position('.', reverse(" + ExprColumn.STR_TABLE_ALIAS + ".lsid))-1)"), JdbcType.VARCHAR, lsid));
            }
            else if (ti.getSqlDialect().isSqlServer())
            {
                st.addColumn(new ExprColumn(st, FieldKey.fromString("primaryKey"), new SQLFragment("right(" + ExprColumn.STR_TABLE_ALIAS + ".lsid, charindex('.', reverse(" + ExprColumn.STR_TABLE_ALIAS + ".lsid))-1)"), JdbcType.VARCHAR, lsid));
            }
        }

        return st;
    }

    private TableInfo generateQueryAuditSql(String tableName, ContainerFilter cf)
    {
        return generateAuditSql(tableName, "QueryUpdateAuditEvent", cf);
    }

    private TableInfo generateDatasetAuditSql(String tableName, ContainerFilter cf)
    {
        SimpleTable<?> ti = generateAuditSql(tableName, "DatasetAuditEvent", cf);
        Container target = getContainer().isWorkbookOrTab() ? getContainer().getParent() : getContainer();

        UserSchema us = QueryService.get().getUserSchema(getUser(), target, "study");
        if (us != null)
        {
            ti.getMutableColumn("DatasetId").setFk(new QueryForeignKey(us.getTable("Datasets"), "DataSetId", "Name"));
        }

        return ti;
    }

    public static class TestCase extends Assert
    {
        public static final String PROJECT_NAME = "QueryExtensionsTestProject";

        @BeforeClass
        public static void setup() throws Exception
        {
            doCleanup();

            Container c = getContainer(true);
            User u = getReaderUser(true);

            MutableSecurityPolicy p = new MutableSecurityPolicy(c.getPolicy());
            p.addRoleAssignment(u, ReaderRole.class);
            SecurityPolicyManager.savePolicy(p, TestContext.get().getUser());
        }

        private static Container getContainer(boolean createIfNeeded)
        {
            if (createIfNeeded && ContainerManager.getForPath(PROJECT_NAME) == null)
            {
                Container c = ContainerManager.createContainer(ContainerManager.getRoot(), PROJECT_NAME);
                c.setActiveModules(PageFlowUtil.set(ModuleLoader.getInstance().getModule("study"), ModuleLoader.getInstance().getModule(DiscvrCoreModule.NAME)));
            }

            return ContainerManager.getForPath(PROJECT_NAME);
        }

        private Dataset createDataset(String name, boolean isDemographics) throws Exception
        {
            Dataset d1 = StudyService.get().createDataset(getContainer(true), TestContext.get().getUser(), name, null, isDemographics);
            d1.setKeyManagementType(Dataset.KeyManagementType.GUID);
            d1.setKeyPropertyName("objectId");
            DomainProperty objectId1 = d1.getDomain().addProperty();
            objectId1.setName("objectId");
            objectId1.setPropertyURI(AuditSummaryUserSchema.class.getName() + ":ObjectId");
            d1.getDomain().save(TestContext.get().getUser());
            d1.save(TestContext.get().getUser());

            return d1;
        }

        @Test
        public void testAuditTables() throws Exception
        {
            Container c = getContainer(true);

            // Add a study:
            Study s = StudyService.get().createStudy(c, TestContext.get().getUser(), "DemoStudy", TimepointType.CONTINUOUS, true);

            Dataset d1 = createDataset("Dataset1", false);
            Dataset d2 = createDataset("Dataset2", true);

            String guid1 = new GUID().toString();

            List<Map<String, Object>> toInsert = new ArrayList<>();
            toInsert.add(new CaseInsensitiveHashMap<>(Map.of("ParticipantId", "P1", "Date", new Date(), "ObjectId", guid1)));
            TableInfo t1 = d1.getTableInfo(TestContext.get().getUser());
            t1.getUpdateService().insertRows(TestContext.get().getUser(), c, toInsert, new BatchValidationException(), null, null);

            TableInfo t2 = d2.getTableInfo(TestContext.get().getUser());
            t2.getUpdateService().insertRows(TestContext.get().getUser(), c, toInsert, new BatchValidationException(), null, null);

            TableInfo qa = QueryService.get().getUserSchema(TestContext.get().getUser(), c, NAME).getTable(DATASET_AUDIT);

            TableSelector ts1 = new TableSelector(qa, PageFlowUtil.set("primaryKey"));
            assertEquals("Incorrect row count", ts1.getRowCount(), 2L);

            assertEquals("Incorrect PK", new TableSelector(qa, PageFlowUtil.set("primaryKey"), new SimpleFilter(FieldKey.fromString("Comment"), "inserted", CompareType.CONTAINS).addCondition(FieldKey.fromString("DatasetId"), d1.getDatasetId()), null).getObject(String.class), guid1);
            assertEquals("Incorrect PK", new TableSelector(qa, PageFlowUtil.set("primaryKey"), new SimpleFilter(FieldKey.fromString("Comment"), "inserted", CompareType.CONTAINS).addCondition(FieldKey.fromString("DatasetId"), d2.getDatasetId()), null).getObject(String.class), "P1");

            assertEquals("Incorrect PK", new TableSelector(qa, PageFlowUtil.set("primaryKey"), new SimpleFilter(FieldKey.fromString("Comment"), "inserted", CompareType.CONTAINS).addCondition(FieldKey.fromString("DatasetId/Name"), d1.getName()), null).getObject(String.class), guid1);
            assertEquals("Incorrect PK", new TableSelector(qa, PageFlowUtil.set("primaryKey"), new SimpleFilter(FieldKey.fromString("Comment"), "inserted", CompareType.CONTAINS).addCondition(FieldKey.fromString("DatasetId/Name"), d2.getName()), null).getObject(String.class), "P1");

            t1.getUpdateService().deleteRows(TestContext.get().getUser(), c, Collections.singletonList(new CaseInsensitiveHashMap<>(Map.of("ObjectId", guid1))), null, null);
            assertEquals("Incorrect PK", new TableSelector(qa, PageFlowUtil.set("primaryKey"), new SimpleFilter(FieldKey.fromString("Comment"), "deleted", CompareType.CONTAINS), null).getObject(String.class), guid1);

            // Ensure this works as reader:
            User reader = getReaderUser(true);
            qa = QueryService.get().getUserSchema(reader, c, NAME).getTable(DATASET_AUDIT);
            assertEquals("Incorrect PK", new TableSelector(qa, PageFlowUtil.set("primaryKey"), new SimpleFilter(FieldKey.fromString("Comment"), "inserted", CompareType.CONTAINS).addCondition(FieldKey.fromString("DatasetId/Name"), d1.getName()), null).getObject(String.class), guid1);
            assertEquals("Incorrect PK", new TableSelector(qa, PageFlowUtil.set("primaryKey"), new SimpleFilter(FieldKey.fromString("Comment"), "inserted", CompareType.CONTAINS).addCondition(FieldKey.fromString("DatasetId/Name"), d2.getName()), null).getObject(String.class), "P1");
        }

        @AfterClass
        public static void doCleanup() throws Exception
        {
            if (getContainer(false) != null)
            {
                ContainerManager.delete(getContainer(false), TestContext.get().getUser());
            }

            if (getReaderUser(false) != null)
            {
                UserManager.deleteUser(getReaderUser(false).getUserId());
            }
        }

        private static final String READER_USER = "readerUserForTesting@myDomain.com";

        private static User getReaderUser(boolean createIfNeeded) throws Exception
        {
            if (createIfNeeded && !UserManager.userExists(new ValidEmail(READER_USER)))
            {
                SecurityManager.NewUserStatus nus = SecurityManager.addUser(new ValidEmail(READER_USER), TestContext.get().getUser());
                return nus.getUser();
            }

            return UserManager.getUser(new ValidEmail(READER_USER));
        }
    }
}
