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
            try{
                self.renderProps().config.filters.set(filter)
            } catch (e){
                console.error(e)
            }
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
                        /*const colorSNV = self.colorSNV ?? 'blue'
                        const colorDeletion = self.colorDeletion ?? 'red'
                        const colorInsertion = self.colorInsertion ?? 'green'
                        const colorOther = self.colorOther ?? 'gray'
                        const colorHigh = self.colorHigh ?? 'red'
                        const colorModerate = self.colorModerate ?? 'goldenrod'
                        const colorLow = self.colorLow ?? 'black'*/
                        //const color = self.renderProps().config.color1.value
                        //const color = "jexl:get(feature,'INFO').IMPACT=='MODERATE'?'"+colorModerate+"':get(feature,'INFO').IMPACT=='HIGH'?'"+colorHigh+"':get(feature,'INFO').IMPACT=='LOW'?'"+colorLow+"':get(feature,'type')=='SNV'?'"+colorSNV+"':get(feature,'type')=='deletion'?'"+colorDeletion+"':get(feature,'type')=='insertion'?'"+colorInsertion+"':'"+colorOther+"'"
                        //const color = "jexl:get(feature,'INFO').IMPACT=='MODERATE'?'goldenrod':get(feature,'INFO').IMPACT=='HIGH'?'red':get(feature,'INFO').IMPACT=='LOW'?'black':get(feature,'type')=='SNV'?'blue':get(feature,'type')=='deletion'?'red':get(feature,'type')=='insertion'?'green':'gray'"

                        console.log("In renderProps in display/model.js - " + self.renderProps().config.colorJexl.value)
                        //const color = "jexl:get(feature,'INFO').IMPACT=='HIGH'?'red':get(feature,'INFO').IMPACT=='MODERATE'?'goldenrod':get(feature,'INFO').IMPACT=='LOW'?'#049931':'gray'"
                        //const color = "jexl:get(feature,'INFO').AF[0]<=0?'#898989':(get(feature,'INFO').AF[0]>0.1 && get(feature,'INFO').AF[0]<=0.2)?'#9F706E':(get(feature,'INFO').AF[0]>0.2 && get(feature,'INFO').AF[0]<=0.30000000000000004)?'#AB6460':(get(feature,'INFO').AF[0]>0.30000000000000004 && get(feature,'INFO').AF[0]<=0.4)?'#B65752':(get(feature,'INFO').AF[0]>0.4 && get(feature,'INFO').AF[0]<=0.5)?'#C14B45':(get(feature,'INFO').AF[0]>0.5 && get(feature,'INFO').AF[0]<=0.6000000000000001)?'#CC3E37':(get(feature,'INFO').AF[0]>0.6000000000000001 && get(feature,'INFO').AF[0]<=0.7000000000000001)?'#D73129':(get(feature,'INFO').AF[0]>0.7000000000000001 && get(feature,'INFO').AF[0]<=0.8)?'#E3251B':(get(feature,'INFO').AF[0]>0.8 && get(feature,'INFO').AF[0]<=0.9)?'#EE190E':(get(feature,'INFO').AF[0]>0.9 && get(feature,'INFO').AF[0]<=1)?'#F90C00':get(feature,'INFO').AF[0]>=1?'#F90C00':'gray'"
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
            icon: FilterListIcon,
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