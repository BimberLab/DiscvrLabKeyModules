package org.labkey.sequenceanalysis;

import org.labkey.api.exp.api.DefaultExperimentDataHandler;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.URLHelper;

import java.io.File;
import java.net.URISyntaxException;

public class HtmlExpDataHandler extends DefaultExperimentDataHandler
{
    @Override
    public Priority getPriority(ExpData data)
    {
        if (data == null || data.getFile() == null || !data.getFile().exists())
        {
            return null;
        }

        return "html".equalsIgnoreCase(FileUtil.getExtension(data.getFile())) ? Priority.HIGH : null;
    }

    @Override
    public URLHelper getShowFileURL(ExpData data)
    {
        File f = data.getFile();
        if (f != null && f.exists())
        {
            PipeRoot root = PipelineService.get().getPipelineRootSetting(data.getContainer());
            if (root != null)
            {
                if (root.isUnderRoot(f))
                {
                    String path = root.relativePath(f);
                    path = path.replaceAll("\\\\", "/");
                    try
                    {
                        return new URLHelper(root.getWebdavURL() + path);
                    }
                    catch (URISyntaxException e)
                    {
                        //ignore
                    }
                }
            }
        }

        return super.getShowFileURL(data);
    }
}
