import { ConfigurationSchema } from '@jbrowse/core/configuration'
import Plugin from '@jbrowse/core/Plugin'
import PluginManager from '@jbrowse/core/PluginManager'
import { isAbstractMenuManager } from '@jbrowse/core/util'
//import { version } from '../package.json'
import ExtendedVariantWidget from './ExtendedVariantWidget'
import ExtendedVariantDisplay from './ExtendedVariantDisplay'
import DisplayType from '@jbrowse/core/pluggableElementTypes/DisplayType'
import {
  createBaseTrackConfig,
  createBaseTrackModel,
} from '@jbrowse/core/pluggableElementTypes/models'

import AdapterType from "@jbrowse/core/pluggableElementTypes/AdapterType";

export default class ExtendedVariantPlugin extends Plugin {
  name = 'ExtendedVariantPlugin'
  version = "0.0.1"//version

  install(pluginManager: PluginManager) {
    console.log("Installing plugins")
    const { jbrequire } = pluginManager
    const { types } = pluginManager.lib['mobx-state-tree']

    const ViewType = jbrequire('@jbrowse/core/pluggableElementTypes/ViewType')
    const WidgetType = jbrequire('@jbrowse/core/pluggableElementTypes/WidgetType')
    const TrackType = jbrequire('@jbrowse/core/pluggableElementTypes/TrackType')
    const LGVPlugin = pluginManager.getPlugin('LinearGenomeViewPlugin',) as import('@jbrowse/plugin-linear-genome-view').default
    const { BaseLinearDisplayComponent } = LGVPlugin.exports

    const stateModel = types
      .model({ type: types.literal('ExtendedVariantView') })
      .actions(() => ({
        setWidth() {
          // unused but required by your view
        },
      }))

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

  }

  configure(pluginManager: PluginManager) {

  }
  
}
