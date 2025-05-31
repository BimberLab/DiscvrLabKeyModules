package org.labkey.studies;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.action.ConfirmAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.TableInfo;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineUrls;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.DuplicateKeyException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.reader.DataLoader;
import org.labkey.api.reader.TabLoader;
import org.labkey.api.resource.Resource;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.studies.StudiesService;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Path;
import org.labkey.api.util.URLHelper;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.view.HtmlView;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public class StudiesController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(StudiesController.class);
    public static final String NAME = "studies";

    private static final Logger _log = LogHelper.getLogger(StudiesController.class, "Messages from StudiesController");

    public StudiesController()
    {
        setActionResolver(_actionResolver);
    }

    @RequiresPermission(AdminPermission.class)
    public static class ImportStudyAction extends ConfirmAction<Object>
    {
        @Override
        public ModelAndView getConfirmView(Object o, BindException errors) throws Exception
        {
            setTitle("Import Study");

            return new HtmlView(HtmlString.unsafe("This will import the default study in this folder, and truncate/load ancillary data. Do you want to continue?"));
        }

        @Override
        public boolean handlePost(Object o, BindException errors) throws Exception
        {
            StudiesService.get().importFolderDefinition(getContainer(), getUser(), ModuleLoader.getInstance().getModule(StudiesModule.NAME), new Path("referenceStudy"));

            StudiesModule m = ModuleLoader.getInstance().getModule(StudiesModule.class);
            loadTsv(m.getModuleResource("data/lookup_sets.tsv"), StudiesSchema.NAME);

            Resource r = m.getModuleResource("data");
            r.list().forEach(tsv -> {
                if ("lookup_sets.tsv".equals(tsv.getName()))
                {
                    return;
                }

                String schemaName = switch (tsv.getName())
                {
                    case "reports.tsv" -> "laboratory";
                    case "species.tsv" -> "laboratory";
                    default -> StudiesSchema.NAME;
                };

                loadTsv(tsv, schemaName);
            });

            return true;
        }

        private void loadTsv(Resource tsv, String schemaName)
        {
            try (DataLoader loader = DataLoader.get().createLoader(tsv, true, null, TabLoader.TSV_FILE_TYPE))
            {
                TableInfo ti = QueryService.get().getUserSchema(getUser(), getContainer(), schemaName).getTable(FileUtil.getBaseName(tsv.getName()));
                if (ti == null)
                {
                    throw new IllegalStateException("Missing table: " + tsv.getName());
                }

                List<Map<String, Object>> rows = loader.load();

                QueryUpdateService qus = ti.getUpdateService();
                qus.setBulkLoad(true);

                qus.truncateRows(getUser(), getContainer(), null, null);
                qus.insertRows(getUser(), getContainer(), rows, new BatchValidationException(), null, null);
            }
            catch (IOException | SQLException | BatchValidationException | QueryUpdateServiceException | DuplicateKeyException e)
            {
                _log.error("Error populating TSV", e);

                throw new RuntimeException(e);
            }
        }

        @Override
        public void validateCommand(Object o, Errors errors)
        {

        }

        @NotNull
        @Override
        public URLHelper getSuccessURL(Object o)
        {
            return PageFlowUtil.urlProvider(PipelineUrls.class).urlBegin(getContainer());
        }
    }
}
