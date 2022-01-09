import jexl from 'jexl';
import { isEmptyObject } from 'jquery';
import { SvgFeatureRendererReactComponent } from '@jbrowse/plugin-svg';
import { observer } from 'mobx-react';
import React from 'react';
import { deserializeFilters } from '../../InfoFilterWidget/filterUtil';
import Diamond from './Diamond';

export function ExtendedVariantRendering(props) {
    const { features, rendererConfig } = props
    const { activeSamples, infoFilters } = rendererConfig

    let expandedFilters = []
    if (infoFilters.toJSON()) {
        expandedFilters = deserializeFilters(infoFilters.toJSON())
    }

    const sampleFilters = activeSamples.value ? activeSamples.value.split(',') : null

    function diamondValidator(feature) {
        return feature.get('type') === "SNV";
    }

    function isFeatureDisplayed(feature) {
        return passesInfoFilters(feature, expandedFilters) && passesSampleFilters(feature, sampleFilters)
    }

    // TODO: can we access the pluginManager.jexl instance directly??
    jexl.addFunction('arrayMax', (array) => {
        return Array.isArray(array) ? Math.max(...array) : array
    })

    function passesInfoFilters(feature, filters){
        if (!filters || !filters.length){
            return true
        }

        for (const filterObj of filters){
            try {
                if (!jexl.evalSync(filterObj.jexlExpression, feature)){
                    return false
                }
            } catch (e){
                console.error("Error in filter execution: " + e)
            }
        }

        return true
    }

    function isVariant(gt) {
        return !(gt === "./." || gt === ".|." || gt === "0/0" || gt === "0|0")
    }

    function passesSampleFilters(feature, sampleIDs){
        if (!sampleIDs || sampleIDs.length === 0) {
            return true
        }

        if (!feature.variant.SAMPLES || isEmptyObject(feature.variant.SAMPLES)) {
            return false
        }

        // Preferentially use pre-computed values:
        if (feature.variant.INFO._variableSamples) {
            for (const sampleId of sampleIDs) {
                if (feature.variant.INFO._variableSamples.indexOf(sampleId) > -1) {
                    return true
                }
            }

            return false
        }

        for (const sampleId of sampleIDs) {
            if (feature.variant.SAMPLES[sampleId]) {
                const gt = feature.variant.SAMPLES[sampleId]["GT"][0]

                // If any sample in the whitelist is non-WT, show this site. Otherwise filter.
                if (isVariant(gt)) {
                    return true
                }
            }
        }

        return false
    }

    function getDiamondValidator() {
        return {
            glyph: Diamond,
            validator: diamondValidator
        }
    }

    return (
        <SvgFeatureRendererReactComponent
            featureDisplayHandler={isFeatureDisplayed}
    extraGlyphs={[getDiamondValidator()]}
    />
)
}

export default observer(ExtendedVariantRendering)