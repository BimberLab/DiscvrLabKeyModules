/*
 * Copyright (c) 2013 LabKey Corporation
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
package org.labkey.biotrust.security;

import org.junit.Assert;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;
import org.labkey.api.data.Container;
import org.labkey.api.security.Group;
import org.labkey.api.security.MutableSecurityPolicy;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.SecurityPolicyManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.security.permissions.AbstractPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.ReadSomePermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.view.NotFoundException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * User: klum
 * Date: 1/22/13
 */
public class BioTrustSecurityManager
{
    private static final BioTrustSecurityManager _instance = new BioTrustSecurityManager();

    public static final String RC_GROUP_NAME = "Research Coordinators";
    public static final String APPROVAL_REVIEWERS_GROUP_NAME = "Approval Reviewers";
    public static final String LABKEY_API_GROUP_NAME = "LabKey API";

    private BioTrustSecurityManager()
    {
        // prevent external construction with a private default constructor
    }

    public static BioTrustSecurityManager get()
    {
        return _instance;
    }

    /**
     * Returns the project group for biotrust RC's
     * @param c
     * @return
     */
    @Nullable
    public Group getResearchCoordinatorGroup(Container c)
    {
        return getProjectGroup(c, RC_GROUP_NAME);
    }

    private Group getProjectGroup(Container c, String groupName)
    {
        try {
            Integer id = SecurityManager.getGroupId(c.getProject(), groupName);
            return SecurityManager.getGroup(id);
        }
        catch (NotFoundException e)
        {
            return null;
        }
    }

    /**
     * Ensures that the project group for biotrust RC's exists and is initialized with
     * the proper RC role.
     * @param c
     * @return
     */
    @Nullable
    public Group ensureResearchCoordinatorGroup(Container c)
    {
        return ensureGroup(c, RC_GROUP_NAME);
    }


    private Group ensureGroup(Container c, String groupName)
    {
        try {
            Integer id = SecurityManager.getGroupId(c.getProject(), groupName);
            return SecurityManager.getGroup(id);
        }
        catch (NotFoundException e)
        {
            Group group = SecurityManager.createGroup(c.getProject(), groupName);

            return group;
        }
    }

    /**
     * Ensures that the project groups for biotrust sample reviewers exists and is initialized with
     * the proper reviewer role.
     * @param c
     */
    public Group ensureApprovalReviewersGroup(Container c)
    {
        return ensureGroup(c, APPROVAL_REVIEWERS_GROUP_NAME);
    }

    public Group getApprovalReviewersGroup(Container c)
    {
        return getProjectGroup(c, APPROVAL_REVIEWERS_GROUP_NAME);
    }

    /**
     * Ensures that the project groups for biotrust LabKey API access exists and is initialized with
     * the proper Reader role.
     * @param c
     */
    public Group ensureLabKeyAPIGroup(Container c)
    {
        return ensureGroup(c, LABKEY_API_GROUP_NAME);
    }

    public static class TestCase extends Assert
    {
        private static List<Class<? extends AbstractPermission>> _allPermissions = new ArrayList<>();

        static {
            _allPermissions.add(ReadPermission.class);
            _allPermissions.add(InsertPermission.class);
            _allPermissions.add(UpdatePermission.class);
            _allPermissions.add(SubmitRequestsPermission.class);
            _allPermissions.add(EditRequestsPermission.class);
            _allPermissions.add(ApproveRequestsPermission.class);
            _allPermissions.add(UpdateWorkflowPermission.class);
            _allPermissions.add(RequestReviewPermission.class);
            _allPermissions.add(UpdateReviewPermission.class);
        }

        @Test
        @SuppressWarnings("unchecked")
        public void test() throws Exception
        {
            Container c = JunitUtil.getTestContainer();

            // all tested permissions
            User pi = ensureUser("pi@nwbt.com");
            User studyContact = ensureUser("studyContact@nwbt.com");
            User rc = ensureUser("rc@nwbt.com");
            User faculty = ensureUser("faculty@nwbt.com");

            try {

                MutableSecurityPolicy policy = new MutableSecurityPolicy(c, c.getPolicy());
                policy.addRoleAssignment(pi, RoleManager.getRole(PrincipalInvestigatorRole.class));
                policy.addRoleAssignment(studyContact, RoleManager.getRole(StudyContactRole.class));
                policy.addRoleAssignment(rc, RoleManager.getRole(BioTrustRCRole.class));
                policy.addRoleAssignment(faculty, RoleManager.getRole(FacultyRole.class));

                SecurityPolicyManager.savePolicy(policy);

                // verify role permissions
                verifyPermissions(c, pi, _allPermissions, Arrays.asList(
                        SubmitRequestsPermission.class,
                        ReadPermission.class,
                        ApproveRequestsPermission.class,
                        InsertPermission.class,
                        UpdatePermission.class
                ));

                verifyPermissions(c, studyContact, _allPermissions, Arrays.asList(
                        ReadPermission.class,
                        ReadSomePermission.class
                ));

                verifyPermissions(c, rc, _allPermissions, Arrays.asList(
                        SubmitRequestsPermission.class,
                        EditRequestsPermission.class,
                        ReadPermission.class,
                        InsertPermission.class,
                        UpdatePermission.class,
                        UpdateWorkflowPermission.class,
                        RequestReviewPermission.class,
                        UpdateReviewPermission.class
                ));

                verifyPermissions(c, faculty, _allPermissions, Arrays.asList(
                        ReadPermission.class,
                        InsertPermission.class,
                        UpdateReviewPermission.class
                ));
            }
            finally
            {
                // clean up
                UserManager.deleteUser(pi.getUserId());
                UserManager.deleteUser(studyContact.getUserId());
                UserManager.deleteUser(rc.getUserId());
                UserManager.deleteUser(faculty.getUserId());

                SecurityPolicyManager.deletePolicy(c);
            }
        }

        private User ensureUser(String email)
        {
            try {
                User u = UserManager.getUser(new ValidEmail(email));
                if (u == null)
                    return SecurityManager.addUser(new ValidEmail(email)).getUser();
                else
                    return u;
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        }

        private void verifyPermissions(Container c, User u, List<Class<? extends AbstractPermission>> allPermissions, List<Class<? extends AbstractPermission>> permissions)
        {
            for (Class<? extends AbstractPermission> p : allPermissions)
            {
                if (permissions.contains(p))
                    assertTrue("User: " + u.getEmail() + " should have permission " + p.getName(), c.hasPermission(u, p));
                else
                    assertFalse("User: " + u.getEmail() + " should not have permission " + p.getName(), c.hasPermission(u, p));
            }
        }
    }
}
