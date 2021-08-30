import { ConfigurationSchema } from '@jbrowse/core/configuration'
import Plugin from '@jbrowse/core/Plugin'
import PluginManager from '@jbrowse/core/PluginManager'
import { isAbstractMenuManager } from '@jbrowse/core/util'
//import { version } from '../package.json'
import { ReactComponent } from './HelloView'
import HelloWidget from './HelloWidget'
import WidgetDisplay from './WidgetDisplay'
import DisplayType from '@jbrowse/core/pluggableElementTypes/DisplayType'
import {
  createBaseTrackConfig,
  createBaseTrackModel,
} from '@jbrowse/core/pluggableElementTypes/models'

import AdapterType from "@jbrowse/core/pluggableElementTypes/AdapterType";

export default class MyProjectPlugin extends Plugin {
  name = 'MyProject'
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
      .model({ type: types.literal('HelloView') })
      .actions(() => ({
        setWidth() {
          // unused but required by your view
        },
      }))

    pluginManager.addTrackType(() => {
      const configSchema = ConfigurationSchema(
        'DemoTrack',
        {},
        {
          baseConfiguration: createBaseTrackConfig(pluginManager),
          explicitIdentifier: 'trackId',
        },
      )
      return new TrackType({
        name: 'DemoTrack',
        configSchema,
        stateModel: createBaseTrackModel(
          pluginManager,
          'DemoTrack',
          configSchema,
        ),
      })
    })

    pluginManager.addDisplayType(() => {
      const { configSchema, stateModel } = pluginManager.load(WidgetDisplay)
      return new DisplayType({
        name: 'WidgetDisplay',
        configSchema,
        stateModel,
        trackType: 'DemoTrack',
        viewType: 'LinearGenomeView',
        ReactComponent: BaseLinearDisplayComponent,
      })
    })

    pluginManager.addWidgetType(() => {
      const {
        configSchema,
        ReactComponent,
        stateModel,
      } = pluginManager.load(HelloWidget)

      return new WidgetType({
        name: 'HelloWidget',
        heading: 'Feature details',
        configSchema,
        stateModel,
        ReactComponent,
      })
    })

    pluginManager.addViewType(() => {
      return new ViewType({ name: 'HelloView', stateModel, ReactComponent })
    })
  }

  configure(pluginManager: PluginManager) {
    if (isAbstractMenuManager(pluginManager.rootModel)) {
      // @ts-ignore
      pluginManager.rootModel.appendToSubMenu(['File', 'Add'], {
        label: 'Open Hello!',
        // @ts-ignore
        onClick: session => {
          session.addView('HelloView', {})
        },
      })
    }
  }
  
}
