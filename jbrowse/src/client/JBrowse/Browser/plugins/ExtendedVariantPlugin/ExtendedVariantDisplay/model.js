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

const attributes = ['SNV', 'Insertion', 'Deletion', 'High', 'Moderate', 'Low', 'Other']
const colors = ['green', 'red', 'blue', 'gray', 'goldenrod']
const filterOptions = ['Impact = HIGH', 'AF > 0.2', 'None']

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
            colorSNV: types.maybe(types.string),
            colorDeletion: types.maybe(types.string),
            colorInsertion: types.maybe(types.string),
            colorOther: types.maybe(types.string),
            colorModerate: types.maybe(types.string),
            colorHigh: types.maybe(types.string),
            colorLow: types.maybe(types.string)
         }),
      )
      .actions(self => ({
         setReady(flag){
            self.ready = flag
         },
         setFilter(filter){
            self.renderProps().config.filters.set(filter)
         },
         setColor(attr, color) {
            if (attr == "SNV"){
                self.colorSNV = color
            }
            else if (attr == "Insertion"){
                self.colorInsertion = color
            }
            else if (attr == "Deletion"){
                self.colorDeletion = color
            }
            else if (attr == "Other"){
                self.colorOther = color
            }
            else if (attr == "High"){
                self.colorHigh = color
            }
            else if (attr == "Moderate"){
                self.colorModerate = color
            }
            else if (attr == "Low"){
                self.colorLow = color
            }
         },
      }))
      .actions(self => ({
         afterAttach() {
            addDisposer(
               self,
               autorun(
                  async () => {
                     try {
                        const { rpcManager } = getSession(self)
                        const colorSNV = self.colorSNV ?? 'blue'
                        const colorDeletion = self.colorDeletion ?? 'red'
                        const colorInsertion = self.colorInsertion ?? 'green'
                        const colorOther = self.colorOther ?? 'gray'
                        const colorHigh = self.colorHigh ?? 'red'
                        const colorModerate = self.colorModerate ?? 'goldenrod'
                        const colorLow = self.colorLow ?? 'black'
                        const color = "jexl:get(feature,'INFO').IMPACT=='MODERATE'?'"+colorModerate+"':get(feature,'INFO').IMPACT=='HIGH'?'"+colorHigh+"':get(feature,'INFO').IMPACT=='LOW'?'"+colorLow+"':get(feature,'type')=='SNV'?'"+colorSNV+"':get(feature,'type')=='deletion'?'"+colorDeletion+"':get(feature,'type')=='insertion'?'"+colorInsertion+"':'"+colorOther+"'"

                        if (self.renderProps().config.color1.value != color || self.ready == false || self.adapterConfig.filters){
                           self.renderProps().config.color1.set(color)
                           self.renderProps().config.filters.set(self.adapterConfig.filters ?? "{}")
                           const { centerLineInfo } = getContainingView(self)
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
               { featureData: feature.toJSON(),
                 extendedVariantDisplayConfig: extendedVariantDisplayConfig,
                 message: message }
            )
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
               console.log(getContainingTrack(self))
               const filterWidget = session.addWidget(
                  'FilterWidget',
                  widgetId,
                  { track: track.configuration }
               )
               session.showWidget(filterWidget)
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
               return [filterMenu, {
                  label: 'Color',
                  icon: PaletteIcon,
                  subMenu: [...attributes.map(option => {
                     return {
                       label: option,
                       subMenu: [...colors.map(color => {
                          return {
                            label: color,
                            onClick: () => {
                                self.setColor(option, color)
                                self.ready = false
                            }
                          }
                       })]
                     }
                  })]
               }]
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