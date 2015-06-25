package org.labkey.galaxyintegration.pipeline;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.AbstractParameter;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.ProtocolApplicationParameter;
import org.labkey.api.exp.api.DataType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpProtocolAction;
import org.labkey.api.exp.api.ExpProtocolApplication;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.util.GUID;

import java.net.URI;
import java.net.URISyntaxException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by bimber on 6/5/2015.
 */
public class ExpRunCreator
{
    private static final Logger _log = Logger.getLogger(ExpRunCreator.class);

    public ExpRunCreator()
    {

    }

    public ExpRun createRun(RecordedActionSet actionSet, Container c, User u, String runName, String description) throws PipelineJobException, ValidationException
    {
        ExpRun run;
        Set<RecordedAction> actions = new LinkedHashSet<>(actionSet.getActions());

        Map<String, ExpProtocol> protocolCache = new HashMap<>();
        List<String> protocolSequence = new ArrayList<>();
        Map<URI, String> runOutputsWithRoles = new LinkedHashMap<>();
        Map<URI, String> runInputsWithRoles = new HashMap<>();

        runInputsWithRoles.putAll(actionSet.getOtherInputs());

        for (RecordedAction action : actions)
        {
            for (RecordedAction.DataFile dataFile : action.getInputs())
            {
                if (runInputsWithRoles.get(dataFile.getURI()) == null)
                {
                    // For inputs, don't stomp over the role specified the first time a file was used as an input
                    runInputsWithRoles.put(dataFile.getURI(), dataFile.getRole());
                }
            }

            for (RecordedAction.DataFile dataFile : action.getOutputs())
            {
                if (!dataFile.isTransient())
                {
                    // For outputs, want to use the last role that was specified, so always overwrite
                    runOutputsWithRoles.put(dataFile.getURI(), dataFile.getRole());
                }

            }
        }

        // Files count as inputs to the run if they're used by one of the actions and weren't produced by one of
        // the actions.
        for (RecordedAction action : actions)
        {
            for (RecordedAction.DataFile dataFile : action.getOutputs())
            {
                runInputsWithRoles.remove(dataFile.getURI());
            }
        }

        ExpProtocol parentProtocol;
        try (DbScope.Transaction transaction = ExperimentService.get().getSchema().getScope().ensureTransaction(ExperimentService.get().getProtocolImportLock()))
        {
            for (RecordedAction action : actions)
            {
                addProtocol(c, u, protocolCache, action.getName());
                protocolSequence.add(action.getName());
            }

            // Check to make sure that we have a protocol that corresponds with each action
            for (RecordedAction action : actions)
            {
                if (protocolCache.get(action.getName()) == null)
                {
                    throw new IllegalArgumentException("Could not find a matching action declaration for " + action.getName());
                }
            }

            String protocolObjectId = "id" + new GUID().toString();
            Lsid parentProtocolLSID = new Lsid(ExperimentService.get().generateLSID(c, ExpProtocol.class, protocolObjectId));
            parentProtocol = ensureProtocol(c, u, protocolCache, protocolSequence, parentProtocolLSID, runName);

            transaction.commit();
        }

        // Break the protocol insertion and run insertion into two separate transactions
        try (DbScope.Transaction transaction = ExperimentService.get().getSchema().getScope().ensureTransaction())
        {
            run = insertRun(c, u, runName, description, actionSet.getActions(), runOutputsWithRoles, runInputsWithRoles, parentProtocol);

            transaction.commit();
        }

        return run;
    }

