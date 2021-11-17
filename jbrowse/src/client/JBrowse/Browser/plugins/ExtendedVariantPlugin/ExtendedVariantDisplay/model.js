import { ConfigurationReference, getConf } from '@jbrowse/core/configuration'
import { getParentRenderProps, getRpcSessionId } from '@jbrowse/core/util/tracks'
import { getContainingTrack, getSession, getContainingView } from '@jbrowse/core/util'
import FilterListIcon from '@material-ui/icons/FilterList'
import configSchemaF from './configSchema'
import { cast, types, addDisposer, getEnv, Instance } from 'mobx-state-tree'
import { autorun, observable } from 'mobx'
import PaletteIcon from '@material-ui/icons/Palette'
import PluginManager from '@jbrowse/core/PluginManager'
import {getSnapshot} from 'mobx-state-tree'


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
         setReady(flag){
            self.ready = flag
         },
         setFilter(filter){
            try{
                self.renderProps().config.filters.set(filter)
            } catch (e){
                console.error(e)
            }
         }
      }))
      .actions(self => ({
         afterAttach() {
            addDisposer(
               self,
               autorun(
                  async () => {
                     try {
                        const { rpcManager } = getSession(self)
                        const color = self.renderProps().config.colorJexl.value

                        if (self.renderProps().config.color1.value != color || self.ready == false || self.adapterConfig.filters != self.renderProps().config.filters.value){
                           self.renderProps().config.color1.set(color)
                           self.setFilter(self.adapterConfig.filters)

                           const { centerLineInfo } = getContainingView(self)
                           if (!centerLineInfo) {
                                console.error('error! centerLineInfo is null')
                                return;
                           }

                           const { refName, assemblyName, offset } = centerLineInfo
                           const centerBp = Math.round(offset) + 1

                           const region = {
                              start: centerBp,
                              end: (centerBp || 0) + 1,
                              refName,
                              assemblyName,
                           }

                           await (self.rendererType).renderInClient(rpcManager, {
                              assemblyName,
                              regions: [region],
                              adapterConfig: self.adapterConfig,
                              rendererType: self.rendererType.name,
                              sessionId: getRpcSessionId(self),
                              timeout: 1000000,
                              ...self.renderProps(),
                           })
                           self.setReady(true)
                        } else {
                           self.setReady(true)
                        }
                     } catch (error) {
                        console.error(error)
                        self.setError(error)
                     }
                  },
                  { delay: 1000 },
               ),
            )
         },
         selectFeature(feature){
            const track = getContainingTrack(self)
            const metadata = getConf(track, 'metadata')
            var extendedVariantDisplayConfig
            if (metadata.extendedVariantDisplayConfig){
               extendedVariantDisplayConfig = metadata.extendedVariantDisplayConfig
            }
            else {
               extendedVariantDisplayConfig = []
            }

            var message
            if (metadata.message){
               message = metadata.message
            }
            else {
               message = ""
            }
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
            label: 'Filter',
            icon: FilterListIcon,
            onClick: () => {
               const session = getSession(self)
               const track = getContainingTrack(self)
               const widgetId = 'Variant-' + getConf(track, 'trackId');
               const filterWidget = session.addWidget(
                  'FilterWidget',
                  widgetId,
                  { track: track.configuration }
               )
               session.showWidget(filterWidget)
            }
         }
         const colorMenu = {
            label: "Color",
            icon: PaletteIcon,
            onClick: () => {
               const session = getSession(self)
               const track = getContainingTrack(self)
               const widgetId = 'Variant-' + getConf(track, 'trackId');
               const colorWidget = session.addWidget(
                  'ColorWidget',
                  widgetId,
                  {track: track.configuration}
               )
               session.showWidget(colorWidget)
            }
         }
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

            get composedTrackMenuItems() {
               return [filterMenu, colorMenu]
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
         }
      })
}