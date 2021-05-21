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

const theme = createJBrowseTheme()



function generateViewState(genome){
/* TODO - Fix plugin functionality
  const [plugins, setPlugins] = useState<PluginConstructor[]>()

  useEffect(() => {
    async function getPlugins() {
      const loadedPlugins = await loadPlugins(genome.plugins)
      setPlugins(loadedPlugins)
    }
    getPlugins()
  }, [setPlugins])
*/
  return createViewState({
      assembly: genome.assembly ?? genome.assemblies,
      tracks: genome.tracks,
      configuration: genome.configuration,
      //plugins: plugins,
      location: genome.location,
      defaultSession: genome.defaultSession,
      onChange: genome.onChange
  })

}

function View(){
    const queryParam = new URLSearchParams(window.location.search);
    const session = queryParam.get('session')

    const [state, setState] = useState(null);
    useEffect(() => {
        Ajax.request({
            url: ActionURL.buildURL('jbrowse', 'getSession.api'),
            method: 'GET',
            success: function(res){
                setState(generateViewState(JSON.parse(res.response)));
                console.log(res);
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
      <ThemeProvider theme={theme}>
          <JBrowseLinearGenomeView viewState={state} />
      </ThemeProvider>
    )
}

export default View
