import { ConfigurationReference } from '@jbrowse/core/configuration'
import { getParentRenderProps } from '@jbrowse/core/util/tracks'
import { getContainingTrack, getSession } from '@jbrowse/core/util'
import FilterListIcon from '@material-ui/icons/FilterList'
import configSchemaF from './configSchema'

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
        type: types.literal('ExtendedVariantDisplay'),
        configuration: ConfigurationReference(configSchema),
      }),
    )

    .actions(self => ({
      selectFeature(feature) {
        var extendedVariantDisplayConfig
        if(getContainingTrack(self).configuration.metadata.value.extendedVariantDisplayConfig){
            extendedVariantDisplayConfig = getContainingTrack(self).configuration.metadata.value.extendedVariantDisplayConfig
        }
        else{
            extendedVariantDisplayConfig = []
        }

        var message
        if(getContainingTrack(self).configuration.metadata.value.message){
            message = getContainingTrack(self).configuration.metadata.value.message
        }
        else{
            message = ""
        }
        const trackId = getContainingTrack(self).configuration.trackId
        const session = getSession(self)
        var widgetId = 'Variant-' + trackId;

        const featureWidget = session.addWidget(
          'ExtendedVariantWidget',
          widgetId,
          { featureData: feature.toJSON(),
            extendedVariantDisplayConfig: extendedVariantDisplayConfig,
            message: message }
        )
        session.showWidget(featureWidget)
        session.setSelection(feature)
      },
    }))

    .views(self => ({
      get renderProps() {
        return {
          ...self.composedRenderProps,
          ...getParentRenderProps(self),
          config: self.configuration.renderer,
        }
      },

      get rendererTypeName() {
        return self.configuration.renderer.type
      },

      get trackMenuItems() {
        return [
          {
            label: 'Filter',
            onClick: self.openFilterConfig,
            icon: FilterListIcon,
          },
        ]
      },
    }))
}