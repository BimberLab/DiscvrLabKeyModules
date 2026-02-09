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
                draw: ctx => {
                  const { ctx: context, featureLayout } = ctx
                  const { x, y, width, height } = featureLayout

                  const centerX = x + width / 2
                  const centerY = y + height / 2
                  const halfWidth = Math.max(width / 2, 4)
                  const halfHeight = height / 2

                  // Purple diamond fill
                  context.fillStyle = '#800080'
                  context.beginPath()
                  context.moveTo(centerX, centerY - halfHeight) // top
                  context.lineTo(centerX + halfWidth, centerY) // right
                  context.lineTo(centerX, centerY + halfHeight) // bottom
                  context.lineTo(centerX - halfWidth, centerY) // left
                  context.closePath()
                  context.fill()

                  // Indigo stroke
                  context.strokeStyle = '#4B0082'
                  context.lineWidth = 1
                  context.stroke()
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
    }

    configure(pluginManager: PluginManager) {

    }
}