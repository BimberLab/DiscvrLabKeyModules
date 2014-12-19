/*
 * Copyright (c) 2013-2014 LabKey Corporation
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
package org.labkey.biotrust;

import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableResultSet;
import org.labkey.api.data.TableSelector;
import org.labkey.api.module.FolderType;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.survey.SurveyService;
import org.labkey.api.survey.model.Survey;
import org.labkey.api.survey.model.SurveyDesign;
import org.labkey.api.util.TestContext;
import org.labkey.biotrust.model.SamplePickup;
import org.labkey.biotrust.model.SampleRequest;
import org.labkey.biotrust.model.SpecimenRequestAttachment;
import org.labkey.biotrust.model.TissueRecord;
import org.labkey.biotrust.query.BioTrustQuerySchema;
import org.labkey.biotrust.query.RequestStatusTable;
import org.labkey.biotrust.query.samples.SampleRequestDomain;
import org.labkey.biotrust.query.samples.TissueRecordDomain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * User: klum
 * Date: 2/18/13
 */
public class BioTrustSampleManager
{
    private static final BioTrustSampleManager _instance = new BioTrustSampleManager();

    private BioTrustSampleManager()
    {
    }

    public static BioTrustSampleManager get()
    {
        return _instance;
    }

    /**
     * Get a sample request by rowId
     * @param c
     * @param user
     * @param rowId
     */
    public SampleRequest getSampleRequest(Container c, User user, int rowId)
    {
        UserSchema schema = QueryService.get().getUserSchema(user, c, BioTrustQuerySchema.NAME);
        TableInfo sampleTable = schema.getTable(BioTrustQuerySchema.SPECIMEN_REQUEST_TABLE_NAME);
        TableInfo tissueTable = schema.getTable(BioTrustQuerySchema.TISSUE_RECORD_TABLE_NAME);

        SimpleFilter sampleRequestFilter = new SimpleFilter(FieldKey.fromParts("rowId"), rowId);
        SampleRequest sampleRequest = new TableSelector(sampleTable, sampleRequestFilter, null).getObject(SampleRequest.class);

        // get the list of tissue records for this sample request
        if (sampleRequest != null)
        {
            SimpleFilter tissueRecordsFilter = new SimpleFilter(FieldKey.fromParts("sampleId"), sampleRequest.getRowId());
            sampleRequest.setTissueRecords(new TableSelector(tissueTable, tissueRecordsFilter, null).getArrayList(TissueRecord.class));
        }

        return sampleRequest;
    }

    /**
     * Get sample requests for a given study
     * @param c
     * @param user
     * @param studyId
     */
    public List<SampleRequest> getSampleRequests(Container c, User user, int studyId)
    {
        UserSchema schema = QueryService.get().getUserSchema(user, c, BioTrustQuerySchema.NAME);
        TableInfo sampleTable = schema.getTable(BioTrustQuerySchema.SPECIMEN_REQUEST_TABLE_NAME);

        SimpleFilter sampleRequestFilter = new SimpleFilter(FieldKey.fromParts("studyId"), studyId);
        return new TableSelector(sampleTable, sampleRequestFilter, null).getArrayList(SampleRequest.class);
    }

