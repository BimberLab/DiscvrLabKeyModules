import { isEmptyObject } from 'jquery'
import jexl from 'jexl'

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