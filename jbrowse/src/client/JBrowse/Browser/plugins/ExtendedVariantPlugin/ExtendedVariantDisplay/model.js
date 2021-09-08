import { ConfigurationReference } from '@jbrowse/core/configuration'
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
       setReady(flag) {
          self.ready = flag
        },
        setColor(attr, color){
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
                const { renderProps } = self
                const colorSNV = self.colorSNV ?? 'green'
                const colorDeletion = self.colorDeletion ?? 'red'
                const colorInsertion = self.colorInsertion ?? 'blue'
                const colorOther = self.colorOther ?? 'gray'
                const colorHigh = self.colorHigh ?? 'red'
                const colorModerate = self.colorModerate ?? 'goldenrod'
                const colorLow = self.colorLow ?? 'black'
                const color = "jexl:get(feature,'INFO').ANN['IMPACT']=='MODERATE'?'"+colorModerate+"':get(feature,'INFO').ANN['IMPACT']=='HIGH'?'"+colorHigh+"':get(feature,'INFO').ANN['IMPACT']=='LOW'?'"+colorLow+"':get(feature,'type')=='SNV'?'"+colorSNV+"':get(feature,'type')=='deletion'?'"+colorDeletion+"':get(feature,'type')=='insertion'?'"+colorInsertion+"':'"+colorOther+"'"
                renderProps.config.color1.set(color)
                const view = getContainingView(self)

                if (self.colorSNV || self.colorDeletion || self.colorInsertion || self.colorHigh || self.colorModerate || self.colorOther) {
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
                    ...renderProps,
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
      selectFeature(feature) {

        var extendedVariantDisplayConfig
        if(getContainingTrack(self).configuration.metadata.value.extendedVariantDisplayConfig){
            extendedVariantDisplayConfig = getContainingTrack(self).configuration.metadata.value.extendedVariantDisplayConfig
        }
        else{
            extendedVariantDisplayConfig = []
        }

        var message
        if (getContainingTrack(self).configuration.metadata.value.message){
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

<<<<<<< HEAD
        get composedTrackMenuItems() {
          return [
            {
              label: 'Color',
              icon: PaletteIcon,
              subMenu: [
                ...attributes.map(
                  option => {
                    return {
                      label: option,
                      subMenu: [
                        ...colors.map(
                            color => {
                                return {
                                    label: color,
                                    onClick: () => {
                                        self.setColor(option, color)
                                        self.ready = false
                                    }
                                }
                            }
                        )
                      ]
                    }
                  },
                ),
              ],
             },
             ]},

      get trackMenuItems() {
        return [
          ...this.composedTrackMenuItems,
          {
            label: 'Get Session',
            onClick: ()  => {
              console.log(getSnapshot(getSession(self)))
            },
            icon: FilterListIcon,
          },
        ]
      },
    }))
=======
            trackMenuItems() {
                return [
                    ...superTrackMenuItems(),
                    {
                        label: 'Filter',
                        onClick: self.openFilterConfig,
                        icon: FilterListIcon,
                    },
                ]
            }
        }
    })
>>>>>>> discvr-21.3
}