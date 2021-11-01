import React, { useEffect, useState } from 'react';
import { observer } from 'mobx-react'
import { getSession } from '@jbrowse/core/util'
import { RefNameAutocomplete } from '@jbrowse/plugin-linear-genome-view'
import { createViewState, loadPlugins, ViewModel } from '@jbrowse/react-linear-genome-view';
import BaseResult from '@jbrowse/core/TextSearch/BaseResults'
import { ActionURL, Ajax } from '@labkey/api';

const RefNameAutocompleteWrapper = observer(({ sessionId, }: { sessionId: any}) => {
    if (sessionId === null){
        return(<p>Error - no session ID provided.</p>)
    }

    const [state, setState] = useState(null);

    function navigate() {
        if (op) {
            window.location.href = ActionURL.buildURL("jbrowse", "jbrowse.view", null, {session: sessionId, location: op.getLocation()})
        }
    }

    function generateViewState(genome){
        return createViewState({
            assembly: genome.assembly ?? genome.assemblies,
            tracks: genome.tracks,
            configuration: genome.configuration,
            plugins: [],
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
            params: {session: sessionId, forSearch: true}
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
    const { assemblyNames } = getSession(session)
    const [selectedAsm, setSelectedAsm] = useState(assemblyNames[0])
    const [op, setOption] = useState<BaseResult | undefined>()

    const selectedRegion = op?.getLocation()
    const message = !assemblyNames.length ? 'No configured assemblies' : ''

    return (
        <span>
      <RefNameAutocomplete
          model={session}
          assemblyName={message ? undefined : selectedAsm}
          value={selectedRegion}
          onSelect={option => {
              setOption(option)
          }}
          TextFieldProps={{
              margin: 'normal',
              variant: 'outlined',
              helperText: 'Enter a sequence or location',
          }}
      />

      <button onClick={navigate}>
          Open
      </button>
    </span>
    )
})

export default RefNameAutocompleteWrapper
