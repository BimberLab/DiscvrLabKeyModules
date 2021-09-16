import Plugin from '@jbrowse/core/Plugin'
import { isAbstractMenuManager } from '@jbrowse/core/util'
import InfoIcon from '@material-ui/icons/Info'
import { getSnapshot } from "mobx-state-tree";
import { getSession } from '@jbrowse/core/util'

export default class LogSession extends Plugin {
  name = 'LogSession'
  version = "0.0.1"

  install(pluginManager) {
    pluginManager.addToExtensionPoint(
      'Core-extendPluggableElement',
      (pluggableElement) => {
        if (pluggableElement.name === 'LinearGenomeView') {
          const {stateModel} = pluggableElement
          const newStateModel = stateModel.extend((self) => {
            const superMenuItems = self.menuItems
            return {
              views: {
                menuItems() {
                  const newMenuItems = [
                    ...superMenuItems(),
                    {type: 'divider'},
                    {
                      label: 'Log Session To Console',
                      icon: InfoIcon,
                      onClick: () => {
                        console.log(getSnapshot(getSession(self)))
                      },
                    },
                  ]
                  return newMenuItems
                },
              },
            }
          })

          pluggableElement.stateModel = newStateModel
        }
        return pluggableElement
      }
    )
  }
}