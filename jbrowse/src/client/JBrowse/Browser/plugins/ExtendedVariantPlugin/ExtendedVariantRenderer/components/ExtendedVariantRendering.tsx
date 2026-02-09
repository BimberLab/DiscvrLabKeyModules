import { observer } from 'mobx-react';
import React from 'react';
import { deserializeFilters } from '../../InfoFilterWidget/filterUtil';
import Diamond from './Diamond';
import { passesInfoFilters, passesSampleFilters } from '../../../../../utils';
import { VcfFeature } from '@jbrowse/plugin-variants';

export function ExtendedVariantRendering(props) {
    const { features, rendererConfig } = props
    const { activeSamples, infoFilters } = rendererConfig

    let expandedFilters = []
    if (infoFilters.toJSON()) {
        expandedFilters = deserializeFilters(infoFilters.toJSON())
    }

    const sampleFilters = activeSamples.value ? activeSamples.value.split(',') : null

    function diamondValidator(feature: VcfFeature) {
        return feature.get('type') === "SNV";
    }

    function isFeatureDisplayed(feature: VcfFeature) {
        return passesInfoFilters(feature, expandedFilters) && passesSampleFilters(feature, sampleFilters)
    }

    function getDiamondValidator() {
        return {
            glyph: Diamond,
            validator: diamondValidator
        }
    }

    return (
        <div />
        /*<SvgFeatureRendererReactComponent
            featureDisplayHandler={isFeatureDisplayed}
            extraGlyphs={[getDiamondValidator()]}
            {...props}
        />*/
    )
}

export default observer(ExtendedVariantRendering)