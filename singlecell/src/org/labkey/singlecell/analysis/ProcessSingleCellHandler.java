package org.labkey.singlecell.analysis;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.data.Container;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.security.User;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.util.FileType;
import org.labkey.api.view.ActionURL;

import java.util.List;

public class ProcessSingleCellHandler extends AbstractSingleCellHandler
{
    private static FileType LOUPE_TYPE = new FileType("cloupe", false);

    @Override
    public String getName()
    {
        return "Single Cell Processing";
    }

    @Override
    public boolean canProcess(SequenceOutputFile f)
    {
        return f.getFile() != null && LOUPE_TYPE.isType(f.getFile());
    }

    @Override
    public SequenceOutputProcessor getProcessor()
    {
        return new Processor(true);
    }
}
