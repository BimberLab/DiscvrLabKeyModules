import React, { useEffect, useState } from 'react';
import { observer } from 'mobx-react';
import { getSession } from '@jbrowse/core/util';
import { RefNameAutocomplete } from '@jbrowse/plugin-linear-genome-view';
import { createViewState } from '@jbrowse/react-linear-genome-view';
import BaseResult from '@jbrowse/core/TextSearch/BaseResults';
import { ActionURL, Ajax } from '@labkey/api';
import "./search.css"

import LogSession from '../Browser/plugins/LogSession/index';
import ExtendedVariantPlugin from '../Browser/plugins/ExtendedVariantPlugin/index';

const nativePlugins = [ExtendedVariantPlugin, LogSession]

const StandaloneSearch = observer(({ sessionId, }: { sessionId: any}) => {
    if (!sessionId){
        return(<p>No session Id provided. Please have you admin use the customize icon to set the session ID for this webpart.</p>)
    }

    const [state, setState] = useState(null);
    const [op, setOption] = useState<BaseResult | undefined>()
    if (op) {
        window.location.href = ActionURL.buildURL("jbrowse", "jbrowse.view", null, {session: sessionId, location: op.getLocation()})
    }

    function generateViewState(genome){
        return createViewState({
            assembly: genome.assembly ?? genome.assemblies,
            tracks: genome.tracks,
            configuration: genome.configuration,
            plugins: nativePlugins,
            location: genome.location,
            defaultSession: genome.defaultSession,
            onChange: genome.onChange
        })
    }

    // Get the LinearGenomeViewModel from the API, providing the session as a parameter
    useEffect(() => {
        Ajax.request({
            url: ActionURL.buildURL('jbrowse', 'getSession.api'),
            method: 'GET',
            success: async function(res){
                let jsonRes = JSON.parse(res.response);
                setState(generateViewState(jsonRes));
            },
            failure: function(res){
                setState("invalid");
                console.log(res);
            },
            params: {session: sessionId}
        });
    }, []);

    // Error handle and then render the component
    if (state === null){
        return (<p>Loading...</p>)
    }
    else if (state == "invalid") {
        return (<p>Error fetching config. See console for more details</p>)
    }

    const { session } = state
    const { view } = session
    const { assemblyNames } = getSession(session)
    if (!assemblyNames.length){
        return (<p>No configured assemblies</p>)
    }

    const selectedRegion = op?.getLocation()

    return (
        <span>
      <RefNameAutocomplete
          model={view}
          assemblyName={assemblyNames.length ? assemblyNames[0] : undefined}
          value={selectedRegion}
          onSelect={option => {
              setOption(option)
          }}
          TextFieldProps={{
              margin: 'normal',
              variant: 'outlined',
              helperText: 'Enter a gene or location',
              style: { margin: 7, minWidth: '175px' },
              InputProps: {
                  style: {
                      padding: 0,
                      height: 32
                  }
              }
          }}
      />
    </span>
    )
})

export default StandaloneSearch
