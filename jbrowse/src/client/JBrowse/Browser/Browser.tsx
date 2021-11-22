import React, {useState, useEffect} from 'react'
//import 'fontsource-roboto'
import {
  createViewState,
  createJBrowseTheme,
  JBrowseLinearGenomeView,
  loadPlugins,
  ThemeProvider,
} from '@jbrowse/react-linear-genome-view'
import { PluginConstructor } from '@jbrowse/core/Plugin'
import { Ajax, Utils, ActionURL } from '@labkey/api'
import MyProjectPlugin from "./plugins/MyProjectPlugin/index"
import LogSession from "./plugins/LogSession/index"
import ExtendedVariantPlugin from "./plugins/ExtendedVariantPlugin/index"

const theme = createJBrowseTheme()
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

function View(){
    const queryParam = new URLSearchParams(window.location.search);
    const session = queryParam.get('session')
    const location = queryParam.get('location')

    const [state, setState] = useState(null);
    const [plugins, setPlugins] = useState<PluginConstructor[]>();
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
      <ThemeProvider _theme={theme}>
          <JBrowseLinearGenomeView viewState={state} />
      </ThemeProvider>
    )
}

export default View

