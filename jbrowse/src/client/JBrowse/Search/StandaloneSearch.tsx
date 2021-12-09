import React, { useEffect, useState } from 'react';
import { observer } from 'mobx-react';
import { getSession } from '@jbrowse/core/util';
import { RefNameAutocomplete } from '@jbrowse/plugin-linear-genome-view';
import { createViewState } from '@jbrowse/react-linear-genome-view';
import BaseResult from '@jbrowse/core/TextSearch/BaseResults';
import { ActionURL, Ajax } from '@labkey/api';
import './search.css';
import { SearchType } from '@jbrowse/core/data_adapters/BaseAdapter';

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
    const { textSearchManager, assemblyManager } = session
    const { rankSearchResults } = view

    const { assemblyNames } = getSession(session)
    if (!assemblyNames.length){
        return (<p>No configured assemblies</p>)
    }

    const assemblyName = assemblyNames[0]
    const assembly = assemblyManager.get(assemblyName)
    const searchScope = view.searchScope(assemblyName)
    const selectedRegion = op?.getLocation()

    // TODO: can we avoid this duplication?
    function dedupe(
        results: BaseResult[] = [],
        cb: (result: BaseResult) => string,
    ) {
        return results.filter(
            (elt, idx, self) => idx === self.findIndex(t => cb(t) === cb(elt)),
        )
    }

    // TODO: can we avoid this duplication?
    async function fetchResults(query: string, searchType?: SearchType) {
        if (!textSearchManager) {
            console.error('No text search manager')
        }

        const textSearchResults = await textSearchManager?.search(
            {
                queryString: query,
                searchType,
            },
            searchScope,
            rankSearchResults,
        )

        const refNameResults = assembly?.allRefNames
            ?.filter(refName => refName.startsWith(query))
            .map(r => new BaseResult({ label: r }))
            .slice(0, 10)

        return dedupe(
            [...(refNameResults || []), ...(textSearchResults || [])],
            elt => elt.getId(),
        )
    }

    return (
        <span>
      <RefNameAutocomplete
          model={view}
          assemblyName={assemblyName ?? undefined}
          fetchResults={fetchResults}
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
