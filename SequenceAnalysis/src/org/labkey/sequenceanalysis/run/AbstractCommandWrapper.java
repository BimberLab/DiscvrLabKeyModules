package org.labkey.sequenceanalysis.run;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: bimber
 * Date: 12/14/12
 * Time: 7:29 AM
 *
 * The goal of this class is to convert from pipeline XML into the command line parameters required to
 * run a given command.
 */
abstract public class AbstractCommandWrapper extends AbstractRunner
{
    public AbstractCommandWrapper(Logger logger)
    {
        _logger = logger;
    }

    @NotNull
    protected String getDelimiter()
    {
        return " ";
    }

    @NotNull
    protected String getArgPrefix()
    {
        return "";
    }

    @NotNull
    protected String getQuoteChar()
    {
        return "\"";
    }

    protected List<String> getArgs(Map<String, String> params, Map<String, CommandArgument> commandMap)
    {
        List<String> args = new ArrayList<>();
        for (String key : commandMap.keySet())
        {
            CommandArgument ca = commandMap.get(key);
            if (params.containsKey(key))
            {
                String value = params.get(key);
                args.addAll(ca.getArgs(value));
            }
            else if (ca.isIncludeByDefault())
            {
                String value = ca.getDefaultVal();
                args.addAll(ca.getArgs(value));
            }
            else if (ca.isRequired())
            {
                throw new IllegalArgumentException("Missing required param: " + key);
            }
        }

        return args;
    }

    protected class CommandArgument
    {
        private String _arg;
        private boolean _required = false;
        private String _defaultVal;
        protected boolean _isSwitch = false;

        public CommandArgument(String arg)
        {
            _arg = arg;
        }

        public CommandArgument(String arg, String defaultVal)
        {
            _arg = arg;
            _defaultVal = defaultVal;
        }

        public List<String> getArgs(String value)
        {
            String ret = getArgPrefix() + _arg;
            if (_isSwitch)
            {
                return Collections.singletonList(ret);
            }
            else
            {
                if (value == null)
                    throw new IllegalArgumentException("Missing value for argument " + _arg);

                return Arrays.asList(ret, value);
            }
        }

        public boolean isRequired()
        {
            return _required;
        }

        public void setRequired(boolean required)
        {
            _required = required;
        }

        public boolean isIncludeByDefault()
        {
            return _defaultVal != null;
        }

        public String getDefaultVal()
        {
            return _defaultVal;
        }
    }

    protected class CommandSwitch extends CommandArgument
    {
        private boolean _includeByDefault = false;

        public CommandSwitch(String param, boolean includeByDefault)
        {
            super(param);
            _isSwitch = true;
            _includeByDefault = includeByDefault;
        }

        public List<String> getArgs()
        {
            return super.getArgs(null);
        }

        @Override
        public boolean isIncludeByDefault()
        {
            return _includeByDefault;
        }
    }
}
