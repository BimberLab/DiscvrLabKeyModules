import jexl from 'jexl';
import { SvgFeatureRendererReactComponent } from '@jbrowse/plugin-svg';
import { observer } from 'mobx-react';
import React from 'react';
import { deserializeFilters } from '../../InfoFilterWidget/filterUtil';
import Diamond from './Diamond';
import { passesInfoFilters, passesSampleFilters } from '../../../../../utils';

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
            {...props}
        />
    )
}

export default observer(ExtendedVariantRendering)