import React from 'react';
import { RefNameAutocomplete } from '@jbrowse/plugin-linear-genome-view';
import { fetchResults } from '@jbrowse/plugin-linear-genome-view/esm/LinearGenomeView/components/util';
import { ParsedLocString, parseLocString } from '@jbrowse/core/util';

export default function StandaloneSearchComponent(props: { session: any, selectedRegion: string, assemblyName: string, onSelect: (queryString: string, parsedLocString: ParsedLocString, errors?: string[]) => void, forVariantTable?: boolean, fieldMinWidth?: number}) {
    const { session, selectedRegion, assemblyName, onSelect, forVariantTable, fieldMinWidth = 175 } = props

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
            minWidth={fieldMinWidth}
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

                    var errorMsgs = []
                    option.results.forEach(r => {
                        if (r.getLocation()) {
                            const l = parseLocString(r.getLocation(), isValidRefNameForAssembly)
                            if (!l) {
                                errorMsgs.push('Invalid location: ' + option.label)
                                return false
                            }

                            // ParsedLocation.start is 0-based
                            let start = l.start ?? -1
                            let end = l.end ?? -1
                            if (start === -1 || end === -1) {
                                errorMsgs.push('Location lacks a start or end, cannot use: ' + r.label)
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

                    if (errorMsgs.length) {
                        console.error("Invalid location: " + option.label + ", " + errorMsgs.join(", "))
                        console.log(option)
                        return onSelect(option.label, null, errorMsgs)
                    }

                    var uniqueContigs = contigs.filter((value, index, array) => array.indexOf(value) === index);
                    if (uniqueContigs.length !== 1) {
                        console.error("Invalid location: " + option.label)
                        console.log(option)
                        return onSelect(option.label, null, ['Invalid location: ' + option.label])
                    }
                    else {
                        return onSelect(option.label, {refName: contigs[0], start: minVal, end: maxVal})
                    }
                }
                else if (option.label) {
                    parsedLocString = parseLocString(option.label, isValidRefNameForAssembly)
                }

                if (parsedLocString) {
                    return onSelect(option.label, parsedLocString)
                }
                else {
                    console.error('No location found for: ' + option.label)
                    console.log(option)
                    return onSelect(option.label, null, ['No location found for: ' + option.label])
                }
            }}
            TextFieldProps={{
                margin: 'normal',
                variant: 'outlined',
                helperText: forVariantTable ? undefined : 'Enter a gene or location',
                style: { margin: 7 },
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