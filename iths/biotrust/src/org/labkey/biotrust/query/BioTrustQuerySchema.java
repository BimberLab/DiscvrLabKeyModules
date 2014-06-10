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
package org.labkey.biotrust.query;

import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.biotrust.BioTrustSchema;
import org.labkey.biotrust.query.contacts.ContactTable;
import org.labkey.biotrust.query.samples.ParticipantEligibilityTable;
import org.labkey.biotrust.query.samples.SamplePickupTable;
import org.labkey.biotrust.query.samples.SampleRequestTable;
import org.labkey.biotrust.query.samples.SampleReviewerMapTable;
import org.labkey.biotrust.query.samples.TissueRecordTable;
import org.labkey.biotrust.query.study.StudyRegistrationTable;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * User: klum
 * Date: 1/7/13
 */
public class BioTrustQuerySchema extends UserSchema
{
    public static final String SCHEMA_DESCR = "Contains data about NWBioTrust specimen requests.";
    public static final String NAME = "biotrust";

    public static final String REQUEST_CATEGORY_TABLE_NAME = "RequestCategory";
    public static final String REQUEST_STATUS_TABLE_NAME = "RequestStatus";
    public static final String DOCUMENT_TYPES_TABLE_NAME = "DocumentTypes";
    public static final String REQUIRED_DOCUMENTS_TABLE_NAME = "RequiredDocuments";
    public static final String SPECIMEN_REQUEST_DOCUMENTS_TABLE_NAME = "SpecimenRequestDocuments";
    public static final String DOCUMENT_PROPERTIES_TABLE_NAME = "DocumentProperties";

    public static final String STUDY_REGISTRATION_TABLE_NAME = "StudyRegistrations";
    public static final String SPECIMEN_REQUEST_TABLE_NAME = "SampleRequests";
    public static final String TISSUE_RECORD_TABLE_NAME = "TissueRecords";
    public static final String PARTICIPANT_ELIGIBILITY_TABLE_NAME = "ParticipantEligibility";
    public static final String PARTICIPANT_ELIGIBILITY_MAP_TABLE_NAME = "ParticipantEligibilityMap";
    public static final String SAMPLE_PICKUP_TABLE_NAME = "SamplePickup";
    public static final String SAMPLE_PICKUP_MAP_TABLE_NAME = "SamplePickupMap";
    public static final String SAMPLE_REVIEWER_MAP_TABLE_NAME = "SampleReviewerMap";
    public static final String CONTACT_TABLE_NAME = "Contacts";

    // custom queries
    public static final String SAMPLE_REQUEST_TISSUE_RECORDS = "SampleRequestTissueRecords";
    public static final String STUDY_DASHBOARD_DATA = "StudyDashboardData";

    public BioTrustQuerySchema(User user, Container c)
    {
        super(NAME, SCHEMA_DESCR, user, c, BioTrustSchema.getInstance().getSchema());
    }

    @Override
    public Set<String> getTableNames()
    {
        Set<String> tables = new HashSet<>();
        tables.add(DOCUMENT_TYPES_TABLE_NAME);
        tables.add(REQUEST_CATEGORY_TABLE_NAME);
        tables.add(REQUEST_STATUS_TABLE_NAME);
        tables.addAll(getDomainMap(getContainer()).keySet());

        tables.add(STUDY_REGISTRATION_TABLE_NAME);
        tables.add(SPECIMEN_REQUEST_TABLE_NAME);
        tables.add(TISSUE_RECORD_TABLE_NAME);
        tables.add(PARTICIPANT_ELIGIBILITY_TABLE_NAME);
        tables.add(SAMPLE_PICKUP_TABLE_NAME);
        tables.add(CONTACT_TABLE_NAME);
        tables.add(SAMPLE_REVIEWER_MAP_TABLE_NAME);

        return tables;
    }

    public static Map<String, Domain> getDomainMap(Container container)
    {
        Map<String, Domain> domainMap = new TreeMap<>();

        for (Domain domain : PropertyService.get().getDomains(container))
        {
            DomainKind kind = domain.getDomainKind();
            if (kind != null && RequestResponsesDomain.NAME.equalsIgnoreCase(kind.getKindName()))
                domainMap.put(domain.getName(), domain);
        }
        return domainMap;
    }


    @Override
    protected TableInfo createTable(String name)
    {
        Map<String, Domain> domainMap = getDomainMap(getContainer());

        if (REQUEST_CATEGORY_TABLE_NAME.equalsIgnoreCase(name))
            return new RequestCategoryTable(BioTrustSchema.getInstance().getTableInfoRequestCategory(), this);
        else if (REQUEST_STATUS_TABLE_NAME.equalsIgnoreCase(name))
            return new RequestStatusTable(BioTrustSchema.getInstance().getTableInfoRequestStatus(), this);
        else if (DOCUMENT_TYPES_TABLE_NAME.equalsIgnoreCase(name))
            return new DocumentTypesTable(BioTrustSchema.getInstance().getTableInfoDocumentTypes(), this);
        else if (SPECIMEN_REQUEST_TABLE_NAME.equalsIgnoreCase(name))
            return new SampleRequestTable(this);
        else if (TISSUE_RECORD_TABLE_NAME.equalsIgnoreCase(name))
            return new TissueRecordTable(this);
        else if (PARTICIPANT_ELIGIBILITY_TABLE_NAME.equalsIgnoreCase(name))
            return new ParticipantEligibilityTable(this);
        else if (SAMPLE_PICKUP_TABLE_NAME.equalsIgnoreCase(name))
            return new SamplePickupTable(BioTrustSchema.getInstance().getTableInfoSamplePickup(), this);
        else if (STUDY_REGISTRATION_TABLE_NAME.equalsIgnoreCase(name))
            return new StudyRegistrationTable(this);
        else if (CONTACT_TABLE_NAME.equalsIgnoreCase(name))
            return new ContactTable(this);
        else if (SAMPLE_REVIEWER_MAP_TABLE_NAME.equalsIgnoreCase(name))
            return new SampleReviewerMapTable(BioTrustSchema.getInstance().getTableInfoSampleReviewerMap(), this);
        else if (domainMap.containsKey(name))
            return new RequestResponsesTable(domainMap.get(name), this);

        return null;
    }
}
