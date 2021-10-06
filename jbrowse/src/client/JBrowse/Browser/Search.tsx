import React, {useState, useEffect} from 'react'
//import 'fontsource-roboto'
import {
  createViewState,
  JBrowseLinearGenomeView,
  loadPlugins,
} from '@jbrowse/react-linear-genome-view'

import { PluginConstructor } from '@jbrowse/core/Plugin'
import { Ajax, ActionURL } from '@labkey/api'
import MyProjectPlugin from "./plugins/MyProjectPlugin/index"
import LogSession from "./plugins/LogSession/index"
import ExtendedVariantPlugin from "./plugins/ExtendedVariantPlugin/index"
import RefNameAutocompleteWrapper from "./RefNameAutocompleteWrapper"

const nativePlugins = [MyProjectPlugin, ExtendedVariantPlugin, LogSession]

function generateViewState(genome, plugins){
  return createViewState({
      assembly: genome.assembly ?? genome.assemblies,
      tracks: genome.tracks,
      configuration: genome.configuration,
      plugins: plugins.concat(nativePlugins),
      location: genome.location,
      defaultSession: genome.defaultSession,
      onChange: genome.onChange
  })
}

function Search(){
    // Grab session + location information from URL params
    const queryParam = new URLSearchParams(window.location.search);
    const session = queryParam.get('session')
    const location = queryParam.get('location')

    const [state, setState] = useState(null);
    const [plugins, setPlugins] = useState<PluginConstructor[]>();

    // Get the LinearGenomeViewModel from the API, providing the session as a parameter
    useEffect(() => {
        Ajax.request({
            url: ActionURL.buildURL('jbrowse', 'getSession.api'),
            method: 'GET',
            success: async function(res){
                let jsonRes = JSON.parse(res.response);
                if (location) {
                    jsonRes.location = location;
                }

                var loadedPlugins = null
                if (jsonRes.plugins != null){
                    try {
                        loadedPlugins = await loadPlugins(jsonRes.plugins);
                    } catch (error) {
                        console.error("Error: ", error)
                    }
                    setPlugins(loadedPlugins);
                } else {
                    loadedPlugins = []
                }
                setState(generateViewState(jsonRes, loadedPlugins));
            },
            failure: function(res){
                setState("invalid");
                console.log(res);
            },
            params: {session: session}
        });
    }, []);

    // Error handle and then render the component
    if(session === null){
        return(<p>Error - no session provided.</p>)
    }
    else if (state === null){
        return (<p>Loading...</p>)
    }
    else if (state == "invalid") {
        return (<p>Error fetching config. See console for more details</p>)
    }
    return (
        <RefNameAutocompleteWrapper viewState={state} sessionParam={session}/>
    )
}

export default Search

