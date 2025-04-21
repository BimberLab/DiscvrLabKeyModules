import { isEmptyObject } from 'jquery';
import jexl from 'jexl';
import { createViewState, loadPlugins } from '@jbrowse/react-linear-genome-view2';
import { ActionURL, Ajax } from '@labkey/api';
import {
    getGridNumericOperators,
    GridColDef,
    GridColType,
    GridComparatorFn,
    GridFilterItem,
    GridFilterOperator
} from '@mui/x-data-grid';
import { ParsedLocString, parseLocString } from '@jbrowse/core/util';
import { VcfFeature } from '@jbrowse/plugin-variants';
import { Operator, OperatorKey, OperatorRegistry, Value } from './VariantSearch/operators'

export function arrayMax(array) {
    return Array.isArray(array) ? Math.max(...array) : array
}

jexl.addFunction('arrayMax', (array) => {
    return arrayMax(array)
})

export function passesInfoFilters(feature, filters) {
    if (!filters || !filters.length){
        return true
    }

    for (const filterObj of filters){
        try {
            if (!feature.variant) {
                filterObj.jexlExpression = filterObj.jexlExpression.replace('variant', 'data')
            }

            if (!jexl.evalSync(filterObj.jexlExpression, feature)){
                return false
            }
        } catch (e){
            handleFailure("Error in filter execution: " + e)
        }
    }

    return true
}

export function passesSampleFilters(variant : VcfFeature, sampleIDs){
    if (!sampleIDs || sampleIDs.length === 0) {
        return true
    }

    // Preferentially use pre-computed values:
    if (variant.get('INFO')['_variableSamples']) {
        for (const sampleId of sampleIDs) {
            if (variant.get('INFO')._variableSamples.indexOf(sampleId) > -1) {
                return true
            }
        }

        return false
    }

    const genotypes = variant.get('GENOTYPES')()
    if (!genotypes || isEmptyObject(genotypes)) {
        return false
    }

    for (const sampleId of sampleIDs) {
        if (genotypes[sampleId]) {
            const gt = genotypes[sampleId]

            // If any sample in the whitelist is non-WT, show this site. Otherwise filter.
            if (isVariant(gt)) {
                return true
            }
        }
    }

    return false
}

export function isVariant(gt) {
    return !(gt === "./." || gt === ".|." || gt === '.' || gt === "0/0" || gt === "0|0")
}


export async function fetchSession(queryParam, sessionId, nativePlugins, refTheme, setState, isTable: boolean, activeTracks?:any, setBgColor?: any, successCallback?: any, trackId?: any) {
    if (!sessionId) {
        return null
    }

    return Ajax.request({
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
                    handleFailure("Error: " + error)
                }
            } else {
                loadedPlugins = []
            }

            const midnight = '#0D233F'
            const blue = '#116596'
            const mandarin = '#FFB11D'
            const grey = '#bfbfbf'

            const themePrimaryColor = jsonRes.themeLightColor || midnight
            const themeSecondaryColor = jsonRes.themeDarkColor || blue
            delete jsonRes.themeLightColor
            delete jsonRes.themeDarkColor

            if (setBgColor) {
                setBgColor(themeSecondaryColor)
            }

            jsonRes.configuration = {
                "theme": {
                    "palette": {
                        primary: {main: themeSecondaryColor},
                        secondary: {main: themePrimaryColor},
                        tertiary: refTheme.palette.augmentColor({color: {main: grey}}),
                        quaternary: refTheme.palette.augmentColor({color: {main: mandarin}}),
                    }
                }
            }

            if (successCallback) {
                try {
                    await successCallback(generateViewState(jsonRes, loadedPlugins, nativePlugins))
                } catch(error) {
                    handleFailure(error, sessionId, trackId, isTable)
                }
            } else {
                setState(generateViewState(jsonRes, loadedPlugins, nativePlugins))
            }
        },
        failure: function(res){
            handleFailure("There was an error: " + res.status, sessionId, trackId, isTable, false)
        },
        params: {session: sessionId, activeTracks: activeTracks ? activeTracks.join(',') : undefined}
    });
}