    /**
     * Deletes a sample request and any associated tissue records.
     * @param c
     * @param user
     * @param sampleId
     */
    public void deleteSampleRequest(Container c, User user, int sampleId)
    {
        DbScope scope = BioTrustSchema.getInstance().getSchema().getScope();

        try (DbScope.Transaction transaction = scope.ensureTransaction())
        {
            UserSchema schema = QueryService.get().getUserSchema(user, c, BioTrustQuerySchema.NAME);
            TableInfo sampleTable = schema.getTable(BioTrustQuerySchema.SPECIMEN_REQUEST_TABLE_NAME);
            TableInfo tissueTable = schema.getTable(BioTrustQuerySchema.TISSUE_RECORD_TABLE_NAME);

            if (sampleTable != null && tissueTable != null)
            {
                TableSelector selector = new TableSelector(tissueTable, Collections.singleton("RowId"), new SimpleFilter(FieldKey.fromParts("SampleId"), sampleId), null);
                Integer[] tissueIds = selector.getArray(Integer.class);
                for (Integer tissueId : tissueIds)
                {
                    deleteTissueRecord(c, user, tissueId);
                }
                List<Map<String, Object>> keys = new ArrayList<>();

                // delete the document set associated with the sample request record
                List<SpecimenRequestAttachment> parents = BioTrustManager.get().getDocumentSet(c, user, sampleId, SpecimenRequestAttachment.OwnerType.samplerequest);
                AttachmentService.get().deleteAttachments(parents.toArray(new SpecimenRequestAttachment[parents.size()]));
                for (SpecimenRequestAttachment parent : parents)
                    BioTrustManager.get().deleteDocumentProperties(parent, null);

                // clean up the specimen request documents table
                SQLFragment sql = new SQLFragment("DELETE FROM ").append(BioTrustSchema.getInstance().getTableInfoSpecimenRequestDocuments(), "");
                sql.append(" WHERE OwnerId = ? AND OwnerType = ?");
                sql.addAll(new Object[]{sampleId, SpecimenRequestAttachment.OwnerType.samplerequest});
                SqlExecutor executor = new SqlExecutor(scope);
                executor.execute(sql);

                ColumnInfo samplePk = sampleTable.getColumn(FieldKey.fromParts("RowId"));
                keys.add(Collections.<String, Object>singletonMap(samplePk.getName(), sampleId));
                QueryUpdateService qus = sampleTable.getUpdateService();
                qus.deleteRows(user, c, keys, null, null);
            }
            transaction.commit();
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    /**
     * Checks to see if there is a submitted tissue record for the given sample request
     * @param c
     * @param user
     * @param sampleRequest
     */
    public boolean isSampleRequestSubmitted(Container c, User user, SampleRequest sampleRequest)
    {
        if (sampleRequest != null)
        {
            for (TissueRecord tissueRecord : sampleRequest.getTissueRecords())
            {
                Survey tissueSurvey = SurveyService.get().getSurvey(c, user, BioTrustQuerySchema.NAME, BioTrustQuerySchema.TISSUE_RECORD_TABLE_NAME, String.valueOf(tissueRecord.getRowId()));
                if (tissueSurvey != null)
                {
                    if (tissueSurvey.getSubmitted() != null)
                        return true;
                }
            }
        }
        return false;
    }

    /**
     * Checks to see if the given sample request has a tissue record in one of the locked states (i.e. Approved, Closed, etc.)
     * @param c
     * @param user
     * @param sampleRequest
     */
    public boolean isSampleRequestLocked(Container c, User user, SampleRequest sampleRequest)
    {
        if (sampleRequest != null)
        {
            for (TissueRecord tissueRecord : sampleRequest.getTissueRecords())
            {
                Survey tissueSurvey = SurveyService.get().getSurvey(c, user, BioTrustQuerySchema.NAME, BioTrustQuerySchema.TISSUE_RECORD_TABLE_NAME, String.valueOf(tissueRecord.getRowId()));
                if (tissueSurvey != null)
                {
                    if (RequestStatusTable.getLockedStates(c, user).indexOf(tissueSurvey.getStatus()) > -1)
                        return true;
                }
            }
        }
        return false;
    }

    /**
     * Get a tissue record by rowId
     * @param c
     * @param user
     * @param rowId
     */
    public TissueRecord getTissueRecord(Container c, User user, int rowId)
    {
        UserSchema schema = QueryService.get().getUserSchema(user, c, BioTrustQuerySchema.NAME);
        TableInfo tissueTable = schema.getTable(BioTrustQuerySchema.TISSUE_RECORD_TABLE_NAME);

        SimpleFilter filter = new SimpleFilter();
        filter.addCondition(FieldKey.fromParts("rowId"), rowId);
        return new TableSelector(tissueTable, filter, null).getObject(TissueRecord.class);
    }


    /**
     * Delete a tissue record
     * @param c
     * @param user
     */
    public void deleteTissueRecord(Container c, User user, int tissueId)
    {
        DbScope scope = BioTrustSchema.getInstance().getSchema().getScope();

        try (DbScope.Transaction transaction = scope.ensureTransaction())
        {
            UserSchema schema = QueryService.get().getUserSchema(user, c, BioTrustQuerySchema.NAME);
            TableInfo tissueTable = schema.getTable(BioTrustQuerySchema.TISSUE_RECORD_TABLE_NAME);

            if (tissueTable != null)
            {
                // delete the document set associated with the tissue record
                List<SpecimenRequestAttachment> parents = BioTrustManager.get().getDocumentSet(c, user, tissueId, SpecimenRequestAttachment.OwnerType.tissue);
                AttachmentService.get().deleteAttachments(parents.toArray(new SpecimenRequestAttachment[parents.size()]));
                for (SpecimenRequestAttachment parent : parents)
                    BioTrustManager.get().deleteDocumentProperties(parent, null);

                // clean up the specimen request documents table
                SQLFragment sql = new SQLFragment("DELETE FROM ").append(BioTrustSchema.getInstance().getTableInfoSpecimenRequestDocuments(), "");
                sql.append(" WHERE OwnerId = ? AND OwnerType = ?");
                sql.addAll(new Object[]{tissueId, SpecimenRequestAttachment.OwnerType.tissue});
                SqlExecutor executor = new SqlExecutor(scope);
                executor.execute(sql);

                // clean up the sample reviewers map
                SQLFragment reviewerSql = new SQLFragment("DELETE FROM ").append(BioTrustSchema.getInstance().getTableInfoSampleReviewerMap(), "");
                reviewerSql.append(" WHERE TissueId = ? AND Container = ?");

                reviewerSql.addAll(new Object[]{tissueId, c.getId()});
                new SqlExecutor(scope).execute(reviewerSql);

                SimpleFilter tissueFilter = new SimpleFilter(FieldKey.fromParts("TissueId"), tissueId);
                tissueFilter.addCondition(FieldKey.fromParts("Container"), c.getId());

                ColumnInfo pk = tissueTable.getColumn(FieldKey.fromParts("RowId"));
                List<Map<String, Object>> keys = new ArrayList<>();
                keys.add(Collections.<String, Object>singletonMap(pk.getName(), tissueId));

                QueryUpdateService qus = tissueTable.getUpdateService();
                if (null != qus)
                    qus.deleteRows(user, c, keys, null, null);
            }
            transaction.commit();
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    private String createInClause(Integer[] ids)
    {
        StringBuilder sb = new StringBuilder();
        String delim = "";

        sb.append("(");
        for (Integer id : ids)
        {
            sb.append(delim);
            sb.append(id);
            delim = ",";
        }
        sb.append(")");

        return sb.toString();
    }

    /**
     * Delete the participant eligibility record and any entries in the junction table
     * @param c
     * @param user
     * @param eligibilityId
     */
    public void deleteParticipantEligibility(Container c, User user, int eligibilityId)
    {
        DbScope scope = BioTrustSchema.getInstance().getSchema().getScope();

        try (DbScope.Transaction transaction = scope.ensureTransaction())
        {
            UserSchema schema = QueryService.get().getUserSchema(user, c, BioTrustQuerySchema.NAME);
            TableInfo eligibilityTable = schema.getTable(BioTrustQuerySchema.PARTICIPANT_ELIGIBILITY_TABLE_NAME);

            ColumnInfo pk = eligibilityTable.getColumn(FieldKey.fromParts("RowId"));
            List<Map<String, Object>> keys = new ArrayList<>();
            keys.add(Collections.<String, Object>singletonMap(pk.getName(), eligibilityId));

            if (eligibilityTable != null)
            {
                SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("EligibilityId"), eligibilityId);
                filter.addCondition(FieldKey.fromParts("Container"), c.getId());
                Table.delete(BioTrustSchema.getInstance().getTableInfoParticipantEligibilityMap(), filter);

                QueryUpdateService qus = eligibilityTable.getUpdateService();
                qus.deleteRows(user, c, keys, null, null);
            }
            transaction.commit();
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    /**
     * Links a tissue record to an eligibility record
     * @param c
     * @param user
     * @param tissueId
     * @param eligibilityId
     */
    public void setParticipantEligibility(Container c, User user, int tissueId, int eligibilityId)
    {
        DbScope scope = BioTrustSchema.getInstance().getSchema().getScope();

        SQLFragment sql = new SQLFragment("INSERT INTO ").append(BioTrustSchema.getInstance().getTableInfoParticipantEligibilityMap(), "");
        sql.append(" (TissueId, EligibilityId, Container) VALUES (?, ?, ?)");

        sql.addAll(new Object[]{tissueId, eligibilityId, c.getId()});
        SqlExecutor executor = new SqlExecutor(scope);

        executor.execute(sql);
    }

    /**
     * Clears a mapping between a tissue record and an eligibility record
     * @param c
     * @param user
     * @param tissueId
     * @param eligibilityId
     */
    public void unsetParticipantEligibility(Container c, User user, int tissueId, int eligibilityId)
    {
        DbScope scope = BioTrustSchema.getInstance().getSchema().getScope();

        SQLFragment sql = new SQLFragment("DELETE FROM ").append(BioTrustSchema.getInstance().getTableInfoParticipantEligibilityMap(), "");
        sql.append(" WHERE TissueId = ? AND EligibilityId = ? AND Container = ?");

        sql.addAll(new Object[]{tissueId, eligibilityId, c.getId()});
        SqlExecutor executor = new SqlExecutor(scope);

        executor.execute(sql);
    }

    public SamplePickup[] getSamplePickups(Container c, User user)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("Container"), c);
        TableSelector selector = new TableSelector(BioTrustSchema.getInstance().getTableInfoSamplePickup(), filter, null);

        return  selector.getArray(SamplePickup.class);
    }

    public SamplePickup[] getSamplePickups(Container c, User user, int sampleId)
    {
        DbScope scope = BioTrustSchema.getInstance().getSchema().getScope();
        UserSchema schema = QueryService.get().getUserSchema(user, c, BioTrustQuerySchema.NAME);

        SQLFragment sql = new SQLFragment("SELECT * FROM ");
        sql.append(BioTrustSchema.getInstance().getTableInfoSamplePickup(), "samplepickup");
        sql.append(" WHERE RowId IN (SELECT SamplePickup FROM ");
        sql.append(schema.getTable(BioTrustQuerySchema.TISSUE_RECORD_TABLE_NAME), "tissuerecords");
        sql.append(" WHERE SampleId = ?)");
        sql.add(sampleId);

        SqlSelector selector = new SqlSelector(scope, sql);
        return  selector.getArray(SamplePickup.class);
    }

    public SamplePickup saveSamplePickup(Container c, User user, SamplePickup samplePickup)
    {

        if (samplePickup.isNew())
        {
            samplePickup.beforeInsert(user, c.getId());
            return Table.insert(user, BioTrustSchema.getInstance().getTableInfoSamplePickup(), samplePickup);
        }
        else
        {
            return Table.update(user, BioTrustSchema.getInstance().getTableInfoSamplePickup(), samplePickup, samplePickup.getRowId());
        }
    }

    public void deleteSamplePickup(Container c, User user, SamplePickup samplePickup)
    {
        if (samplePickup.isNew())
            return;

        deleteSamplePickup(c, user, samplePickup.getRowId());
    }

    public void deleteSamplePickup(Container c, User user, int pickupId)
    {
        DbScope scope = BioTrustSchema.getInstance().getSchema().getScope();

        try (DbScope.Transaction transaction = scope.ensureTransaction())
        {
            SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("PickupId"), pickupId);
            filter.addCondition(FieldKey.fromParts("Container"), c.getId());
            Table.delete(BioTrustSchema.getInstance().getTableInfoSamplePickupMap(), filter);

            SimpleFilter pickupFilter = new SimpleFilter(FieldKey.fromParts("RowId"), pickupId);
            pickupFilter.addCondition(FieldKey.fromParts("Container"), c.getId());
            Table.delete(BioTrustSchema.getInstance().getTableInfoSamplePickup(), pickupFilter);

            transaction.commit();
        }
    }

    public void setSamplePickup(Container c, User user, int tissueId, int pickupId)
    {
        DbScope scope = BioTrustSchema.getInstance().getSchema().getScope();

        SQLFragment sql = new SQLFragment("INSERT INTO ").append(BioTrustSchema.getInstance().getTableInfoSamplePickupMap(), "");
        sql.append(" (TissueId, PickupId, Container) VALUES (?, ?, ?)");

        sql.addAll(new Object[]{tissueId, pickupId, c.getId()});
        SqlExecutor executor = new SqlExecutor(scope);

        executor.execute(sql);
    }

    public void unsetSamplePickup(Container c, User user, int tissueId, int pickupId)
    {
        DbScope scope = BioTrustSchema.getInstance().getSchema().getScope();

        SQLFragment sql = new SQLFragment("DELETE FROM ").append(BioTrustSchema.getInstance().getTableInfoSamplePickupMap(), "");
        sql.append(" WHERE TissueId = ? AND PickupId = ? AND Container = ?");

        sql.addAll(new Object[]{tissueId, pickupId, c.getId()});
        SqlExecutor executor = new SqlExecutor(scope);

        executor.execute(sql);
    }

    public void surveyDeleted(Container c, User user, Survey survey) throws Exception
    {
        DbScope scope = BioTrustSchema.getInstance().getSchema().getScope();

        try (DbScope.Transaction transaction = scope.ensureTransaction())
        {
            // delete samples associated with the survey instance
            UserSchema schema = QueryService.get().getUserSchema(user, c, BioTrustQuerySchema.NAME);
            if (schema != null)
            {
                TableInfo sampleTable = schema.getTable(BioTrustQuerySchema.SPECIMEN_REQUEST_TABLE_NAME);

                if (sampleTable != null && survey.getResponsesPk() != null)
                {
                    SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("StudyId"), Integer.parseInt(survey.getResponsesPk()));
                    TableSelector selector = new TableSelector(sampleTable, Collections.singleton("RowId"), filter, null);
                    Integer[] sampleIds = selector.getArray(Integer.class);
                    for (Integer sampleId : sampleIds)
                    {
                        deleteSampleRequest(c, user, sampleId);
                    }
                }
            }
            transaction.commit();
        }
    }

    /**
     * Sets the reviewers for the specified sample
     */
    public void setSampleReviewers(Container c, int tissueId, String status, List<User> reviewers)
    {
        DbScope scope = BioTrustSchema.getInstance().getSchema().getScope();
        try (DbScope.Transaction transaction = scope.ensureTransaction())
        {
            // first remove all existing reviewer entries for this tissueId and status
            SQLFragment sql = new SQLFragment("DELETE FROM ").append(BioTrustSchema.getInstance().getTableInfoSampleReviewerMap(), "");
            sql.append(" WHERE TissueId = ? AND Status = ? AND Container = ?");
            sql.addAll(new Object[]{tissueId, status, c.getId()});
            new SqlExecutor(scope).execute(sql);

            for (User user : reviewers)
            {
                sql = new SQLFragment("INSERT INTO ").append(BioTrustSchema.getInstance().getTableInfoSampleReviewerMap(), "");
                sql.append(" (TissueId, Reviewer, Status, Container) VALUES (?, ?, ?, ?)");

                sql.addAll(new Object[]{tissueId, user.getUserId(), status, c.getId()});
                new SqlExecutor(scope).execute(sql);
            }
            transaction.commit();
        }
    }

    /**
     * Returns the set of users specified to review the specified sample
     * @param c
     * @param tissueId
     * @return
     */
    public Set<User> getSampleReviewers(Container c, int tissueId, String status)
    {
        Set<User> reviewers = new HashSet<>();

        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("Container"), c.getId());
        filter.addCondition(FieldKey.fromParts("TissueId"), tissueId);
        filter.addCondition(FieldKey.fromParts("Status"), status);

        TableSelector selector = new TableSelector(BioTrustSchema.getInstance().getTableInfoSampleReviewerMap(), Collections.singleton("Reviewer"), filter, null);

        for (Integer userId : selector.getArray(Integer.class))
        {
            if (userId != null)
            {
                User user = UserManager.getUser(userId);
                if (user != null)
                    reviewers.add(user);
            }
        }
        return reviewers;
    }

