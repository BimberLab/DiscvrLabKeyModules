import React, {useState, useEffect} from 'react'

import {
  createViewState,
  JBrowseLinearGenomeView,
  loadPlugins
} from '@jbrowse/react-linear-genome-view'
import { createTheme } from '@material-ui/core/styles'
import { PluginConstructor } from '@jbrowse/core/Plugin'
import { Ajax, ActionURL } from '@labkey/api'
import MyProjectPlugin from "./plugins/MyProjectPlugin/index"
import LogSession from "./plugins/LogSession/index"
import ExtendedVariantPlugin from "./plugins/ExtendedVariantPlugin/index"
import { makeStyles } from "@material-ui/core/styles"
import "./jbrowse.css"

const refTheme = createTheme()
const blue = '#116596'
const midnight = '#0D233F'
const mandarin = '#FFB11D'
const grey = '#bfbfbf'

const nativePlugins = [MyProjectPlugin, ExtendedVariantPlugin, LogSession]

const useStyles = makeStyles({
    labkeyOverrides: {
        borderStyle: "none; !important",
        fontSize: "14px"
    }
})

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
    const classes = useStyles()

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

                const themePrimaryColor = jsonRes.themePrimaryColor || midnight
                const themeSecondaryColor = jsonRes.themeSecondaryColor || blue
                delete jsonRes.themePrimaryColor
                delete jsonRes.themeSecondaryColor
                jsonRes.configuration = {
                    "theme": {
                        "palette": {
                            primary: {main: themeSecondaryColor},
                            secondary: {main: themePrimaryColor},
                            tertiary: refTheme.palette.augmentColor({main: grey}),
                            quaternary: refTheme.palette.augmentColor({main: mandarin}),
                        }
                    }
                }

                setState(generateViewState(jsonRes, loadedPlugins));
            },
            failure: function(res){
                //TODO: better, consistent error handling
                setState("invalid");
                console.log(res);
            },
            params: {session: session}
        });
    }, []);

    if (session === null){
        return(<p>Error - no session provided.</p>)
    }
    else if (state === null){
        return (<p>Loading...</p>)
    }
    else if (state == "invalid") {
        return (<p>Error fetching config. See console for more details</p>)
    }
    return (
        <div className="jbrowse-app">
            <JBrowseLinearGenomeView viewState={state} />
        </div>
    )
}

export default View