function applyUrlParams(json, queryParam) {
    const location = queryParam.get('location')
    if (location) {
        json.location = location
    }

    const highlight = queryParam.get('highlight')
    if (highlight) {
        json.highlight = highlight
    }

    const sampleFilters = queryParam.get('sampleFilters')
    if (sampleFilters) {
        const filterTokens = sampleFilters.split(':')
        if (filterTokens.length != 2) {
            handleFailure('Invalid sample filters: ' + sampleFilters)
        } else {
            const [trackId, sampleIds] = filterTokens
            const sampleList = sampleIds.split(',')
            if (sampleList.length == 0) {
                handleFailure('No samples in filter: ' + sampleFilters)
            } else {
                let found = false
                for (const track of json.tracks) {
                    if (track.trackId?.toLowerCase() === trackId?.toLowerCase() || track.name?.toLowerCase() === trackId?.toLowerCase() || track.trackId?.toLowerCase().includes(trackId?.toLowerCase())) {
                        track.displays[0].renderer.activeSamples = sampleList.join(',')
                        found = true
                        break
                    }
                }

                if (!found) {
                    handleFailure('Unable to find matching track for sample filter: ' + sampleFilters)
                }
            }
        }
    }

    const infoFilters = queryParam.get('infoFilters')
    if (infoFilters) {
        const filterTokens = infoFilters.split(':')
        if (filterTokens.length != 2) {
            handleFailure('Invalid info filters: ' + infoFilters)
        } else {
            const [trackId, infoFilterObj] = filterTokens
            const infoFilterList = JSON.parse(decodeURIComponent(infoFilterObj))
            let found = false
            for (const track of json.tracks) {
                if (track.trackId?.toLowerCase() === trackId?.toLowerCase() || track.name?.toLowerCase() === trackId?.toLowerCase() || track.trackId?.toLowerCase().includes(trackId?.toLowerCase())) {
                    track.displays[0].renderer.infoFilters = [...infoFilterList]
                    found = true
                    break
                }
            }

            if (!found) {
                handleFailure('Unable to find matching track for info filter: ' + infoFilters)
            }
        }
    }
}

function generateViewState(json, plugins, nativePlugins){
    return createViewState({
        assembly: json.assembly ?? json.assemblies,
        tracks: json.tracks,
        configuration: json.configuration,
        plugins: plugins.concat(nativePlugins),
        location: json.location,
        defaultSession: json.defaultSession,
        onChange: json.onChange
    })
}

export function navigateToTable(sessionId, locString, trackId, track?: any) {
    const sampleFilterURL = serializeSampleFilters(track)
    const infoFilterURL = serializeInfoFilters(track)
    window.location.href = ActionURL.buildURL("jbrowse", "variantTable.view", null, {session: sessionId, location: locString, trackId: trackId, activeTracks: trackId, sampleFilters: sampleFilterURL, infoFilters: infoFilterURL})
}

export function navigateToSearch(sessionId, locString, trackId, isValidRefNameForAssembly, track?: any) {
    const sampleFilterURL = serializeSampleFilters(track)
    const infoFilterURL = serializeInfoFilters(track)

    let searchString = null
    if (locString && isValidRefNameForAssembly) {
        const parsedLocString = parseLocString(locString, isValidRefNameForAssembly)
        const contig = parsedLocString.refName;
        const start = parsedLocString.start;
        const end = parsedLocString.end;

        searchString = serializeLocationToLuceneQuery(contig, start, end)
    }

    window.location.href = ActionURL.buildURL("jbrowse", "variantSearch.view", null, {session: sessionId, location: locString, trackId: trackId, activeTracks: trackId, sampleFilters: sampleFilterURL, infoFilters: infoFilterURL, searchString: searchString})
}

export function navigateToBrowser(sessionId: string, locString: string, trackGUID?: string, track?: any) {
    const sampleFilterURL = serializeSampleFilters(track)
    const infoFilterURL = serializeInfoFilters(track)
    window.location.href = ActionURL.buildURL("jbrowse", "jbrowse.view", null, {session: sessionId, location: locString, trackGUID: trackGUID, sampleFilters: sampleFilterURL, infoFilters: infoFilterURL})
}