    private ExpRun insertRun(Container c, User u, String runName, String description, Set<RecordedAction> actions, Map<URI, String> runOutputsWithRoles, Map<URI, String> runInputsWithRoles, ExpProtocol parentProtocol) throws PipelineJobException, ValidationException
    {
        ExpRun run = ExperimentService.get().createExperimentRun(c, runName);
        run.setProtocol(parentProtocol);
        run.setComments(description);
        run.setJobId(PipelineService.get().getJobId(u, c, new GUID().toString()));
        run.save(u);

        Map<String, ExpProtocolAction> expActionMap = new HashMap<>();
        List<ExpProtocolAction> expActions = parentProtocol.getSteps();
        for (ExpProtocolAction action : expActions)
        {
            expActionMap.put(action.getChildProtocol().getName(), action);
        }

        //Map<URI, ExpData> datas = new LinkedHashMap<>();

        // Set up the inputs to the whole run
        ExpProtocolApplication inputApp = run.addProtocolApplication(u, expActions.get(0), parentProtocol.getApplicationType(), "Run inputs");
        for (Map.Entry<URI, String> runInput : runInputsWithRoles.entrySet())
        {
            URI uri = runInput.getKey();
            String role = runInput.getValue();
            ExpData data = _createdDatas.get(uri);
            if (data != null)
            {
                inputApp.addDataInput(u, data, role);
            }
            else
            {
                _log.error("unable to find created ExpData input matching: " + uri);
                ExpData d = addData(c, u, uri, "input");
                inputApp.addDataInput(u, d, role);
            }
        }

        // Set up the inputs and outputs for the individual actions
        for (RecordedAction action : actions)
        {
            // Look up the step by its name
            ExpProtocolAction step = expActionMap.get(action.getName());

            ExpProtocolApplication app = run.addProtocolApplication(u, step, ExpProtocol.ApplicationType.ProtocolApplication, action.getName());
            app.setStartTime(action.getStartTime());
            app.setEndTime(action.getEndTime());
            app.setRecordCount(action.getRecordCount());

            if (!action.getName().equals(action.getDescription()))
            {
                app.setName(action.getDescription());
                app.save(u);
            }

            // Transfer all the protocol parameters
            for (Map.Entry<RecordedAction.ParameterType, Object> param : action.getParams().entrySet())
            {
                ProtocolApplicationParameter protAppParam = new ProtocolApplicationParameter();
                protAppParam.setProtocolApplicationId(app.getRowId());
                protAppParam.setRunId(run.getRowId());
                RecordedAction.ParameterType paramType = param.getKey();
                protAppParam.setName(paramType.getName());
                protAppParam.setOntologyEntryURI(paramType.getURI());

                protAppParam.setValue(paramType.getType(), param.getValue());

                loadParameter(u, protAppParam, ExperimentService.get().getSchema().getTable("ProtocolApplicationParameter"), FieldKey.fromParts("ProtocolApplicationId"), app.getRowId());
            }

            // If there are any property settings, transfer them here
            for (Map.Entry<PropertyDescriptor, Object> prop : action.getProps().entrySet())
            {
                PropertyDescriptor pd = prop.getKey();
                app.setProperty(u, pd, prop.getValue());
            }

            // Set up the inputs
            Set<Integer> encounteredDatas = new HashSet<>();
            for (RecordedAction.DataFile dd : action.getInputs())
            {
                ExpData data = addData(c, u, dd.getURI(), dd.getRole());
                if (encounteredDatas.contains(data.getRowId()))
                {
                    _log.error("duplicate input file for action: " + action.getName() + ", " + dd.getURI());
                    continue;
                }
                encounteredDatas.add(data.getRowId());
                app.addDataInput(u, data, dd.getRole());
            }

            // Set up the outputs
            encounteredDatas.clear();
            for (RecordedAction.DataFile dd : action.getOutputs())
            {
                ExpData outputData = addData(c, u, dd.getURI(), dd.getRole());
                if (encounteredDatas.contains(outputData.getRowId()))
                {
                    _log.error("duplicate output file for action: " + action.getName() + ", " + dd.getURI());
                    continue;
                }

                if (outputData.getSourceApplication() != null)
                {
                    //TODO
                    //datas.remove(dd.getURI());
                    //datas.remove(outputData.getDataFileURI());
                    //outputData.setDataFileURI(null);
                    //outputData.save(u);

                    //outputData = _createdDatas.get(dd.getURI());
                    //outputData.setSourceApplication(app);
                    //outputData.save(u);
                }
                else
                {
                    outputData.setSourceApplication(app);
                    outputData.save(u);
                }
            }
        }

        if (!runOutputsWithRoles.isEmpty())
        {
            // Set up the outputs for the run
            ExpProtocolApplication outputApp = run.addProtocolApplication(u, expActions.get(expActions.size() - 1), ExpProtocol.ApplicationType.ExperimentRunOutput, "Run outputs");
            for (Map.Entry<URI, String> entry : runOutputsWithRoles.entrySet())
            {
                URI uri = entry.getKey();
                String role = entry.getValue();
                ExpData data = _createdDatas.get(uri);
                if (data != null)
                {
                    outputApp.addDataInput(u, data, role);
                }
                else
                {
                    _log.error("unable to find created ExpData output matching: " + uri);
                }
            }
        }
        return run;
    }

