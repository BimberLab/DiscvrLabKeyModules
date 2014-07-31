package org.labkey.sequenceanalysis.api.run;

/**
 * This is intended to manage the command line formatting needed by a tool
 */
public interface ToolPreferences
{
    public String getArgPrefix();

    public String getArgSeparator();

    public String getQuoteChar();
}
