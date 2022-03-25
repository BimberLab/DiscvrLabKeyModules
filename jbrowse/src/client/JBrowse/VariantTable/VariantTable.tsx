import React, { useEffect, useState } from 'react';
import { observer } from 'mobx-react';
import { getEnv } from 'mobx-state-tree'
import { createViewState } from '@jbrowse/react-linear-genome-view';
import { ActionURL, Ajax } from '@labkey/api';
import { parseLocString } from '@jbrowse/core/util'
import LogSession from '../Browser/plugins/LogSession/index';
import ExtendedVariantPlugin from '../Browser/plugins/ExtendedVariantPlugin/index';
import VariantTableWidget from './components/VariantTableWidget';

const nativePlugins = [ExtendedVariantPlugin, LogSession]

function VariantTable() {
    const queryParam = new URLSearchParams(window.location.search);
    const sessionId = queryParam.get('session') || queryParam.get('database')

    if (!sessionId){
        return(<p>No session Id provided. Please have you admin use the customize icon to set the session ID for this webpart.</p>)
    }

    const locString = queryParam.get('location') || queryParam.get('loc')
    if (!locString) {
        return(<p>Must provide the location to load</p>)
    }

    const trackId = queryParam.get('trackId')
    if (!trackId) {
        return(<p>Must provide the track Id</p>)
    }

    const [view, setView] = useState(null);
    const [parsedLocString, setParsedLocString] = useState(null);
    const [assemblyNames, setAssemblyNames] = useState(null);
    const [assembly, setAssembly] = useState(null);
    const [pluginManager, setPluginManager] = useState(null);

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
                let jsonRes = JSON.parse(res.response)
                const viewState = generateViewState(jsonRes)
                const { session } = viewState
                const { pluginManager } = getEnv(viewState)
                const { view } = session
                const { assemblyNames, assemblyManager} = session

                const assembly = await assemblyManager.waitForAssembly(assemblyNames[0])
                setAssembly(assembly)
                setAssemblyNames(assemblyNames)
                setView(view)
                setPluginManager(pluginManager)

                const isValidRefNameForAssembly = function(refName: string, assemblyName?: string) {
                    return assemblyManager.isValidRefName(refName, assemblyNames[0])
                }

                const parsedLocString = parseLocString(locString, isValidRefNameForAssembly)
                setParsedLocString(parsedLocString)
            },
            failure: function(res){
                setView("invalid")
                console.log(res);
            },
            params: {session: sessionId}
        });
    }, []);

    // Error handle and then render the component
    if (view === null){
        return (<p>Loading...</p>)
    }
    else if (view === "invalid") {
        return (<p>Error fetching config. See console for more details</p>)
    }

    if (!assemblyNames.length){
        return (<p>No configured assemblies</p>)
    }

    return (
        <div style={{height: "90vh"}}>
            <VariantTableWidget view={view} trackId={trackId} parsedLocString={parsedLocString} assembly={assembly} sessionId={sessionId} pluginManager={pluginManager}/>
        </div>
    )
}

export default observer(VariantTable)