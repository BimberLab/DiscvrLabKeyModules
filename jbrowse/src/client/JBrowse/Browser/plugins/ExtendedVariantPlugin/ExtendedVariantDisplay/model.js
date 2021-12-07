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
            const track = getContainingTrack(self)
            //TODO: make proper schema
            const metadata = getConf(track, 'metadata')
            const metadata2 = readConfObject(track, 'extendedVariantDisplayConfig')
            console.log(metadata2)
            var extendedVariantDisplayConfig = metadata.extendedVariantDisplayConfig || []
            var message = metadata.message || ""

            const trackId = getConf(track, 'trackId')
            const session = getSession(self)
            var widgetId = 'Variant-' + trackId;
            const featureWidget = session.addWidget(
               'ExtendedVariantWidget',
               widgetId,
               { featureData: feature,
                   trackId: trackId,
                   extendedVariantDisplayConfig: extendedVariantDisplayConfig,
                   message: message
           })
            session.showWidget(featureWidget)
            session.setSelection(feature)
         },
      }))

      .views(self => {
         const { renderProps: superRenderProps } = self
         const { trackMenuItems: superTrackMenuItems } = self
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
            label: 'Filter by Sample',
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
                  rendererConfig: self.configuration.renderer,
                  filterConfig: self.configuration.filters,
                  palette: self.configuration.palette
               }
            },

            get rendererTypeName() {
               return self.configuration.renderer.type
            },

            get composedTrackMenuItems() {
               return [filterMenu, sampleFilterMenu, colorMenu]
            },

            trackMenuItems() {
               return [
                  ...this.composedTrackMenuItems,
                  {  label: 'Get Session',
                     onClick: ()  => {
                       console.log(getSnapshot(getSession(self)))
                     },
                     icon: FilterListIcon, },
               ]
            },

            get filters() {
               let filters = []
               console.log('filters called!!')

               return filters
            }
         }
      })
}