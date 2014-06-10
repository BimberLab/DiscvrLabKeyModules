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

import org.apache.commons.collections15.map.HashedMap;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.attachments.SpringAttachmentFile;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableSelector;
import org.labkey.api.module.FolderType;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.survey.SurveyService;
import org.labkey.api.survey.model.Survey;
import org.labkey.api.survey.model.SurveyDesign;
import org.labkey.api.util.GUID;
import org.labkey.api.util.TestContext;
import org.labkey.api.util.UnexpectedException;
import org.labkey.biotrust.model.DocumentProperties;
import org.labkey.biotrust.model.DocumentType;
import org.labkey.biotrust.model.SpecimenRequestAttachment;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BioTrustManager
{
    private static final Logger _log = Logger.getLogger(BioTrustManager.class);
    private static final BioTrustManager _instance = new BioTrustManager();

    private BioTrustManager()
    {
    }

    public static BioTrustManager get()
    {
        return _instance;
    }

    public Container getBioTrustProject(Container current)
    {
        for (Container c : ContainerManager.getProjects())
        {
            if (c.getFolderType().getName().equals(BioTrustRCFolderType.NAME))
                return c;
        }
        return null;
    }

    public DocumentType saveDocumentType(Container c, User user, DocumentType doc)
    {

        if (doc.isNew())
        {
            doc.beforeInsert(user, c.getId());
            return Table.insert(user, BioTrustSchema.getInstance().getTableInfoDocumentTypes(), doc);
        }
        else
        {
            return Table.update(user, BioTrustSchema.getInstance().getTableInfoDocumentTypes(), doc, doc.getTypeId());
        }
    }

    public List<DocumentType> getDocumentTypes(Container c, User user)
    {
        TableSelector selector = new TableSelector(BioTrustSchema.getInstance().getTableInfoDocumentTypes());

        return Arrays.asList(selector.getArray(DocumentType.class));
    }

    public DocumentType getDocumentType(Container c, User user, int typeId)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("TypeId"), typeId);
        TableSelector selector = new TableSelector(BioTrustSchema.getInstance().getTableInfoDocumentTypes(), filter, null);

        DocumentType[] types = selector.getArray(DocumentType.class);

        assert types.length <= 1;

        return types.length == 1 ? types[0] : null;
    }

    public DocumentType getDocumentTypeByName(Container c, User user, String typeName)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("Name"), typeName);
        TableSelector selector = new TableSelector(BioTrustSchema.getInstance().getTableInfoDocumentTypes(), filter, null);

        DocumentType[] types = selector.getArray(DocumentType.class);

        assert types.length <= 1;

        return types.length == 1 ? types[0] : null;
    }


    public void deleteDocumentType(Container c, User user, DocumentType doc)
    {
        if (doc.isNew())
            return;

        try (DbScope.Transaction transaction = BioTrustSchema.getInstance().getSchema().getScope().ensureTransaction())
        {
            Table.delete(BioTrustSchema.getInstance().getTableInfoRequiredDocuments(), new SimpleFilter(FieldKey.fromParts("DocumentTypeId"), doc.getTypeId()));
            Table.delete(BioTrustSchema.getInstance().getTableInfoSpecimenRequestDocuments(), new SimpleFilter(FieldKey.fromParts("DocumentTypeId"), doc.getTypeId()));
            Table.delete(BioTrustSchema.getInstance().getTableInfoDocumentTypes(), new SimpleFilter(FieldKey.fromParts("TypeId"), doc.getTypeId()));

            transaction.commit();
        }
    }

    /**
     * Returns the list of required document types for a specified survey instance.
     * @param c
     * @param user
     * @param surveyId
     * @return
     */
    public List<DocumentType> getRequiredDocumentTypes(Container c, User user, int surveyId)
    {
        DbScope scope = BioTrustSchema.getInstance().getSchema().getScope();
        Survey survey = SurveyService.get().getSurvey(c, user, surveyId);

        if (survey != null)
        {
            // first get any attachments already uploaded
            SQLFragment sql = new SQLFragment("SELECT * FROM ");

            sql.append(BioTrustSchema.getInstance().getTableInfoDocumentTypes(), "dtypes");
            sql.append(" WHERE TypeId IN (SELECT DocumentTypeId FROM ").append(BioTrustSchema.getInstance().getTableInfoRequiredDocuments(), "reqdoc");
            sql.append(" WHERE SurveyDesignId = ?)");

            sql.add(survey.getSurveyDesignId());

            SqlSelector selector = new SqlSelector(scope, sql);

            DocumentType[] documents = selector.getArray(DocumentType.class);

            return Arrays.asList(documents);
        }
        return Collections.emptyList();
    }

    public boolean isDocumentRequired(Container c, User user, DocumentType doc, int surveyDesignId)
    {
        DbScope scope = BioTrustSchema.getInstance().getSchema().getScope();
        SQLFragment sql = new SQLFragment("SELECT SurveyDesignId FROM ");

        sql.append(BioTrustSchema.getInstance().getTableInfoRequiredDocuments(), "reqdoc");
        sql.append(" WHERE SurveyDesignId = ? AND DocumentTypeId = ?");

        sql.addAll(new Object[]{surveyDesignId, doc.getTypeId()});

        SqlSelector selector = new SqlSelector(scope, sql);

        return selector.getArray(Integer.class).length > 0;
    }

    public void setRequiredDocument(Container c, User user, DocumentType doc, int surveyDesignId)
    {
        if (!isDocumentRequired(c, user, doc, surveyDesignId))
        {
            DbScope scope = BioTrustSchema.getInstance().getSchema().getScope();

            SQLFragment sql = new SQLFragment("INSERT INTO ").append(BioTrustSchema.getInstance().getTableInfoRequiredDocuments(), "");
            sql.append(" (SurveyDesignId, DocumentTypeId) VALUES (?, ?)");

            sql.addAll(new Object[]{surveyDesignId, doc.getTypeId()});
            SqlExecutor executor = new SqlExecutor(scope);

            executor.execute(sql);
        }
    }

    public void unsetRequiredDocument(Container c, User user, DocumentType doc, int surveyDesignId)
    {
        DbScope scope = BioTrustSchema.getInstance().getSchema().getScope();

        SQLFragment sql = new SQLFragment("DELETE FROM ").append(BioTrustSchema.getInstance().getTableInfoRequiredDocuments(), "");
        sql.append(" WHERE SurveyDesignId = ?");

        sql.add(surveyDesignId);
        SqlExecutor executor = new SqlExecutor(scope);

        executor.execute(sql);
    }

    /**
     * Returns the document set (or uploaded documents) for a specified survey instance.
     * @param c
     * @param user
     * @param ownerId
     * @return
     */
    public List<SpecimenRequestAttachment> getDocumentSet(final Container c, final User user, final int ownerId,
                                                          final SpecimenRequestAttachment.OwnerType ownerType)
    {
        // TODO: issue with having same ownerId/ownerType combination in different projects
        final List<SpecimenRequestAttachment> documentSet = new ArrayList<>();
        DbScope scope = BioTrustSchema.getInstance().getSchema().getScope();

        SQLFragment sql = new SQLFragment();
        sql.append("SELECT * FROM ").append(BioTrustSchema.getInstance().getTableInfoSpecimenRequestDocuments(), "srd");
        sql.append(" WHERE OwnerId = ? AND OwnerType = ?");

        sql.add(ownerId);
        sql.add(ownerType.name());
        SqlSelector selector = new SqlSelector(scope, sql);

        selector.forEachMap(new Selector.ForEachBlock<Map<String, Object>>()
        {
            @Override
            public void exec(Map<String, Object> row) throws SQLException
            {
                String entityId = (String)row.get("AttachmentParentId");
                int documentTypeId = (Integer)row.get("DocumentTypeId");

                boolean required = false;

                if (ownerType == SpecimenRequestAttachment.OwnerType.study)
                {
                    Survey survey = SurveyService.get().getSurvey(c, user, ownerId);
                    DocumentType documentType = getDocumentType(c, user, documentTypeId);
                    if (survey != null && documentType != null)
                    {
                        required = isDocumentRequired(c, user, documentType, survey.getSurveyDesignId());
                    }
                }
                documentSet.add(new SpecimenRequestAttachment(c, entityId, ownerId, ownerType, documentTypeId, required));
            }
        });
        return documentSet;
    }

    /**
     * Saves or updates a specific linked document URL record for a survey
     * @param c
     * @param user
     * @param ownerId
     * @param ownerType
     * @param documentType
     * @param name The linked document name
     * @param url The URL for the linked document
     */
    public void saveLinkedDocumentUrl(Container c, User user, int ownerId, SpecimenRequestAttachment.OwnerType ownerType,
                                      DocumentType documentType, String name, String url)
    {
        SpecimenRequestAttachment parent = ensureAttachmentParent(c, user, ownerId, documentType, ownerType);
        BioTrustManager.get().saveDocumentProperties(user, parent.getEntityId(), name, true, url);
    }

    /**
     * Saves or updates a specified document set for a survey
     * @param c
     * @param user
     * @param documents
     * @param ownerId
     */
    public void saveDocuments(Container c, User user, Map<DocumentType, List<AttachmentFile>> documents, int ownerId,
                              SpecimenRequestAttachment.OwnerType ownerType)
    {
        for (Map.Entry<DocumentType, List<AttachmentFile>> entry : documents.entrySet())
        {
            SpecimenRequestAttachment parent = ensureAttachmentParent(c, user, ownerId, entry.getKey(), ownerType);

            try {
                AttachmentService.get().addAttachments(parent, entry.getValue(), user);
            }
            catch (IOException e)
            {
                throw UnexpectedException.wrap(e);
            }

            // create a record for the document properties
            for (AttachmentFile doc : entry.getValue())
            {
                if (BioTrustManager.get().getDocumentProperties(parent.getEntityId(), doc.getFilename()) == null)
                {
                    BioTrustManager.get().saveDocumentProperties(user, parent.getEntityId(), doc.getFilename(), true, null);
                }
            }
        }
    }

    private SpecimenRequestAttachment ensureAttachmentParent(Container c, User user, int ownerId, DocumentType documentType,
                                                             SpecimenRequestAttachment.OwnerType ownerType)
    {
        SpecimenRequestAttachment parent = getAttachmentParent(c, user, documentType, ownerId, ownerType);
        if (parent.getEntityId() == null)
        {
            DbScope scope = BioTrustSchema.getInstance().getSchema().getScope();

            // create a new guid for the parent entity id
            parent.setEntityId(GUID.makeGUID());

            // poke an entry in the the specimen request documents table
            SQLFragment sql = new SQLFragment("INSERT INTO ").append(BioTrustSchema.getInstance().getTableInfoSpecimenRequestDocuments(), "");
            sql.append(" (OwnerId, OwnerType, DocumentTypeId, AttachmentParentId) VALUES (?, ?, ?, ?)");

            sql.addAll(new Object[]{ownerId, ownerType.name(), documentType.getTypeId(), parent.getEntityId()});
            SqlExecutor executor = new SqlExecutor(scope);

            executor.execute(sql);
        }

        return parent;
    }

    /**
     * Deletes the specified set of documents for a survey instance
     */
    public void deleteDocuments(Container c, User user, Map<DocumentType, List<String>> documents, int ownerId,
                                SpecimenRequestAttachment.OwnerType ownerType)
    {
        DbScope scope = BioTrustSchema.getInstance().getSchema().getScope();

        try (DbScope.Transaction transaction = scope.ensureTransaction())
        {
            for (Map.Entry<DocumentType, List<String>> entry : documents.entrySet())
            {
                SpecimenRequestAttachment parent = getAttachmentParent(c, user, entry.getKey(), ownerId, ownerType);

                if (parent != null)
                {
                    // delete the attachments from the parent by name, and the document properties
                    for (String name : entry.getValue())
                    {
                        AttachmentService.get().deleteAttachment(parent, name, user);
                        deleteDocumentProperties(parent, name);
                    }

                    // if there are not more attachments for this parent, remove the entry in the the specimen request documents table
                    if (parent.getAttachments().size() == 0 && getLinkedDocuments(parent.getEntityId()).size() == 0)
                    {
                        SQLFragment sql = new SQLFragment("DELETE FROM ").append(BioTrustSchema.getInstance().getTableInfoSpecimenRequestDocuments(), "");
                        sql.append(" WHERE OwnerId = ? AND OwnerType = ? AND DocumentTypeId = ? AND AttachmentParentId = ?");

                        sql.addAll(new Object[]{ownerId, ownerType.name(), entry.getKey().getTypeId(), parent.getEntityId()});
                        SqlExecutor executor = new SqlExecutor(scope);

                        executor.execute(sql);
                    }
                }
            }
            transaction.commit();
        }
    }

    /**
     * Returns the document set (or uploaded documents) for a specified survey instance and document type
     */
    public SpecimenRequestAttachment getAttachmentParent(Container c, User user, DocumentType document, int ownerId,
                                                         SpecimenRequestAttachment.OwnerType ownerType)
    {
        SpecimenRequestAttachment parent = new SpecimenRequestAttachment(c, null, ownerId, ownerType, document.getTypeId());
        if (document != null)
        {
            DbScope scope = BioTrustSchema.getInstance().getSchema().getScope();

            SQLFragment sql = new SQLFragment();
            sql.append("SELECT AttachmentParentId FROM ").append(BioTrustSchema.getInstance().getTableInfoSpecimenRequestDocuments(), "srd");
            sql.append(" WHERE OwnerId = ? AND OwnerType = ? AND DocumentTypeId = ?");

            sql.addAll(new Object[]{ownerId, ownerType.name(), document.getTypeId()});

            SqlSelector selector = new SqlSelector(scope, sql);
            String[] attachmentParent = selector.getArray(String.class);

            assert attachmentParent.length == 0 || attachmentParent.length == 1;

            if (attachmentParent.length == 1)
            {
                parent.setEntityId(attachmentParent[0]);
            }
        }
        return parent;
    }

    public List<DocumentProperties> getLinkedDocuments(String attachmentParentId)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("AttachmentParentId"), attachmentParentId);
        filter.addCondition(FieldKey.fromParts("LinkedDocumentUrl"), null, CompareType.NONBLANK);
        return new TableSelector(BioTrustSchema.getInstance().getTableInfoDocumentProperties(), filter, null).getArrayList(DocumentProperties.class);
    }

    public DocumentProperties getLinkedDocumentByName(String attachmentParentId, String name)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("AttachmentParentId"), attachmentParentId);
        filter.addCondition(FieldKey.fromParts("DocumentName"), name);
        filter.addCondition(FieldKey.fromParts("LinkedDocumentUrl"), null, CompareType.NONBLANK);
        return new TableSelector(BioTrustSchema.getInstance().getTableInfoDocumentProperties(), filter, null).getObject(DocumentProperties.class);
    }

    public DocumentProperties getDocumentProperties(String attachmentParentId, String name)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("AttachmentParentId"), attachmentParentId);
        filter.addCondition(FieldKey.fromParts("DocumentName"), name);
        return new TableSelector(BioTrustSchema.getInstance().getTableInfoDocumentProperties(), filter, null).getObject(DocumentProperties.class);
    }

    public DocumentProperties saveDocumentProperties(User user, String attachmentParentId, String name, boolean active,
                                                     @Nullable String url)
    {
        DocumentProperties dp = getDocumentProperties(attachmentParentId, name);
        if (dp == null)
        {
            dp = new DocumentProperties();
            dp.setAttachmentParentId(attachmentParentId);
            dp.setDocumentName(name);
            dp.setActive(active);
            dp.setLinkedDocumentUrl(url);
            return Table.insert(user, BioTrustSchema.getInstance().getTableInfoDocumentProperties(), dp);
        }
        else
        {
            Object[] keys = new Object[2];
            keys[0] = dp.getAttachmentParentId();
            keys[1] = dp.getDocumentName();

            dp.setActive(active);
            if (url != null)
                dp.setLinkedDocumentUrl(url);

            return Table.update(user, BioTrustSchema.getInstance().getTableInfoDocumentProperties(), dp, keys);
        }
    }

    public void deleteDocumentProperties(SpecimenRequestAttachment parent, @Nullable String name)
    {
        if (parent != null)
        {
            SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("AttachmentParentId"), parent.getEntityId());
            if (name != null && getDocumentProperties(parent.getEntityId(), name) != null)
                filter.addCondition(FieldKey.fromParts("DocumentName"), name);

            Table.delete(BioTrustSchema.getInstance().getTableInfoDocumentProperties(), filter);
        }
    }

    public void surveyDeleted(Container c, User user, Survey survey) throws Exception
    {
        DbScope scope = BioTrustSchema.getInstance().getSchema().getScope();
        try (DbScope.Transaction transaction = scope.ensureTransaction())
        {

            // delete document set associated with the survey instance
            List<SpecimenRequestAttachment> parents = _instance.getDocumentSet(c, user, survey.getRowId(), SpecimenRequestAttachment.OwnerType.study);
            AttachmentService.get().deleteAttachments(parents.toArray(new SpecimenRequestAttachment[parents.size()]));
            for (SpecimenRequestAttachment parent : parents)
                deleteDocumentProperties(parent, null);

            // clean up the specimen request documents table
            SQLFragment sql = new SQLFragment("DELETE FROM ").append(BioTrustSchema.getInstance().getTableInfoSpecimenRequestDocuments(), "");
            sql.append(" WHERE OwnerId = ? AND OwnerType = ?");

            sql.addAll(new Object[]{survey.getRowId(), SpecimenRequestAttachment.OwnerType.study});
            SqlExecutor executor = new SqlExecutor(scope);

            executor.execute(sql);

            transaction.commit();
        }
    }

    public boolean isApprovalRequestStatus(String status)
    {
        DbScope scope = BioTrustSchema.getInstance().getSchema().getScope();
        SQLFragment sql = new SQLFragment("SELECT ApprovalState FROM ");
        sql.append(BioTrustSchema.getInstance().getTableInfoRequestStatus(), "reqst");
        sql.append(" WHERE Status = ?");
        sql.add(status);

        SqlSelector selector = new SqlSelector(scope, sql);
        Boolean isApproval = selector.getObject(Boolean.class);

        return isApproval == null ? false : isApproval;
    }

    public void insertRequestStatus(String status)
    {
        String existingStatus = new TableSelector(BioTrustSchema.getInstance().getTableInfoRequestStatus(), Collections.singleton("Status"),
                new SimpleFilter(FieldKey.fromParts("Status"), status), null).getObject(String.class);

        if (existingStatus == null)
        {
            DbScope scope = BioTrustSchema.getInstance().getSchema().getScope();
            SQLFragment sql = new SQLFragment("INSERT INTO ").append(BioTrustSchema.getInstance().getTableInfoRequestStatus(), "");
            sql.append(" (Status) VALUES (?)");
            sql.add(status);
            new SqlExecutor(scope).execute(sql);
        }
    }

    public void deleteRequestStatus(String status)
    {
        DbScope scope = BioTrustSchema.getInstance().getSchema().getScope();
        SQLFragment sql = new SQLFragment("DELETE FROM ").append(BioTrustSchema.getInstance().getTableInfoRequestStatus(), "");
        sql.append(" WHERE Status = ?");
        sql.add(status);
        new SqlExecutor(scope).execute(sql);
    }

    public static class TestCase extends Assert
    {
        private static final List<String> documents = Arrays.asList(new String[]{"IRB Approval Packet", "MTDUA Agreement", "Confidentiality Pledge", "Blank Consent Form"});
        private static final List<String> requiredDocuments = Arrays.asList(new String[]{"MTDUA Agreement", "Confidentiality Pledge"});

        @Test
        public void test() throws Exception
        {
            Container c = ContainerManager.getSharedContainer();
            User user = TestContext.get().getUser();

            BioTrustManager mgr = BioTrustManager.get();
            FolderType originalFolderType = c.getFolderType();
            c.setFolderType(ModuleLoader.getInstance().getFolderType(BioTrustRCFolderType.NAME), user);

            // initialize sample domains
            BioTrustRCFolderType.initializeSampleDomains(c, user);

            // create document types
            for (String name : documents)
            {
                DocumentType doc = new DocumentType();
                doc.setName(name);

                mgr.saveDocumentType(c, user, doc);
            }

            Map<String, DocumentType> docMap = new HashMap<>();

            for (DocumentType type : mgr.getDocumentTypes(c, user))
            {
                if (documents.contains(type.getName()))
                    docMap.put(type.getName(), type);
            }

            // create a survey design and survey
            SurveyDesign design = new SurveyDesign();
            design.setLabel("test survey design");
            design.setSchemaName("biotrust");
            design.setQueryName("RequestCategory");
            design.setMetadata("foo");

            design = SurveyService.get().saveSurveyDesign(c, user, design);

            Survey survey = new Survey();
            survey.setLabel("test survey");
            survey.setSurveyDesignId(design.getRowId());

            survey = SurveyService.get().saveSurvey(c, user, survey);

            for (String name : documents)
            {
                assertTrue("document type " + name + " not found", docMap.containsKey(name));

                DocumentType type = docMap.get(name);

                assertFalse(type.isAllowMultipleUpload());
                assertFalse(type.isNew());
            }

            // assign required document types
            for (String name : requiredDocuments)
            {
                DocumentType type = docMap.get(name);

                assertNotNull(type);
                mgr.setRequiredDocument(c, user, type, design.getRowId());
            }

            // save documents to the survey document set
            MultipartFile f = new MockMultipartFile("file.txt", "file.txt", "text/plain", "Hello World".getBytes());
            Map<String, MultipartFile> fileMap = new HashMap<>();
            fileMap.put("file.txt", f);
            List<AttachmentFile> files = SpringAttachmentFile.createList(fileMap);

            Map<DocumentType, List<AttachmentFile>> documentSet = new HashMap<>();
            documentSet.put(docMap.get(requiredDocuments.get(0)), files);

            mgr.saveDocuments(c, user, documentSet, survey.getRowId(), SpecimenRequestAttachment.OwnerType.study);

            Map<DocumentType, List<String>> documentsToDelete = new HashedMap<>();

            for (SpecimenRequestAttachment doc : mgr.getDocumentSet(c, user, survey.getRowId(), SpecimenRequestAttachment.OwnerType.study))
            {
                List<String> attachmentNames = new ArrayList<>();
                documentsToDelete.put(mgr.getDocumentType(c, user, doc.getDocumentTypeId()), attachmentNames);

                List<Attachment> attachments = AttachmentService.get().getAttachments(doc);

                assertTrue("no attachments returned", !attachments.isEmpty());

                for (Attachment a : attachments)
                {
                    assertTrue("attachment file does not exist", a.getName() != null);
                    attachmentNames.add(a.getName());
                }
            }

            // delete the document set
            assertTrue(!documentsToDelete.isEmpty());
            mgr.deleteDocuments(c, user, documentsToDelete, survey.getRowId(), SpecimenRequestAttachment.OwnerType.study);

            for (DocumentType type : mgr.getRequiredDocumentTypes(c, user, survey.getRowId()))
            {
                assertTrue("required type not found", requiredDocuments.contains(type.getName()));
                mgr.unsetRequiredDocument(c, user, type, survey.getSurveyDesignId());
            }

            assertTrue("required documents not at zero", mgr.getRequiredDocumentTypes(c, user, survey.getRowId()).size() == 0);

            // delete the document types and survey design and survey
            for (DocumentType type : docMap.values())
                mgr.deleteDocumentType(c, user, type);

            SurveyService.get().deleteSurveyDesign(c, user, design.getRowId(), true);

            BioTrustRCFolderType.deleteSampleDomains(c, user);
            c.setFolderType(originalFolderType, user);
        }
    }
}
