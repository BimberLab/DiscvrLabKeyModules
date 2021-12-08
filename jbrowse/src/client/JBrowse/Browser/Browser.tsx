import React, {useState, useEffect} from 'react'

import {
  createViewState,
  JBrowseLinearGenomeView,
  loadPlugins
} from '@jbrowse/react-linear-genome-view'
import { createTheme } from '@material-ui/core/styles'
import { PluginConstructor } from '@jbrowse/core/Plugin'
import { Ajax, ActionURL } from '@labkey/api'
import LogSession from "./plugins/LogSession/index"
import ExtendedVariantPlugin from "./plugins/ExtendedVariantPlugin/index"
import { makeStyles } from "@material-ui/core/styles"
import "./jbrowse.css"

const refTheme = createTheme()
const blue = '#116596'
const midnight = '#0D233F'
const mandarin = '#FFB11D'
const grey = '#bfbfbf'

const nativePlugins = [ExtendedVariantPlugin, LogSession]

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

function applyUrlParams(json, queryParam) {
    const location = queryParam.get('location')
    if (location) {
        json.location = location;
    }

    const sampleFilters = queryParam.get('sampleFilters')
    if (sampleFilters) {
        const filterTokens = sampleFilters.split(':')
        if (filterTokens.length != 2) {
            console.error('Invalid sample filters: ' + sampleFilters)
        } else {
            const [trackId, sampleIds] = filterTokens
            const sampleList = sampleIds.split(',')
            if (sampleList.length == 0) {
                console.error('No samples in filter: ' + sampleFilters)
            } else {
                let found = false
                for (const track of json.tracks) {
                    if (track.trackId === trackId || track.name === trackId) {
                        track.displays[0].renderer.activeSamples = sampleList.join(',')
                        found = true
                        break
                    }
                }

                if (!found) {
                    console.error('Unable to find matching track for sample filter: ' + sampleFilters)
                }
            }
        }
    }

    let activeTracks = queryParam.get('activeTracks')
    if (activeTracks) {
        activeTracks = activeTracks.split(',')

        //TODO
    }
}

function View(){
    const queryParam = new URLSearchParams(window.location.search);
    const session = queryParam.get('session')

    const [state, setState] = useState(null);
    const [plugins, setPlugins] = useState<PluginConstructor[]>();
    useEffect(() => {
        Ajax.request({
            url: ActionURL.buildURL('jbrowse', 'getSession.api'),
            method: 'GET',
            success: async function(res){
                let jsonRes = JSON.parse(res.response);
                applyUrlParams(jsonRes, queryParam)

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

                const themePrimaryColor = jsonRes.themeLightColor || midnight
                const themeSecondaryColor = jsonRes.themeDarkColor || blue
                delete jsonRes.themeLightColor
                delete jsonRes.themeDarkColor
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
        //TODO: can we make this expand to full page height?
        <div style={{height: "100%"}}>
            <JBrowseLinearGenomeView viewState={state} />
        </div>
    )
}

export default View

