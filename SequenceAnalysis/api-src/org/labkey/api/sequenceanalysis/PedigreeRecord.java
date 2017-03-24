package org.labkey.api.sequenceanalysis;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by bimber on 3/15/2017.
 */
public class PedigreeRecord
{
    String subjectName;
    String father;
    String mother;
    String gender;

    public PedigreeRecord()
    {

    }

    /**
     * returns the first order relatives present in the passed list.
     */
    public Set<String> getRelativesPresent(Collection<PedigreeRecord> animals)
    {
        Set<String> ret = new HashSet<>();
        for (PedigreeRecord potentialRelative : animals)
        {
            if (isParentOf(potentialRelative) || isChildOf(potentialRelative))
            {
                ret.add(potentialRelative.subjectName);
            }
        }

        return ret;
    }

    public boolean isParentOf(PedigreeRecord potentialOffspring)
    {
        return subjectName.equals(potentialOffspring.father) || subjectName.equals(potentialOffspring.mother);
    }

    public boolean isChildOf(PedigreeRecord potentialParent)
    {
        if (father != null && father.equals(potentialParent.subjectName))
        {
            return true;
        }
        else if (mother != null && mother.equals(potentialParent.subjectName))
        {
            return true;
        }

        return false;
    }

    public String getSubjectName()
    {
        return subjectName;
    }

    public void setSubjectName(String subjectName)
    {
        this.subjectName = subjectName;
    }

    public String getFather()
    {
        return father;
    }

    public void setFather(String father)
    {
        this.father = father;
    }

    public String getMother()
    {
        return mother;
    }

    public void setMother(String mother)
    {
        this.mother = mother;
    }

    public String getGender()
    {
        return gender;
    }

    public void setGender(String gender)
    {
        this.gender = gender;
    }
}
