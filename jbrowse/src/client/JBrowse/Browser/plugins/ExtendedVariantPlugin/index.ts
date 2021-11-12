import { ConfigurationSchema } from '@jbrowse/core/configuration'
import Plugin from '@jbrowse/core/Plugin'
import PluginManager from '@jbrowse/core/PluginManager'
import ExtendedVariantWidget from './ExtendedVariantWidget'
import ExtendedVariantDisplay from './ExtendedVariantDisplay'
import DisplayType from '@jbrowse/core/pluggableElementTypes/DisplayType'
import {
  createBaseTrackConfig,
  createBaseTrackModel,
} from '@jbrowse/core/pluggableElementTypes/models'

import AdapterType from "@jbrowse/core/pluggableElementTypes/AdapterType";
import {configSchema as EVAdapterConfigSchema} from './ExtendedVariantAdapter'
import {EVAdapterClass} from './ExtendedVariantAdapter'

import {
    configSchema as EVRendererConfigSchema,
    ReactComponent as EVRendererReactComponent
} from './ExtendedVariantRenderer'

import BoxRendererType from '@jbrowse/core/pluggableElementTypes/renderers/BoxRendererType'
class ExtendedVariantRenderer extends BoxRendererType {
  supportsSVG = true
}
import FilterWidget from './FilterWidget'
import ColorWidget from './ColorWidget'

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
      const configSchema = ConfigurationSchema(
        'ExtendedVariantTrack',
        {},
        {
          baseConfiguration: createBaseTrackConfig(pluginManager),
          explicitIdentifier: 'trackId',
        },
      )
      return new TrackType({
        name: 'ExtendedVariantTrack',
        configSchema,
        stateModel: createBaseTrackModel(
          pluginManager,
          'ExtendedVariantTrack',
          configSchema,
        ),
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
      } = pluginManager.load(FilterWidget)

      return new WidgetType({
        name: 'FilterWidget',
        heading: 'Filters',
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

  }

  configure(pluginManager: PluginManager) {

  }
}