export function parsedLocStringToUrl(parsedLocString: ParsedLocString) {
    if (!parsedLocString) {
        return ''
    }

    const start = parsedLocString.start ?? -1
    const end = parsedLocString.end ?? -1

    if (start === -1 || end === -1) {
        return parsedLocString.refName
    }

    return parsedLocString ? parsedLocString.refName + ":" + (parsedLocString.start+1) + ".." + parsedLocString.end : ""
}

export function getBrowserUrlNoFilters(sessionId, locString, trackGUID?: string, track?: any) {
    return ActionURL.buildURL("jbrowse", "jbrowse.view", null, {session: sessionId, location: locString, trackGUID: trackGUID })
}

function serializeSampleFilters(track) {
    if (!track) {
        return undefined
    }

    if (!track.configuration.displays[0].renderer.activeSamples.value) {
        return undefined
    }

    return track.configuration.trackId + ":" + track.configuration.displays[0].renderer.activeSamples.value
}

function serializeInfoFilters(track) {
    if (!track) {
        return undefined
    }

    if (!track.configuration.displays[0].renderer.infoFilters.valueJSON || isEmptyObject(track.configuration.displays[0].renderer.infoFilters.valueJSON)  || !track.configuration.displays[0].renderer.infoFilters.valueJSON.length) {
        return undefined
    }

    return track.configuration.trackId + ":" + encodeURIComponent(track.configuration.displays[0].renderer.infoFilters.valueJSON)
}

function handleFailure(error, sessionId?, trackId?, isTable?, reloadOnFailure = true) {
    alert(error)

    if (reloadOnFailure && sessionId && trackId) {
        if (isTable) {
            navigateToTable(sessionId, "", trackId)
        } else {
            navigateToBrowser(sessionId, "", trackId)
        }
    }
}

export function getGenotypeURL(trackId, contig, start, end) {
    // NOTE: due to jbrowse/trix behavior, the trackId that gets serialized into the trix index is the actual trackGUID plus the filename.
    // Since this action expects the GUID alone, detect long filenames and subset.
    // TODO: once this behavior is fixed in jbrowse, remove this logic
    if (trackId.length > 36) {
        trackId = trackId.substr(0,36)
    }

    return ActionURL.buildURL("jbrowse", "genotypeTable.view", null, {trackId: trackId, chr: contig, start: start, stop: end})
}

export function serializeLocationToLuceneQuery(contig, start, end) {
    const filters = [
        new Filter("contig", OperatorKey.Equals, contig.toString()),
        new Filter("start", OperatorKey.NumericGte, start.toString()),
        new Filter("end", OperatorKey.NumericLte, end.toString())
    ]

    return createEncodedFilterString(filters)
}

export async function fetchLuceneQuery(filters, sessionId, trackGUID, offset, pageSize, sortField, sortReverseString, successCallback, failureCallback) {
    if (!offset) {
        offset = 0
    }

    if (!sessionId) {
        failureCallback("There was an error: " + "Lucene query: no session ID")
        return
    }

    if (!trackGUID) {
        failureCallback("There was an error: " + "Lucene query: no track ID")
        return
    }

    if (!filters) {
        failureCallback("There was an error: " + "Lucene query: no filters")
        return
    }

    let sortReverse;
    if(sortReverseString == "asc") {
        sortReverse = true
    } else {
        sortReverse = false
    }

    return Ajax.request({
        url: ActionURL.buildURL('jbrowse', 'luceneQuery.api'),
        method: 'GET',
        success: async function(res){
            let jsonRes = JSON.parse(res.response);
            successCallback(jsonRes)
        },
        failure: function(res) {
            failureCallback("There was an error: " + res.status + "\n Status Body: " + res.responseText + "\n Session ID:" + sessionId)
        },
        params: {
            "searchString": buildLuceneQuery(filters),
            "sessionId": sessionId,
            "trackId": trackGUID,
            "offset": offset,
            "pageSize": pageSize,
            "sortField": sortField ?? "genomicPosition",
            "sortReverse": sortReverse 
        },
    });
}

