import React, { useState } from 'react';
import BaseResult from '@jbrowse/core/TextSearch/BaseResults';
import { navigateToBrowser, navigateToTable } from '../../utils';
import { SearchType } from '@jbrowse/core/data_adapters/BaseAdapter';
import { RefNameAutocomplete } from '@jbrowse/plugin-linear-genome-view';

export default function StandaloneSearchComponent(props: {session: any, trackId: string, selectedRegion: string, assemblyName: string, tableUrl: boolean}) {
    const { session, trackId, selectedRegion, assemblyName, tableUrl } = props
    const [op, setOption] = useState<BaseResult | undefined>()
    const { view } = session
    const { textSearchManager, assemblyManager } = session
    const { rankSearchResults } = view

    if (op && !tableUrl) {
        navigateToBrowser(view.id, op.getLocation())
    } else if (op && tableUrl) {
        navigateToTable(view.id, op.getLocation(), trackId)
    }

    const assembly = assemblyManager.get(assemblyName)
    const searchScope = view.searchScope(assemblyName)
    const effectiveSelectedRegion = op?.getLocation() || selectedRegion

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

        const textSearchResults = await textSearchManager?.search({
            queryString: query,
            searchType,
        }, searchScope, rankSearchResults)

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
      <span style={tableUrl ? {display: 'inline-block', marginRight: '14px'} : {}}>
      <RefNameAutocomplete
          model={view}
          assemblyName={assemblyName ?? undefined}
          fetchResults={fetchResults}
          value={effectiveSelectedRegion}
          onSelect={option => {
              setOption(option)
          }}
          TextFieldProps={{
              margin: 'normal',
              variant: 'outlined',
              helperText: tableUrl ? undefined : 'Enter a gene or location',
              style: { margin: 7, minWidth: '175px' },
              InputProps: {
                  style: {
                      paddingBottom: 0,
                      height: tableUrl ? 45 : 32
                  }
              }
          }}
      />
    </span>
    )
}