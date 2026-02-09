import Plugin from '@jbrowse/core/Plugin';
import PluginManager from '@jbrowse/core/PluginManager';
import ExtendedVariantWidget from './ExtendedVariantWidget';
import ExtendedVariantDisplay from './ExtendedVariantDisplay';
import DisplayType from '@jbrowse/core/pluggableElementTypes/DisplayType';
import { createBaseTrackModel } from '@jbrowse/core/pluggableElementTypes/models';

import CanvasFeatureRenderer from "@jbrowse/plugin-canvas";
import AdapterType from '@jbrowse/core/pluggableElementTypes/AdapterType';
import { configSchema as EVAdapterConfigSchema, EVAdapterClass } from './ExtendedVariantAdapter';

import { default as createExtendedVariantTrackConfig } from './configSchema';
import InfoFilterWidget from './InfoFilterWidget';
import ColorWidget from './ColorWidget';
import SampleFilterWidget from './SampleFilterWidget';
import GlyphType from '@jbrowse/core/pluggableElementTypes/GlyphType';
import { readConfObject } from '@jbrowse/core/configuration';
import { emphasize } from '@jbrowse/core/util/color';
import { deserializeFilters } from './InfoFilterWidget/filterUtil';
import { passesInfoFilters, passesSampleFilters } from '../../../utils';

const utrHeightFraction = 0.65

function isUTR(feature: any) {
    return /(\bUTR|_UTR|untranslated[_\s]region)\b/.test(
        feature?.get?.('type') || '',
    )
}

export default class ExtendedVariantPlugin extends Plugin {
    name = 'ExtendedVariantPlugin'
    version = "0.0.1"

    install(pluginManager: PluginManager) {
        const { jbrequire } = pluginManager
        const WidgetType = jbrequire('@jbrowse/core/pluggableElementTypes/WidgetType')
        const TrackType = jbrequire('@jbrowse/core/pluggableElementTypes/TrackType')
        const LGVPlugin = pluginManager.getPlugin('LinearGenomeViewPlugin',) as import('@jbrowse/plugin-linear-genome-view').default
        const { BaseLinearDisplayComponent } = LGVPlugin.exports

        pluginManager.addAdapterType(() =>
            new AdapterType({
                name: "ExtendedVariantAdapter",
                configSchema: EVAdapterConfigSchema,
                AdapterClass: EVAdapterClass
            }),
        )

        pluginManager.addTrackType(() => {
            const configSchema = createExtendedVariantTrackConfig(pluginManager)
            return new TrackType({
                name: 'ExtendedVariantTrack',
                configSchema,
                stateModel: createBaseTrackModel(
                    pluginManager,
                    'ExtendedVariantTrack',
                    configSchema
                )
            })
        })

        pluginManager.addDisplayType(() => {
            const { configSchema, stateModel } = pluginManager.load(ExtendedVariantDisplay)
            return new DisplayType({
                name: 'ExtendedVariantDisplay',
                configSchema,
                stateModel,
                trackType: 'ExtendedVariantTrack',
                viewType: 'LinearGenomeView',
                ReactComponent: BaseLinearDisplayComponent,
            })
        })

        pluginManager.addGlyphType(
            () =>
              new GlyphType({
                name: 'SNVGlyph',
                displayName: 'SNV Diamond',
                draw: (ctx: any) => {
                  const { ctx: context, featureLayout, feature, config } = ctx
                  const selected = !!ctx?.selected
                  const rendererConfig: any = config
                  const { x, y, width } = featureLayout

                  let top = y
                  let height = featureLayout.height
                  if (isUTR(feature)) {
                    top += ((1 - utrHeightFraction) / 2) * height
                    height *= utrHeightFraction
                  }

                  const centerX = x + width / 2
                  const centerY = top + height / 2
                  const halfWidth = height / 2
                  const halfHeight = height / 2

                  const color = (readConfObject as any)(
                      rendererConfig,
                      isUTR(feature) ? 'color3' : 'color1',
                      { feature },
                  ) || '#800080'
                  const color2 = (readConfObject as any)(rendererConfig, 'color2', { feature }) || '#4B0082'

                  let emphasizedColor
                  try {
                    emphasizedColor = emphasize(color, 0.3)
                  } catch (error) {
                    emphasizedColor = color
                  }

                  context.fillStyle = selected ? emphasizedColor : color
                  context.beginPath()
                  context.moveTo(centerX, centerY - halfHeight) // top
                  context.lineTo(centerX + halfWidth, centerY) // right
                  context.lineTo(centerX, centerY + halfHeight) // bottom
                  context.lineTo(centerX - halfWidth, centerY) // left
                  context.closePath()
                  context.fill()

                  if (selected) {
                    context.strokeStyle = color2
                    context.lineWidth = 1
                    context.stroke()
                  }
                },
                match: feature => feature.get('type') === 'SNV',
              })
          )

        pluginManager.addWidgetType(() => {
            const {
                configSchema,
                ReactComponent,
                stateModel,
            } = pluginManager.load(ExtendedVariantWidget)

            return new WidgetType({
                name: 'ExtendedVariantWidget',
                heading: 'Feature details',
                configSchema,
                stateModel,
                ReactComponent,
            })
        })

        pluginManager.addWidgetType(() => {
            const {
                configSchema,
                ReactComponent,
                stateModel,
            } = pluginManager.load(InfoFilterWidget)

            return new WidgetType({
                name: 'InfoFilterWidget',
                heading: 'Filter Variants',
                configSchema,
                stateModel,
                ReactComponent,
            })
        })

        pluginManager.addWidgetType(() => {
            const {
                configSchema,
                ReactComponent,
                stateModel,
            } = pluginManager.load(ColorWidget)

            return new WidgetType({
                name: 'ColorWidget',
                heading: 'Color Schemes',
                configSchema,
                stateModel,
                ReactComponent,
            })
        })

        pluginManager.addWidgetType(() => {
            const {
                configSchema,
                ReactComponent,
                stateModel,
            } = pluginManager.load(SampleFilterWidget)

            return new WidgetType({
                name: 'SampleFilterWidget',
                heading: 'Filter By Sample',
                configSchema,
                stateModel,
                ReactComponent,
            })
        })

        pluginManager.jexl.addFunction('arrayMax', (array) => {
            return Array.isArray(array) ? Math.max(...array) : array
        })

        pluginManager.jexl.addFunction('formatWithCommas', (val) => {
            return val ? Number(val).toLocaleString() : val
        })

        pluginManager.jexl.addFunction('passesInfoFilters', (feature, serializedFilters) => {
            try {
                const filters = typeof serializedFilters === 'string'
                    ? JSON.parse(serializedFilters)
                    : serializedFilters
                const expandedFilters = deserializeFilters(filters)
                return passesInfoFilters(feature, expandedFilters)
            } catch (e) {
                console.error(e)
                return true
            }
        })

        pluginManager.jexl.addFunction('passesSampleFilters', (feature, serializedSampleFilters) => {
            try {
                const sampleFilters = typeof serializedSampleFilters === 'string'
                    ? JSON.parse(serializedSampleFilters)
                    : serializedSampleFilters
                return passesSampleFilters(feature, sampleFilters)
            } catch (e) {
                console.error(e)
                return true
            }
        })
    }

    configure(pluginManager: PluginManager) {

    }
}