export class FieldModel {
    name: string
    label: string
    description: string
    type: string
    isInDefaultColumns: boolean
    isMultiValued: boolean
    isHidden: boolean
    colWidth: number
    formatString: string
    orderKey: number = 999
    allowableValues: string[]
    category: string
    url: string
    flex: number
    supportsFilter: boolean = true

    getLabel(): string {
        return this.label ?? this.name
    }

    getMuiType(): GridColType {
        let muiFieldType;

        switch (this.type) {
            case 'Flag':
            case 'String':
            case 'Character':
            case 'Impact':
                muiFieldType = "string";
                break;
            case 'Float':
            case 'Integer':
                muiFieldType = "number";
            break;
        }

        return muiFieldType
    }

    getOperators(): Operator[] {
        if (this.name === 'variableSamples') {
          return [
            OperatorKey.EqualsOneOf,
            OperatorKey.VariableIn,
            OperatorKey.NotVariableIn,
            OperatorKey.VariableInAll,
            OperatorKey.VariableInAny,
            OperatorKey.NotVariableInAny,
            OperatorKey.NotVariableInOne,
            OperatorKey.IsEmpty,
            OperatorKey.IsNotEmpty,
          ].map(k => OperatorRegistry[k])
        }

        switch (this.type) {
          case 'String':
          case 'Flag':
          case 'Character':
          case 'Impact':
            return [
              OperatorKey.Equals,
              OperatorKey.NotEquals,
              OperatorKey.Contains,
              OperatorKey.NotContains,
              OperatorKey.StartsWith,
              OperatorKey.EndsWith,
              OperatorKey.IsEmpty,
              OperatorKey.IsNotEmpty,
            ].map(k => OperatorRegistry[k])

          case 'Float':
          case 'Integer':
            return [
              OperatorKey.NumericEq,
              OperatorKey.NumericNeq,
              OperatorKey.NumericGt,
              OperatorKey.NumericGte,
              OperatorKey.NumericLt,
              OperatorKey.NumericLte,
            ].map(k => OperatorRegistry[k])

          default:
            return []
        }
    }

    getDefaultOperator(): Operator | undefined {
        return this.getOperators()[0]
    }

    toGridColDef(): GridColDef {
        let gridCol: GridColDef = {
            field: this.name,
            description: this.description,
            headerName: this.label ?? this.name,
            minWidth: 25,
            width: this.colWidth ?? 50,
            type: this.getMuiType(),
            flex: this.flex || 1,
            headerAlign: 'left',
            align: "left",
            hideable: true
        }

        //TODO: consider whether we really need a separate isHidden
        //hide: this.isHidden || this.isInDefaultColumns === false


        // TODO: can we pass the JEXL format string here? How does this impact filter/sorting?
        // if (this.formatString) {
        //     gridCol.type = "string"
        //     gridCol.valueFormatter = (params: GridValueFormatterParams) => {
        //         const context = {...params.row}
        //         return jexl.evalSync(this.formatString, context)
        //     }
        // }

        // TODO: does this really apply here? Can we drop it?
        if (this.isMultiValued) {
            gridCol.sortComparator = multiValueComparator
            gridCol.filterOperators = getGridNumericOperators().map(op => multiModalOperator(op))
        }

        return gridCol
    }
}

export function createEncodedFilterString(filters: Filter[]): string {
  if (!filters.length || filters.every(f => f.isEmpty())) return 'all'
  return encodeURIComponent(filters.map(f => f.encode()).join('&'))
}

export function buildLuceneQuery(filters: Filter[]): string {
  return filters
    .filter(f => !f.isEmpty())
    .map(f => f.toLucene())
    .join('&')
}

