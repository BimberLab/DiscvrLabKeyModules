import {ConfigurationReference, getConf, readConfObject} from '@jbrowse/core/configuration'
import {getContainingTrack, getSession} from '@jbrowse/core/util'
import FilterListIcon from '@material-ui/icons/FilterList'
import configSchemaF from './configSchema'
import {getSnapshot, types} from 'mobx-state-tree'
import PaletteIcon from '@material-ui/icons/Palette'

export default jbrowse => {
   const configSchema = jbrowse.jbrequire(configSchemaF)
   const { BaseLinearDisplay } = jbrowse.getPlugin(
      'LinearGenomeViewPlugin',
   ).exports

   return types
      .compose(
         'ExtendedVariantDisplay',
         BaseLinearDisplay,
         types.model({
            type: types.literal('ExtendedVariantDisplay'),
            configuration: ConfigurationReference(configSchema),
         }),
      )
      .actions(self => ({
         selectFeature(feature){
            const session = getSession(self)
            const track = getContainingTrack(self)

            const trackId = getConf(track, 'trackId')
            const detailsConfig = getConf(track, ['displays', '0', 'detailsConfig'])

            const widgetId = 'Variant-' + trackId;
            const featureWidget = session.addWidget(
               'ExtendedVariantWidget',
               widgetId,
               {
                  featureData: feature,
                  trackId: trackId,
                  message: '',
                  detailsConfig: detailsConfig
               }
            )

            session.showWidget(featureWidget)
            session.setSelection(feature)
         },
      }))

      .views(self => {
         const { renderProps: superRenderProps } = self
         const filterMenu = {
            label: 'Filter By Attributes',
            icon: FilterListIcon,
            onClick: () => {
               const session = getSession(self)
               const track = getContainingTrack(self)
               const widgetId = 'Variant-' + getConf(track, 'trackId');
               const filterWidget = session.addWidget(
                  'InfoFilterWidget',
                  widgetId,
                  { track: track.configuration }
               )
               session.showWidget(filterWidget)
            }
         }
         const colorMenu = {
            label: "Color Selection",
            icon: PaletteIcon,
            onClick: () => {
               const session = getSession(self)
               const track = getContainingTrack(self)
               const widgetId = 'Variant-' + getConf(track, 'trackId');
               const colorWidget = session.addWidget(
                  'ColorWidget',
                  widgetId,
                  { track: track.configuration }
               )
               session.showWidget(colorWidget)
            }
         }

         const sampleFilterMenu = {
            label: 'Filter By Sample',
            icon: FilterListIcon,
            onClick: () => {
               const session = getSession(self)
               const track = getContainingTrack(self)
               const widgetId = 'Variant-' + getConf(track, 'trackId');
               const sampleFilterWidget = session.addWidget(
                  'SampleFilterWidget',
                  widgetId,
                  { track: track.configuration }
               )
               session.showWidget(sampleFilterWidget)
            }
         }

         return {
            renderProps() {
               return {
                  ...superRenderProps(),
                  config: self.configuration.renderer,
                  rendererConfig: self.configuration.renderer
               }
            },

            get rendererTypeName() {
               return self.configuration.renderer.type
            },

            trackMenuItems() {
               return [
                  filterMenu, sampleFilterMenu, colorMenu
              ]
            }
         }
      })
}