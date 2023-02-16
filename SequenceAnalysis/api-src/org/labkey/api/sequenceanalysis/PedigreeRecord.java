package org.labkey.api.sequenceanalysis;

import org.apache.commons.lang3.StringUtils;

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
    boolean isPlaceholderFather = false;
    boolean isPlaceholderMother = false;

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
        else return mother != null && mother.equals(potentialParent.subjectName);
    }

    public String getSubjectName()
    {
        return subjectName;
    }

    public void setSubjectName(String subjectName)
    {
        if (StringUtils.isEmpty(subjectName))
        {
            throw new IllegalArgumentException("Subject name cannot be null");
        }

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

    public boolean isPlaceholderFather()
    {
        return isPlaceholderFather;
    }

    public void setPlaceholderFather(boolean placeholderFather)
    {
        isPlaceholderFather = placeholderFather;
    }

    public boolean isPlaceholderMother()
    {
        return isPlaceholderMother;
    }

    public void setPlaceholderMother(boolean placeholderMother)
    {
        isPlaceholderMother = placeholderMother;
    }

    public int getTotalParents(boolean includePlaceholder)
    {
        int ret = 0;

        ret += !StringUtils.isEmpty(getFather()) && (includePlaceholder || !isPlaceholderFather()) ? 1 : 0;
        ret += !StringUtils.isEmpty(getMother()) && (includePlaceholder || !isPlaceholderMother()) ? 1 : 0;

        return ret;
    }
}