export async function fetchFieldTypeInfo(sessionId: string, trackId: string, successCallback: (fields: FieldModel[], groups: string[], promotedFilters: Map<string, Filter[]>) => void, failureCallback) {
    if (!sessionId || !trackId) {
        console.error("Cannot fetch field type info: sessionId or trackId not provided")
        return
    }

    Ajax.request({
        url: ActionURL.buildURL('jbrowse', 'getIndexedFields.api'),
        method: 'GET',
        success: async function(res){
            const json = JSON.parse(res.response)
            const fields: Array<FieldModel> = json.fields.map((f) => Object.assign(new FieldModel(), f ))
            const groups: string[] = json.groups
            const promotedFilters: Map<string, Filter[]> = json.promotedFilters.reduce((map, obj) => {
                const [label, filterStr] = obj.split('|')
                const filters = Filter.fromString(filterStr)
                map.set(label, filters)

                return map
            }, new Map<string, Filter[]>())

            successCallback(fields, groups, promotedFilters)
        },
        failure: function(res){
            console.error(res)
            failureCallback("There was an error while fetching field types: " + res.status + "\n Status Body: " + res.statusText + "\n Session ID:" + sessionId)
        },
        params: {sessionId: sessionId, trackId: trackId},
    });
}

export function truncateToValidGUID(str: string) {
    if (str && str.length > 36) {
        return str.substring(0, 36)
    }

    return str;
}

export class Filter {
  field: string
  operator: Operator
  value: Value

  constructor(field: string, operatorKey: OperatorKey, value: Value) {
    this.field = field
    this.operator = OperatorRegistry[operatorKey]
    this.value = value
  }

  encode(): string {
    return [this.field, this.operator.key, this.value].join(',')
  }

  toLucene(): string {
    return this.operator.generateLucene(this.field, this.value as any)
  }

  isEmpty(): boolean {
    return this.field === ''
  }

  static fromString(str: string): Filter[] {
    const decoded = decodeURIComponent(str)
    return decoded
      .split('&')
      .filter(x => x !== 'all')
      .map(item => {
        const [field, opKey, ...valParts] = item.split(',')
        const value = valParts.join(',')
        return new Filter(field, opKey as OperatorKey, isNaN(Number(value)) ? value : Number(value))
      })
  }

  static deduplicate(filters: Filter[]): Filter[] {
    const map = new Map(filters.map(f => [f.encode(), f]))
    return Array.from(map.values())
  }
}

export function searchStringToInitialFilters(knownFieldNames: string[]) : Filter[] {
    const queryParam = new URLSearchParams(window.location.search)
    const searchString = queryParam.get("searchString")

    if (searchString && searchString != "all") {
        return Filter.fromString(searchString).filter(({ field }) => knownFieldNames.includes(field))
    }

    return [new Filter('', OperatorKey.None, '')]
}


export const multiValueComparator: GridComparatorFn = (v1, v2) => {
    return arrayMax(parseCellValue(v1)) - arrayMax(parseCellValue(v2))
}

export const multiModalOperator = (operator: GridFilterOperator) => {
    const getApplyFilterFn = (
        filterItem: GridFilterItem,
        column: GridColDef,
    ) => {
        const innerFilterFn = operator.getApplyFilterFn(filterItem, column);
        if (!innerFilterFn) {
            return innerFilterFn;
        }

        return (value) => {
            let cellValue = parseCellValue(value)

            switch(filterItem.operator) {
                case "!=":
                    return cellValue.map(val => val == Number(filterItem.value)).every((val) => val == false)
                case "=":
                    return cellValue.map(val => val == Number(filterItem.value)).includes(true)
                case ">":
                    return arrayMax(cellValue) > Number(filterItem.value)
                case "<":
                    return arrayMax(cellValue) < Number(filterItem.value)
                case "<=":
                    return arrayMax(cellValue) <= Number(filterItem.value)
                case ">=":
                    return arrayMax(cellValue) >= Number(filterItem.value)
                case "isEmpty":
                    return cellValue.length == 0
                case "isNotEmpty":
                    return cellValue.length > 0
                default:
                    return true
            }
        }
    }

    return {
        ...operator,
        getApplyFilterFn,
    }
}

export const parseCellValue = (cellValue) => String(cellValue ?? "").split(",").map(str => {
    return Number(str);
})
