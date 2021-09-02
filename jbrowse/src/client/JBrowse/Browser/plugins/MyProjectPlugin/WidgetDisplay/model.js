import { ConfigurationReference } from '@jbrowse/core/configuration'
import { getParentRenderProps } from '@jbrowse/core/util/tracks'
import { getSession } from '@jbrowse/core/util'
import FilterListIcon from '@material-ui/icons/FilterList'
import configSchemaF from './configSchema'
import {getSnapshot} from "mobx-state-tree";

export default jbrowse => {
  const { types } = jbrowse.jbrequire('mobx-state-tree')

  const configSchema = jbrowse.jbrequire(configSchemaF)

  const { BaseLinearDisplay } = jbrowse.getPlugin(
    'LinearGenomeViewPlugin',
  ).exports

  return types
    .compose(
      'WidgetDisplay',
      BaseLinearDisplay,
      types.model({
        type: types.literal('WidgetDisplay'),
        configuration: ConfigurationReference(configSchema),
      }),
    )

    .actions(self => ({
      selectFeature(feature) {
        const session = getSession(self)
        const featureWidget = session.addWidget(
          'HelloWidget',
          'hWidget',
          { featureData: feature.toJSON() },
        )
        session.showWidget(featureWidget)
        session.setSelection(feature)
      },
    }))

    .views(self => {
      const { renderProps: superRenderProps } = self
      const { trackMenuItems: superTrackMenuItems } = self

      return {
        renderProps() {
          return {
            ...superRenderProps(),
            config: self.configuration.renderer,
          }
        },

        get rendererTypeName() {
          return self.configuration.renderer.type
        },

        trackMenuItems() {
          return [
            {
              label: 'Get Session',
              onClick: ()  => {
                console.log(getSnapshot(getSession(self)))
              },
              icon: FilterListIcon,
            },
          ]
        }
      }
    })
}