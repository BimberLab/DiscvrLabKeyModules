import { ConfigurationSchema } from '@jbrowse/core/configuration'
import Plugin from '@jbrowse/core/Plugin'
import PluginManager from '@jbrowse/core/PluginManager'
import { isAbstractMenuManager } from '@jbrowse/core/util'
//import { version } from '../package.json'
import { ReactComponent } from './VariantView'
import VariantWidget from './VariantWidget'
import VariantDisplay from './VariantDisplay'
import DisplayType from '@jbrowse/core/pluggableElementTypes/DisplayType'
import {
  createBaseTrackConfig,
  createBaseTrackModel,
} from '@jbrowse/core/pluggableElementTypes/models'

import AdapterType from "@jbrowse/core/pluggableElementTypes/AdapterType";

export default class VariantPlugin extends Plugin {
  name = 'VariantPlugin'
  version = "0.0.1"//version

  install(pluginManager: PluginManager) {
    console.log("Installing plugins")
    const { jbrequire } = pluginManager
    const { types } = pluginManager.lib['mobx-state-tree']

    const ViewType = jbrequire('@jbrowse/core/pluggableElementTypes/ViewType')
    const WidgetType = jbrequire('@jbrowse/core/pluggableElementTypes/WidgetType')
    const LGVPlugin = pluginManager.getPlugin('LinearGenomeViewPlugin',) as import('@jbrowse/plugin-linear-genome-view').default
    const { BaseLinearDisplayComponent } = LGVPlugin.exports

    const stateModel = types
      .model({ type: types.literal('VariantView') })
      .actions(() => ({
        setWidth() {
          // unused but required by your view
        },
      }))

    pluginManager.addDisplayType(() => {
      const { configSchema, stateModel } = pluginManager.load(VariantDisplay)
      return new DisplayType({
        name: 'VariantDisplay',
        configSchema,
        stateModel,
        trackType: 'VariantTrack',
        viewType: 'LinearGenomeView',
        ReactComponent: BaseLinearDisplayComponent,
      })
    })

    pluginManager.addWidgetType(() => {
      const {
        configSchema,
        ReactComponent,
        stateModel,
      } = pluginManager.load(VariantWidget)

      return new WidgetType({
        name: 'VariantWidget',
        heading: 'Feature details',
        configSchema,
        stateModel,
        ReactComponent,
      })
    })

    pluginManager.addViewType(() => {
      return new ViewType({ name: 'VariantView', stateModel, ReactComponent })
    })
  }

  configure(pluginManager: PluginManager) {
    if (isAbstractMenuManager(pluginManager.rootModel)) {
      // @ts-ignore
      pluginManager.rootModel.appendToSubMenu(['File', 'Add'], {
        label: 'Open Hello!',
        // @ts-ignore
        onClick: session => {
          session.addView('VariantView', {})
        },
      })
    }
  }
  
}
