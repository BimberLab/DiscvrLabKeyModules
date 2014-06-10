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
package org.labkey.biotrust;

import org.apache.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.security.Group;
import org.labkey.api.security.InvalidGroupMembershipException;
import org.labkey.api.security.MemberType;
import org.labkey.api.security.MutableSecurityPolicy;
import org.labkey.api.security.RoleAssignment;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.SecurityPolicyManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.biotrust.security.AbstractBioTrustRole;
import org.labkey.biotrust.security.BillingContactRole;
import org.labkey.biotrust.security.BioTrustRCRole;
import org.labkey.biotrust.security.BioTrustSecurityManager;
import org.labkey.biotrust.security.BudgetApproverRole;
import org.labkey.biotrust.security.FacultyRole;
import org.labkey.biotrust.security.PrimaryStudyContactRole;
import org.labkey.biotrust.security.PrincipalInvestigatorRole;
import org.labkey.biotrust.security.SamplePickupRole;
import org.labkey.biotrust.security.StudyContactRole;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * User: klum
 * Date: 3/4/13
 */
public class BioTrustContactsManager
{
    private static final BioTrustContactsManager _instance = new BioTrustContactsManager();
    private static final Logger _log = Logger.getLogger(BioTrustContactsManager.class);

    private BioTrustContactsManager()
    {
    }

    public static BioTrustContactsManager get()
    {
        return _instance;
    }

    public List<RoleAssignment> getRoleAssignments(Container c)
    {
        SecurityPolicy policy = SecurityPolicyManager.getPolicy(c);
        List<RoleAssignment> assignments = new ArrayList<>();

        // return only the assignments for contact related biotrust roles
        for (RoleAssignment assignment : policy.getAssignments())
        {
            Role role = assignment.getRole();
            if (AbstractBioTrustRole.class.isAssignableFrom(role.getClass()) && ((AbstractBioTrustRole)role).isContactRole())
            {
                assignments.add(assignment);
            }
        }
        return assignments;
    }

    /**
     * Returns whether the user principal has a contact related biotrust role
     */
    public boolean hasBioTrustContactRole(Container c, User user)
    {
        SecurityPolicy policy = SecurityPolicyManager.getPolicy(c);
        for (Role role : policy.getEffectiveRoles(user))
        {
            if (AbstractBioTrustRole.class.isAssignableFrom(role.getClass()))
            {
                if (((AbstractBioTrustRole)role).isContactRole())
                    return true;
            }
        }
        return false;
    }

    public void addRoleAssignment(Container c, User user, Role role)
    {
        SecurityPolicy existingPolicy = SecurityPolicyManager.getPolicy(c);
        MutableSecurityPolicy policy = new MutableSecurityPolicy(c, existingPolicy);

        // the user could be null if the study contact or sample pickup contact is set to "None" (i.e. userid -1)
        if (user != null)
        {
            addUserRoleAssignement(c, user, policy, role);
            SecurityPolicyManager.savePolicy(policy);
        }
    }

    public void updateRoleAssignments(Container c, User user, List<Role> roles, boolean removeAll)
    {
        SecurityPolicy existingPolicy = SecurityPolicyManager.getPolicy(c);
        MutableSecurityPolicy policy = new MutableSecurityPolicy(c, existingPolicy);

        // only clear biotrust roles, unless otherwise specified
        if (!removeAll)
        {
            for (Role assignedRole : policy.getAssignedRoles(user))
            {
                if (!AbstractBioTrustRole.class.isAssignableFrom(assignedRole.getClass()))
                    roles.add(assignedRole);
            }
        }

        policy.clearAssignedRoles(user);
        for (Role role : roles)
        {
            addUserRoleAssignement(c, user, policy, role);
        }
        SecurityPolicyManager.savePolicy(policy);
    }

    private void addUserRoleAssignement(Container c, User user, MutableSecurityPolicy policy, Role role)
    {
        // rc role assignment is handled indirectly through group membership
        if (role.getClass().equals(BioTrustRCRole.class))
        {
            try {
                Group group = BioTrustSecurityManager.get().getResearchCoordinatorGroup(c);
                SecurityManager.addMember(group, user);
            }
            catch (InvalidGroupMembershipException e)
            {
                _log.warn(e.getMessage());
            }
        }
        else
            policy.addRoleAssignment(user, role);

    }

    /**
     * Returns a list of biotrust roles
     * @param includeProjectRoles true to include project wide roles or just the roles
     *                            relevant to a requestor
     * @return
     */
    public List<Role> getBioTrustRoles(boolean includeProjectRoles)
    {
        List<Role> roles = new ArrayList<>();

        if (includeProjectRoles)
        {
            roles.add(RoleManager.getRole(BioTrustRCRole.class));
            roles.add(RoleManager.getRole(FacultyRole.class));
        }
        roles.add(RoleManager.getRole(PrincipalInvestigatorRole.class));
        roles.add(RoleManager.getRole(PrimaryStudyContactRole.class));
        roles.add(RoleManager.getRole(StudyContactRole.class));
        roles.add(RoleManager.getRole(BillingContactRole.class));
        roles.add(RoleManager.getRole(BudgetApproverRole.class));
        roles.add(RoleManager.getRole(SamplePickupRole.class));

        return roles;
    }

    public Set<User> getUsersWithRole(Container c, Role role, boolean includeProject)
    {
        Set<User> users  = new HashSet<>();
        SecurityPolicy existingPolicy = SecurityPolicyManager.getPolicy(c);

        for (RoleAssignment assignment : existingPolicy.getAssignments())
        {
            if (assignment.getRole().equals(role))
            {
                User user = UserManager.getUser(assignment.getUserId());
                if (user == null)
                {
                    // if it's a group assignment, add all direct members of the group
                    Group group = SecurityManager.getGroup(assignment.getUserId());
                    if (group != null)
                    {
                        users.addAll(org.labkey.api.security.SecurityManager.getGroupMembers(group, MemberType.ACTIVE_USERS));
                    }
                }
                else
                    users.add(user);
            }
        }

        if (includeProject && !c.isProject())
            users.addAll(getUsersWithRole(c.getProject(), role, false));

        return users;
    }
}