    public Set<String> getAlwaysEditablePropertyNames()
    {
        Set<String> propNames = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        propNames.addAll(SampleRequestDomain.ALWAYS_EDITABLE_PROPERTY_NAMES);
        propNames.addAll(TissueRecordDomain.ALWAYS_EDITABLE_PROPERTY_NAMES);
        return propNames;
    }

    public static class TestCase extends Assert
    {
        @Test
        public void test() throws Exception
        {
            Container c = ContainerManager.getSharedContainer();
            User user = TestContext.get().getUser();

            BioTrustSampleManager mgr = BioTrustSampleManager.get();
            FolderType originalFolderType = c.getFolderType();
            c.setFolderType(ModuleLoader.getInstance().getFolderType(BioTrustRCFolderType.NAME), user);

            // initialize sample domains
            BioTrustRCFolderType.initializeSampleDomains(c, user);

            // create a survey design and survey
            SurveyDesign design = new SurveyDesign();
            design.setLabel("test survey design");
            design.setSchemaName("biotrust");
            design.setQueryName("RequestCategory");
            design.setMetadata("foo");

            design = SurveyService.get().saveSurveyDesign(c, user, design);

            Survey survey = new Survey();
            survey.setLabel("Test Study");
            survey.setSurveyDesignId(design.getRowId());

            survey = SurveyService.get().saveSurvey(c, user, survey);

            // add some sample requests
            UserSchema schema = QueryService.get().getUserSchema(user, c, BioTrustQuerySchema.NAME);
            TableInfo sampleTable = schema.getTable(BioTrustQuerySchema.SPECIMEN_REQUEST_TABLE_NAME);
            TableInfo tissueTable = schema.getTable(BioTrustQuerySchema.TISSUE_RECORD_TABLE_NAME);

            assertNotNull("failed getting sample table", sampleTable);
            assertNotNull("failed getting tissue table", tissueTable);

            List<Map<String, Object>> samples = null;

            if (sampleTable != null)
            {
                List<Map<String, Object>> rows = new ArrayList<>();

                Map<String, Object> row = new HashMap<>();
                rows.add(row);

                row.put("studyid", survey.getRowId());
                row.put("requesttype", "Surgical");
                row.put("container", c);
                row.put("requireblood", true);

                QueryUpdateService qus = sampleTable.getUpdateService();

                samples = qus.insertRows(user, c, rows, new BatchValidationException(), null, null);
            }

            String[] tissueTypes = new String[]{"Breast", "Prostate", "Colon"};
            List<Map<String, Object>> tissues = null;
            if (tissueTable != null && samples != null)
            {
                List<Map<String, Object>> rows = new ArrayList<>();

                for (Map<String, Object> sample : samples)
                {
                    for (String type : tissueTypes)
                    {
                        Map<String, Object> row = new HashMap<>();
                        rows.add(row);

                        row.put("sampleid", sample.get("rowid"));
                        row.put("tissuetype", type);
                        row.put("container", c);
                    }
                }

                QueryUpdateService qus = tissueTable.getUpdateService();
                tissues = qus.insertRows(user, c, rows, new BatchValidationException(), null, null);
            }

            // create eligibility info
            TableInfo eligibiltyTable = schema.getTable(BioTrustQuerySchema.PARTICIPANT_ELIGIBILITY_TABLE_NAME);
            String[] pathTypes = new String[]{"Cancer", "Not Cancer"};

            assertNotNull("failed getting eligibility table", eligibiltyTable);
            List<Map<String, Object>> eligibility = null;

            if (eligibiltyTable != null)
            {
                List<Map<String, Object>> rows = new ArrayList<>();

                for (String type : pathTypes)
                {
                    Map<String, Object> row = new HashMap<>();
                    rows.add(row);

                    row.put("totalcount", 100);
                    row.put("container", c);
                    row.put("gender", "male");
                    row.put("pathstage", type);
                }
                QueryUpdateService qus = eligibiltyTable.getUpdateService();
                eligibility = qus.insertRows(user, c, rows, new BatchValidationException(), null, null);
            }

            String status = "_TEST STATUS_";
            BioTrustManager.get().insertRequestStatus(status);

            // associate tissue records with eligibility information
            Map<Integer, Integer> tissueToEligibilityMap = new HashMap<>();
            int idx = 0;
            for (Map<String, Object> tissue : tissues)
            {
                Map<String, Object> pe = eligibility.get(idx % 2);
                Integer tissueId = (Integer)tissue.get("rowid");
                Integer peId = (Integer)pe.get("rowid");

                mgr.setParticipantEligibility(c, user, tissueId, peId);
                tissueToEligibilityMap.put(tissueId, peId);

                mgr.setSampleReviewers(c, tissueId, status, Collections.singletonList(user));

                idx++;
            }

            // verify the tissue to eligibility mapping
            TableInfo peMapTable = BioTrustSchema.getInstance().getTableInfoParticipantEligibilityMap();
            TableSelector selector = new TableSelector(peMapTable, new SimpleFilter(FieldKey.fromParts("container"), c), null);
            TableResultSet rs = selector.getResultSet();

            while (rs.next())
            {
                Map<String, Object> row = rs.getRowMap();

                Integer tissueId = (Integer)row.get("tissueid");
                Integer eligibilityId = (Integer)row.get("eligibilityid");

                if (tissueToEligibilityMap.containsKey(tissueId))
                {
                    assertTrue("incorrect participant eligibility information for tissue record", tissueToEligibilityMap.get(tissueId).equals(eligibilityId));

                    Set<User> reviewers = mgr.getSampleReviewers(c, tissueId, status);
                    assertTrue("incorrect number of reviewers associated with the sample request", reviewers.size() == 1);
                }
            }
            rs.close();

            // delete the samples, they should cascade and delete associated types
            for (Map<String, Object> sample : samples)
                mgr.deleteSampleRequest(c, user, (Integer)sample.get("rowid"));

            // delete participant eligibility records
            for (Map<String, Object> pe : eligibility)
                mgr.deleteParticipantEligibility(c, user, (Integer) pe.get("rowid"));

            SurveyService.get().deleteSurveyDesign(c, user, design.getRowId(), true);
            BioTrustManager.get().deleteRequestStatus(status);
            BioTrustRCFolderType.deleteSampleDomains(c, user);
            c.setFolderType(originalFolderType, user);
        }
    }
}
