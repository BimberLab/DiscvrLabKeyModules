import { isEmptyObject } from 'jquery'
import jexl from 'jexl'
import { createViewState, loadPlugins } from '@jbrowse/react-linear-genome-view';
import { ActionURL, Ajax } from '@labkey/api';

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
            console.error("Error in filter execution: " + e)
        }
    }

    return true
}

export function passesSampleFilters(feature, sampleIDs){
    if (!sampleIDs || sampleIDs.length === 0) {
        return true
    }

    const featureVariant = feature.variant ?? feature.data

    if (!featureVariant.SAMPLES || isEmptyObject(featureVariant.SAMPLES)) {
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
        if (featureVariant.SAMPLES[sampleId]) {
            const gt = featureVariant.SAMPLES[sampleId]["GT"][0]

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


export async function fetchSession(queryParam, session, nativePlugins, refTheme, setState, activeTracks?:any, setBgColor?: any, successCallback?: any) {
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
                    console.error("Error: ", error)
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

            if(setBgColor) {
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
                await successCallback(generateViewState(jsonRes, loadedPlugins, nativePlugins))
            } else {
                setState(generateViewState(jsonRes, loadedPlugins, nativePlugins))
            }
        },
        failure: function(res){
            //TODO: better, consistent error handling
            setState("invalid");
            console.log(res);
        },
        params: {session: session, activeTracks: activeTracks ? activeTracks.join(',') : undefined}
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
            console.error('Invalid sample filters: ' + sampleFilters)
        } else {
            const [trackId, sampleIds] = filterTokens
            const sampleList = sampleIds.split(',')
            if (sampleList.length == 0) {
                console.error('No samples in filter: ' + sampleFilters)
            } else {
                let found = false
                for (const track of json.tracks) {
                    if (track.trackId?.toLowerCase() === trackId?.toLowerCase() || track.name?.toLowerCase() === trackId?.toLowerCase()) {
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

    // TODO: track doesn't contain an infoFilters list, for some reason
    const infoFilters = queryParam.get('infoFilters')
    if (infoFilters) {
        const filterTokens = infoFilters.split(':')
        if (filterTokens.length != 2) {
            console.error('Invalid info filters: ' + infoFilters)
        } else {
            const [trackId, infoFilterObj] = filterTokens
            const infoFilterList = JSON.parse(decodeURIComponent(infoFilterObj))
            let found = false
            for (const track of json.tracks) {
                if (track.trackId?.toLowerCase() === trackId?.toLowerCase() || track.name?.toLowerCase() === trackId?.toLowerCase()) {
                    track.displays[0].renderer.infoFilters = [...infoFilterList]
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
    window.location.href = ActionURL.buildURL("jbrowse", "variantTable.view", null, {session: sessionId, location: locString, trackId: trackId, sampleFilters: sampleFilterURL, infoFilters: infoFilterURL})
}

export function navigateToBrowser(sessionId, locString, trackId?: string, track?: any) {
    const sampleFilterURL = serializeSampleFilters(track)
    const infoFilterURL = serializeInfoFilters(track)
    window.location.href = ActionURL.buildURL("jbrowse", "jbrowse.view", null, {session: sessionId, location: locString, trackId: trackId, sampleFilters: sampleFilterURL, infoFilters: infoFilterURL})
}

function serializeSampleFilters(track) {
    if(!track) {
        return undefined
    }

    return track.configuration.trackId + ":" + track.configuration.displays[0].renderer.activeSamples.value
}

function serializeInfoFilters(track) {
    if(!track) {
        return undefined
    }

    return track.configuration.trackId + ":" + encodeURIComponent(track.configuration.displays[0].renderer.infoFilters.valueJSON)
}