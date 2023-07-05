import { isEmptyObject } from 'jquery';
import jexl from 'jexl';
import { createViewState, loadPlugins } from '@jbrowse/react-linear-genome-view';
import { ActionURL, Ajax } from '@labkey/api';

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

export function passesSampleFilters(feature, sampleIDs){
    if (!sampleIDs || sampleIDs.length === 0) {
        return true
    }

    const featureVariant = feature.variant ?? feature.data
    const samples = featureVariant.SAMPLES || featureVariant.samples
    if (!samples || isEmptyObject(samples)) {
        return false
    }

    // Preferentially use pre-computed values:
    if (featureVariant.INFO._variableSamples) {
        for (const sampleId of sampleIDs) {
            if (featureVariant.INFO._variableSamples.indexOf(sampleId) > -1) {
                return true
            }
        }

        return false
    }

    for (const sampleId of sampleIDs) {
        if (samples[sampleId]) {
            const gt = samples[sampleId]["GT"][0]

            // If any sample in the whitelist is non-WT, show this site. Otherwise filter.
            if (isVariant(gt)) {
                return true
            }
        }
    }

    return false
}

function isVariant(gt) {
    return !(gt === "./." || gt === ".|." || gt === "0/0" || gt === "0|0")
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
                        tertiary: refTheme.palette.augmentColor({main: grey}),
                        quaternary: refTheme.palette.augmentColor({main: mandarin}),
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
        json.location = location;
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

function generateViewState(genome, plugins, nativePlugins){
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

export function navigateToTable(sessionId, locString, trackId, track?: any) {
    const sampleFilterURL = serializeSampleFilters(track)
    const infoFilterURL = serializeInfoFilters(track)
    window.location.href = ActionURL.buildURL("jbrowse", "variantTable.view", null, {session: sessionId, location: locString, trackId: trackId, activeTracks: trackId, sampleFilters: sampleFilterURL, infoFilters: infoFilterURL})
}

export function navigateToFreeTextSearch(sessionId, trackGUID) {
    // TODO: update this to use a different action. Also evaluate how to serialize filters
    window.location.href = ActionURL.buildURL("jbrowse", "variantTable.view", null, {session: sessionId, trackId: trackGUID})
}

export function navigateToBrowser(sessionId, locString, trackGUID?: string, track?: any) {
    const sampleFilterURL = serializeSampleFilters(track)
    const infoFilterURL = serializeInfoFilters(track)
    return ActionURL.buildURL("jbrowse", "jbrowse.view", null, {session: sessionId, location: locString, trackGUID: trackGUID, sampleFilters: sampleFilterURL, infoFilters: infoFilterURL})
}

export function navigateToBrowserNoFilters(sessionId, locString, trackGUID?: string, track?: any) {
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

    if (!track.configuration.displays[0].renderer.infoFilters.valueJSON || isEmptyObject(track.configuration.displays[0].renderer.infoFilters.valueJSON)) {
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

function generateLuceneString(field, operator, value) {
  let luceneQueryString = '';

  if (field === 'variableSamples' && operator == "in set") {
    return `variableSamples:~${value}~`;
  }

  switch (operator) {
    case '=': // Exact match for numeric fields
        luceneQueryString = `${field}:[${value} TO ${value}]`;
        break;
    case '!=': // Not equal to, for numeric fields
        luceneQueryString = `${field}:[* TO ${value - 0.000001}] OR ${field}:[${value + 0.000001} TO *]`;
        break;
    case '>': // Greater than for numeric fields
        luceneQueryString = `${field}:[${value + 0.000001} TO *]`;
        break;
    case '>=': // Greater than or equal to for numeric fields
        luceneQueryString = `${field}:[${value} TO *]`;
        break;
    case '<': // Less than for numeric fields
        luceneQueryString = `${field}:[* TO ${value - 0.000001}]`;
        break;
    case '<=': // Less than or equal to for numeric fields
        luceneQueryString = `${field}:[* TO ${value}]`;
        break;
    case 'equals': // Exact match for string fields
        luceneQueryString = `${field}:${value}`;
        break;
    case 'contains': // Substring search for string fields
        luceneQueryString = `${field}:*${value}*`;
        break;
    case 'does not equal': // Not equal to for string fields
        luceneQueryString = `-${field}:${value}`;
        break;
    case 'does not contain': // Does not contain for string fields
        luceneQueryString = `-${field}:*${value}*`;
        break;
    case 'starts with': // Starts with for string fields
        luceneQueryString = `${field}:${value}*`;
        break;
    case 'ends with': // Ends with for string fields
        luceneQueryString = `${field}:*${value}`;
        break;
    case 'is empty': // Field is empty
        luceneQueryString = `*:* -${field}:*`;
        break;
    case 'is not empty': // Field is not empty
        luceneQueryString = `${field}:*`;
        break;
    case 'variable in': // Variable in for multi-valued fields
        luceneQueryString = `${field}:${value}`;
        break;
    case 'not variable in': // Not variable in for multi-valued fields
        luceneQueryString = `*:* -${field}:${value}`;
        break;
    default:
        // Operators that require multiple values
        const values = value.split(',');

        switch (operator) {
        case 'variable in all of': // Variable in all of the provided values
            luceneQueryString = values.map(v => `+${field}:${v}`).join(' ');
            break;
        case 'variable in any of': // Variable in any of the provided values
            luceneQueryString = values.map(v => `${field}:${v}`).join(' OR ');
            break;
        case 'not variable in any of': // Not variable in any of the provided values
            luceneQueryString = values.map(v => `*:* -${field}:${v}`).join(' AND ');
            break;
        case 'not variable in one of': // Not variable in one of the provided values
            luceneQueryString = values.map(v => `*:* -${field}:${v}`).join(' OR ');
            break;
        default:
            throw new Error(`Invalid operator: ${operator}`);
        }
    }

  return luceneQueryString;
}

export async function fetchLuceneQuery(filters, sessionId, trackGUID, offset, successCallback, failureCallback) {
    console.log("Running fetchLuceneQuery, FILTERS")
    console.log(filters)

    if (!offset) {
        offset = 0
    }

    console.log("Offset: ", offset)

    if (!sessionId) {
        console.error("Lucene query: no session ID")
        failureCallback()
        return
    }

    if (!trackGUID) {
        console.error("Lucene query: no track ID")
        failureCallback()
        return
    }

    if (!filters) {
        console.log("No filters!")
        failureCallback()
        return
    }

    console.log("Attempting lucene query:")

    return Ajax.request({
        url: ActionURL.buildURL('jbrowse', 'luceneQuery.api'),
        method: 'GET',
        success: async function(res){
            let jsonRes = JSON.parse(res.response);
            console.log("Lucene query success:", jsonRes)
            successCallback(jsonRes)
        },
        failure: function(res){
            console.log("Lucene query failure:", res.status)
            console.log(res.response)
            console.log(res.statusText)
            failureCallback()
            handleFailure("There was an error: " + res.status, sessionId)
        },
        params: {"searchString": createEncodedFilterString(filters, true), "sessionId": sessionId, "trackId": trackGUID, "offset": offset},
    });
}

export function createEncodedFilterString(filters: Array<{field: string; operator: string; value: string}>, lucenify: boolean) {
    let ret: any = []

    if(!filters || filters.length == 0 || (filters.length == 1 && filters[0].field == "" && filters[0].operator == "" && filters[0].value == "")) {
        console.log("EMPTY FILTERS, RETURNING ALL!")
        console.log(filters)
        return "all"
    }

    if(lucenify) {
        ret = filters.map(val => generateLuceneString(val.field, val.operator, val.value));
    } else {
        ret = filters.map(val => val.field + "," + val.operator + "," + val.value)
    }
    const concatenatedString = ret.join('&');

    return encodeURIComponent(concatenatedString);
}

export async function fetchFieldTypeInfo(sessionId, trackId, successCallback) {
    if (!sessionId || !trackId) {
        console.error("Lucene fetch field type info: sessionId or trackId not provided")
        return
    }

    return Ajax.request({
        url: ActionURL.buildURL('jbrowse', 'getIndexedFields.api'),
        method: 'GET',
        success: async function(res){
            let jsonRes = JSON.parse(res.response);
            console.log("Fetch field type info success:", jsonRes)
            successCallback(jsonRes)
        },
        failure: function(res){
            console.log("Fetch field type info failure:", res.status)
            handleFailure("There was an error: " + res.status, sessionId)
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

export function searchStringToInitialFilters(operators) : any[] {
    const queryParam = new URLSearchParams(window.location.search)
    const searchString = queryParam.get("searchString")

    let initialFilters: any[] = [{ field: "", operator: "", value: "" }]

    if (searchString && searchString != "all") {
        const decodedSearchString = decodeURIComponent(searchString)
        const searchStringsArray = decodedSearchString.split("&")
        console.log("search strings array: ", searchStringsArray)
        initialFilters = searchStringsArray
        .map((item) => {
        const [field, operator, value] = item.split(",")
        return { field, operator, value }
        })
        .filter(({ field }) => operators.hasOwnProperty(field))
    }

    return initialFilters 
}

export function fieldTypeInfoToOperators(fieldTypeInfo): any {
    const stringType = ["equals", "does not equal", "contains", "does not contain", "starts with", "ends with", "is empty", "is not empty"];
    const variableSamplesType = ["in set", "variable in", "not variable in", "variable in all of", "variable in any of", "not variable in any of", "not variable in one of", "is empty", "is not empty"];
    const numericType = ["=", "!=", ">", ">=", "<", "<=", "is empty", "is not empty"];
    const noneType = [];

    const operators = Object.keys(fieldTypeInfo).reduce((acc, idx) => {
      const fieldObj = fieldTypeInfo[idx];
      const field = fieldObj.name;
          const type = fieldObj.type;

          let fieldType;

          switch (type) {
            case 'Flag':
            case 'String':
            case 'Character':
              fieldType = stringType;
              break;
            case 'Float':
            case 'Integer':
              fieldType = numericType;
              break;
            case 'Impact':
              fieldType = stringType;
              break;
            case 'None':
            default:
              fieldType = noneType;
              break;
          }

          acc[field] = { type: fieldType };

          if(field == "variableSamples") {
            acc[field] = { type: variableSamplesType };
          }

          return acc;
        }, {}) ?? [];

    return operators
}

