import Plugin from '@jbrowse/core/Plugin';
import PluginManager from '@jbrowse/core/PluginManager';
import ExtendedVariantWidget from './ExtendedVariantWidget';
import ExtendedVariantDisplay from './ExtendedVariantDisplay';
import DisplayType from '@jbrowse/core/pluggableElementTypes/DisplayType';
import { createBaseTrackModel } from '@jbrowse/core/pluggableElementTypes/models';

import AdapterType from '@jbrowse/core/pluggableElementTypes/AdapterType';
import { configSchema as EVAdapterConfigSchema, EVAdapterClass } from './ExtendedVariantAdapter';

import {
    configSchema as EVRendererConfigSchema,
    ExtendedVariantRenderer,
    ReactComponent as EVRendererReactComponent
} from './ExtendedVariantRenderer';

import { default as createExtendedVariantTrackConfig } from './configSchema';
import InfoFilterWidget from './InfoFilterWidget';
import ColorWidget from './ColorWidget';
import SampleFilterWidget from './SampleFilterWidget';

export default class ExtendedVariantPlugin extends Plugin {
    name = 'ExtendedVariantPlugin'
    version = "0.0.1"

    install(pluginManager: PluginManager) {
        const { jbrequire } = pluginManager
        const WidgetType = jbrequire('@jbrowse/core/pluggableElementTypes/WidgetType')
        const TrackType = jbrequire('@jbrowse/core/pluggableElementTypes/TrackType')
        const LGVPlugin = pluginManager.getPlugin('LinearGenomeViewPlugin',) as import('@jbrowse/plugin-linear-genome-view').default
        const { BaseLinearDisplayComponent } = LGVPlugin.exports

        pluginManager.addRendererType(
            () =>
                new ExtendedVariantRenderer({
                    name: 'ExtendedVariantRenderer',
                    ReactComponent: EVRendererReactComponent,
                    configSchema: EVRendererConfigSchema,
                    pluginManager,
                }),
        )

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
                heading: 'Filter by Sample',
                configSchema,
                stateModel,
                ReactComponent,
            })
        })
    }

    configure(pluginManager: PluginManager) {

    }
}