package org.labkey.GeneticsCore;

import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.action.ApiAction;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.security.CSRF;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.springframework.validation.BindException;

import java.lang.String;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: bimber
 * Date: 7/1/13
 * Time: 11:59 AM
 */
public class GeneticsCoreController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(GeneticsCoreController.class);
    private static final Logger _log = Logger.getLogger(GeneticsCoreController.class);

    public GeneticsCoreController()
    {
        setActionResolver(_actionResolver);
    }

    @RequiresPermission(ReadPermission.class)
    @CSRF
    public class GetNavItemsAction extends ApiAction<Object>
    {
        public ApiResponse execute(Object form, BindException errors)
        {
            Map<String, Object> resultProperties = new HashMap<>();

            resultProperties.put("collaborations", getSection("/Public/Collaborations"));
            resultProperties.put("internal", getSection("/Internal"));
            resultProperties.put("labs", getSection("/Labs"));

            //for now, public is hard coded
            List<JSONObject> publicJson = new ArrayList<>();
            Container publicContainer = ContainerManager.getForPath("/Public");
            if (publicContainer != null)
            {
                JSONObject json = new JSONObject();
                json.put("name", "Front Page");
                json.put("path", ContainerManager.getHomeContainer().getPath());
                json.put("url", ContainerManager.getHomeContainer().getStartURL(getUser()).toString());
                json.put("canRead", ContainerManager.getHomeContainer().hasPermission(getUser(), ReadPermission.class));
                publicJson.add(json);

                json = new JSONObject();
                json.put("name", "Overview / Tutorials");
                json.put("path", publicContainer.getPath());
                json.put("url", publicContainer.getStartURL(getUser()).toString());
                json.put("canRead", publicContainer.hasPermission(getUser(), ReadPermission.class));
                publicJson.add(json);

                json = new JSONObject();
                Container publicBlast = ContainerManager.getForPath("/Public/PublicBLAST");
                if (publicBlast != null)
                {
                    json.put("name", "BLAST");
                    json.put("path", publicBlast.getPath());
                    json.put("url", new ActionURL("blast", "blast", publicBlast).toString());
                    json.put("canRead", publicBlast.hasPermission(getUser(), ReadPermission.class));
                    publicJson.add(json);
                }

                json = new JSONObject();
                json.put("name", "Genome Browser");
                json.put("path", publicContainer.getPath());
                json.put("url", new ActionURL("wiki", "page", publicContainer).toString() + "name=Genome Browser Instructions");
                json.put("canRead", publicContainer.hasPermission(getUser(), ReadPermission.class));
                publicJson.add(json);

            }

            resultProperties.put("public", publicJson);
            resultProperties.put("success", true);

            return new ApiSimpleResponse(resultProperties);
        }

        private List<JSONObject> getSection(String path)
        {
            List<JSONObject> ret = new ArrayList<>();
            Container mainContainer = ContainerManager.getForPath(path);
            if (mainContainer != null)
            {
                for (Container c : mainContainer.getChildren())
                {
                    //NOTE: unlike EHR, omit children if the current user cannot read them
                    if (!c.hasPermission(getUser(), ReadPermission.class))
                    {
                        continue;
                    }

                    JSONObject json = new JSONObject();
                    json.put("name", c.getName());
                    json.put("title", c.getTitle());
                    json.put("path", c.getPath());
                    json.put("url", c.getStartURL(getUser()));
                    json.put("canRead", c.hasPermission(getUser(), ReadPermission.class));
                    ret.add(json);
                }
            }

            return ret;
        }
    }

    @RequiresPermission(UpdatePermission.class)
    @CSRF
    public class CacheAnalysesAction extends ApiAction<CacheAnalysesForm>
    {
        public ApiResponse execute(CacheAnalysesForm form, BindException errors)
        {
            Map<String, Object> resultProperties = new HashMap<>();

            //first verify permission to delete
            if (form.getAlleleNames() != null)
            {
                try
                {
                    ExpProtocol protocol = ExperimentService.get().getExpProtocol(form.getProtocolId());
                    if (protocol == null)
                    {
                        errors.reject(ERROR_MSG, "Unknown protocol: " + form.getProtocolId());
                        return null;
                    }

                    Pair<List<Integer>, List<Integer>> ret = GeneticsCoreManager.get().cacheAnalyses(getViewContext(), protocol, form.getAlleleNames());
                    resultProperties.put("runsCreated", ret.first);
                    resultProperties.put("runsDeleted", ret.second);
                }
                catch (IllegalArgumentException e)
                {
                    errors.reject(ERROR_MSG, e.getMessage());
                    return null;
                }
            }
            else
            {
                errors.reject(ERROR_MSG, "No alleles provided");
                return null;
            }

            resultProperties.put("success", true);

            return new ApiSimpleResponse(resultProperties);
        }
    }

    public static class CacheAnalysesForm
    {
        private String[] _alleleNames;
        private String _json;
        private int _protocolId;

        public String[] getAlleleNames()
        {
            return _alleleNames;
        }

        public void setAlleleNames(String[] alleleNames)
        {
            _alleleNames = alleleNames;
        }

        public int getProtocolId()
        {
            return _protocolId;
        }

        public void setProtocolId(int protocolId)
        {
            _protocolId = protocolId;
        }

        public String getJson()
        {
            return _json;
        }

        public void setJson(String json)
        {
            _json = json;
        }
    }


    @RequiresPermission(UpdatePermission.class)
    @CSRF
    public class CacheHaplotypesAction extends ApiAction<CacheAnalysesForm>
    {
        public ApiResponse execute(CacheAnalysesForm form, BindException errors)
        {
            Map<String, Object> resultProperties = new HashMap<>();

            //first verify permission to delete
            if (form.getJson() != null)
            {
                try
                {
                    ExpProtocol protocol = ExperimentService.get().getExpProtocol(form.getProtocolId());
                    if (protocol == null)
                    {
                        errors.reject(ERROR_MSG, "Unknown protocol: " + form.getProtocolId());
                        return null;
                    }

                    Pair<List<Integer>, List<Integer>> ret = GeneticsCoreManager.get().cacheHaplotypes(getViewContext(), protocol, new JSONArray(form.getJson()));
                    resultProperties.put("runsCreated", ret.first);
                    resultProperties.put("runsDeleted", ret.second);
                }
                catch (IllegalArgumentException e)
                {
                    errors.reject(ERROR_MSG, e.getMessage());
                    return null;
                }
            }
            else
            {
                errors.reject(ERROR_MSG, "No data provided");
                return null;
            }

            resultProperties.put("success", true);

            return new ApiSimpleResponse(resultProperties);
        }
    }
}
