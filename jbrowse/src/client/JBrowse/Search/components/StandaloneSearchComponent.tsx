import React from 'react';
import { RefNameAutocomplete } from '@jbrowse/plugin-linear-genome-view';
import { fetchResults } from '@jbrowse/plugin-linear-genome-view/esm/LinearGenomeView/components/util';
import { ParsedLocString, parseLocString } from '@jbrowse/core/util';

export default function StandaloneSearchComponent(props: { session: any, selectedRegion: string, assemblyName: string, onSelect: (string) => void, forVariantTable?: boolean}) {
    const { session, selectedRegion, assemblyName, onSelect, forVariantTable } = props

    const { view } = session
    const { textSearchManager, assemblyManager } = session
    const { rankSearchResults } = view

    const assembly = assemblyManager.get(assemblyName)
    const searchScope = view.searchScope(assemblyName)

    const doFetch = (queryString: string) => {
        return fetchResults( { queryString, assembly, textSearchManager, rankSearchResults, searchScope })
    }

    const componentAssemblyName = assemblyName
    const isValidRefNameForAssembly = function(refName: string, assemblyName?: string) {
        return assemblyManager.isValidRefName(refName, assemblyName ?? componentAssemblyName)
    }

    return (
        <span style={forVariantTable ? {display: 'inline-block', marginRight: '14px'} : {}}>
        <RefNameAutocomplete
          model={view}
          assemblyName={assemblyName ?? undefined}
          fetchResults={doFetch}
          value={selectedRegion}
          onSelect={option => {
              let parsedLocString: ParsedLocString
              if (option.getLocation()) {
                  parsedLocString = parseLocString(option.getLocation(), isValidRefNameForAssembly)
              }
              else if (option.results?.length) {
                  let contigs = []
                  let minVal = -1
                  let maxVal = -1

                  console.log(option)

                  option.results.forEach(r => {
                      if (r.getLocation()) {
                          const l = parseLocString(r.getLocation(), isValidRefNameForAssembly)
                          if (!l) {
                              alert('Invalid location: ' + option.label)
                              console.error('Invalid location: ' + option.label)
                              return
                          }

                          // ParsedLocation.start is 0-based
                          let start = l.start ?? -1
                          let end = l.end ?? -1
                          if (start === -1 || end === -1) {
                            alert('Location lacks a start or end, cannot use: ' + r.label)
                            return false
                          }

                          contigs.push(l.refName)

                          if (minVal === -1 || minVal > start) {
                              minVal = start
                          }

                          if (maxVal === -1 || maxVal < end) {
                              maxVal = end
                          }
                      }
                  })

                  if (contigs.length !== 1) {
                      alert('Invalid location: ' + selectedRegion)
                      console.error("Invalid location after parse: " + selectedRegion)
                      return null
                  }
                  else {
                      return(contigs[0] + ":" + (minVal + 1) + "-" + maxVal)
                  }
              }
              else if (option.label) {
                  parsedLocString = parseLocString(option.label, isValidRefNameForAssembly)

              }

              if (parsedLocString) {
                  let start = parsedLocString.start ?? -1
                  let end = parsedLocString.end ?? -1
                  if (start === -1 || end === -1) {
                      alert('Location lacks a start or end, cannot use: ' + option.label)
                      return null
                  }

                  // ParsedLocation.start is 0-based
                  onSelect(parsedLocString.refName + ":" + (1 + parsedLocString.start) + ".." + parsedLocString.end)
              }
              else {
                alert('No location found for: ' + option.label)
              }
          }}
          TextFieldProps={{
              margin: 'normal',
              variant: 'outlined',
              helperText: forVariantTable ? undefined : 'Enter a gene or location',
              style: { margin: 7, minWidth: '175px' },
              InputProps: {
                  style: {
                      paddingBottom: 0,
                      height: forVariantTable ? 45 : 32
                  }
              }
          }}
      />
    </span>
    )
}