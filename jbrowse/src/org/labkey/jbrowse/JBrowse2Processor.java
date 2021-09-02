package org.labkey.jbrowse;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.files.FileContentService;
import org.labkey.api.reader.Readers;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.List;

public class JBrowse2Processor
{
    private static final Logger _log = LogManager.getLogger(JBrowse2Processor.class);
    private Logger _customLogger = null;

    public JBrowse2Processor(@Nullable Logger log)
    {
        _customLogger = log;
    }

    private Logger getLogger()
    {
        return _customLogger == null ? _log : _customLogger;
    }

    public static File getBaseDir(Container c)
    {
        return getBaseDir(c, true);
    }

    @Nullable
    public static File getBaseDir(Container c, boolean doCreate)
    {
        FileContentService fileService = FileContentService.get();
        File fileRoot = fileService == null ? null : fileService.getFileRoot(c, FileContentService.ContentType.files);
        if (fileRoot == null || !fileRoot.exists())
        {
            return null;
        }

        File jbrowseDir = new File(fileRoot, ".jbrowse2");
        if (!jbrowseDir.exists())
        {
            if (!doCreate)
            {
                return null;
            }

            jbrowseDir.mkdirs();
        }

        return jbrowseDir;
    }

}