    private Map<URI, ExpData> _createdDatas = new HashMap<>();

    private void loadParameter(User user, AbstractParameter param,
                              TableInfo tiValueTable,
                              FieldKey pkName, int rowId)
    {
        SimpleFilter filter = new SimpleFilter(pkName, rowId);
        filter.addCondition(FieldKey.fromParts("OntologyEntryURI"), param.getOntologyEntryURI());
        Map<String, Object> existingValue = new TableSelector(tiValueTable, filter, null).getMap();

        if (existingValue == null)
        {
            Table.insert(user, tiValueTable, param);
        }
        else
        {
            throw new RuntimeSQLException(new SQLException("Duplicate " + tiValueTable.getSelectName() + " value, filter= " + filter + ". Existing parameter is " + existingValue + ", new value is " + param.getValue()));
        }
    }

    private ExpData addData(Container c, User u, URI originalURI, @Nullable String name) throws PipelineJobException
    {
        if (!_createdDatas.containsKey(originalURI))
        {
            Lsid lsid = new Lsid(ExperimentService.get().generateGuidLSID(c, new DataType("Galaxy")));
            ExpData data = ExperimentService.get().createData(c, name, lsid.toString());
            try
            {
                URI updatedUri = new URI(originalURI.toString() + "/display");
                data.setDataFileURI(updatedUri);

                if (data != null)
                {
                    data.save(u);
                }

                _createdDatas.put(originalURI, data);
            }
            catch (URISyntaxException e)
            {
                throw new PipelineJobException(e);
            }
        }

        return _createdDatas.get(originalURI);
    }

    private ExpProtocol ensureProtocol(Container c, User u, Map<String, ExpProtocol> protocolCache, List<String> protocolSequence, Lsid lsid, String description)
    {
        return createProtocol(c, u, protocolCache, protocolSequence, lsid, description);
    }

    private ExpProtocol createProtocol(Container c, User u, Map<String, ExpProtocol> protocolCache, List<String> protocolSequence, Lsid lsid, String description)
    {
        ExpProtocol parentProtocol;
        parentProtocol = ExperimentService.get().createExpProtocol(c, ExpProtocol.ApplicationType.ExperimentRun, lsid.getObjectId(), lsid.toString());
        parentProtocol.setName(description);
        parentProtocol.save(u);

        int sequence = 1;
        parentProtocol.addStep(u, parentProtocol, sequence++);
        for (String name : protocolSequence)
        {
            parentProtocol.addStep(u, protocolCache.get(name), sequence++);
        }

        Lsid outputLsid = createOutputProtocolLSID(lsid);
        ExpProtocol outputProtocol = ExperimentService.get().createExpProtocol(c, ExpProtocol.ApplicationType.ExperimentRunOutput, outputLsid.getObjectId(), outputLsid.toString());
        outputProtocol.save(u);

        parentProtocol.addStep(u, outputProtocol, sequence++);
        return parentProtocol;
    }

    private Lsid createOutputProtocolLSID(Lsid parentProtocolLSID)
    {
        Lsid result = new Lsid(parentProtocolLSID.toString());
        result.setObjectId(parentProtocolLSID.getObjectId() + ".Output");
        return result;
    }

    private void addProtocol(Container c, User u, Map<String, ExpProtocol> protocols, String name)
    {
        // Check if we've already dealt with this one
        if (!protocols.containsKey(name))
        {
            // Check if it's in the database already
            ExpProtocol protocol = ExperimentService.get().getExpProtocol(c, name);
            if (protocol == null)
            {
                protocol = ExperimentService.get().createExpProtocol(c, ExpProtocol.ApplicationType.ProtocolApplication, name);
                protocol.save(u);
            }
            protocols.put(name, protocol);
        }
        else
        {
            _log.error("existing protocol with name: " + name);
        }
    }
}
