package org.labkey.sequenceanalysis.query;

import org.apache.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.sequenceanalysis.RefNtSequenceModel;
import org.labkey.sequenceanalysis.SequenceAnalysisSchema;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by bimber on 7/27/2014.
 */
public class SequenceTriggerHelper
{
    private Container _container = null;
    private User _user = null;
    private static final Logger _log = Logger.getLogger(SequenceTriggerHelper.class);
    private TableInfo _refNts = null;

    private Map<Integer, String> _sequenceMap = new HashMap<>();

    public SequenceTriggerHelper(int userId, String containerId)
    {
        _user = UserManager.getUser(userId);
        if (_user == null)
            throw new RuntimeException("User does not exist: " + userId);

        _container = ContainerManager.getForId(containerId);
        if (_container == null)
            throw new RuntimeException("Container does not exist: " + containerId);

    }

    private User getUser()
    {
        return _user;
    }

    private Container getContainer()
    {
        return _container;
    }

    public void addSequence(int rowIdx, String sequence)
    {
        if (sequence != null)
        {
            _sequenceMap.put(rowIdx, sequence);
        }
    }

    private TableInfo getRefNts()
    {
        if (_refNts != null)
        {
            return _refNts;
        }

        _refNts = QueryService.get().getUserSchema(getUser(), getContainer(), SequenceAnalysisSchema.SCHEMA_NAME).getTable(SequenceAnalysisSchema.TABLE_REF_NT_SEQUENCES);

        return _refNts;
    }

    public void processSequence(int rowId, String sequence) throws IOException
    {
        if (sequence != null)
        {
            RefNtSequenceModel model = new TableSelector(getRefNts(), new SimpleFilter(FieldKey.fromString("rowid"), rowId), null).getObject(RefNtSequenceModel.class);
            model.createFileForSequence(getUser(), sequence, null);

        }
    }
}
