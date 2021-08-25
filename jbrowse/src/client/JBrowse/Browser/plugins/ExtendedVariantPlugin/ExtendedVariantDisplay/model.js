import { ConfigurationReference } from '@jbrowse/core/configuration'
import { getParentRenderProps, getRpcSessionId } from '@jbrowse/core/util/tracks'
import { getContainingTrack, getSession, getContainingView } from '@jbrowse/core/util'
import FilterListIcon from '@material-ui/icons/FilterList'
import configSchemaF from './configSchema'
import { cast, types, addDisposer, getEnv, Instance } from 'mobx-state-tree'
import { autorun, observable } from 'mobx'
import PaletteIcon from '@material-ui/icons/Palette'
import PluginManager from '@jbrowse/core/PluginManager'

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
        colorInsertion: types.maybe(types.string)
      }),
    )
    .actions(self => ({
       setReady(flag) {
          self.ready = flag
        },
        setSNV(color) {
          self.colorSNV = color
        },
        setInsertion(color) {
          self.colorInsertion = color
        },
        setDeletion(color) {
          self.colorDeletion = color
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
                const color = "jexl:get(feature,'type')=='SNV'?'"+colorSNV+"':get(feature,'type')=='deletion'?'"+colorDeletion+"':get(feature,'type')=='insertion'?'"+colorInsertion+"':'gray'"
                renderProps.config.color1.set(color)
                const view = getContainingView(self)

                if (self.colorSNV || self.colorDeletion || self.colorInsertion) {
                  const { centerLineInfo } = getContainingView(self)
                  const { refName, assemblyName, offset } = centerLineInfo
                  const centerBp = Math.round(offset) + 1

                  const region = {
                    start: centerBp,
                    end: (centerBp || 0) + 1,
                    refName,
                    assemblyName,
                  }

                  console.log("starting await")
                  await (self.rendererType).renderInClient(rpcManager, {
                    assemblyName,
                    regions: [region],
                    adapterConfig: self.adapterConfig,
                    rendererType: self.rendererType.name,
                    sessionId: getRpcSessionId(self),
                    timeout: 1000000,
                    ...renderProps,
                  })
                  console.log('await done')
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
        get composedTrackMenuItems() {
          return [
            {
              label: 'Color',
              icon: PaletteIcon,
              subMenu: [
                ...['red', 'green', 'blue'].map(
                  option => {
                    return {
                      label: option,
                      onClick: () => {
                        self.setSNV(option)
                        self.setDeletion(option)
                        self.setInsertion(option)
                        self.ready = false
                      },
                      }
                    },
                  ),
                ],
             },
             ]},

      get trackMenuItems() {
        return [
          ...this.composedTrackMenuItems
        ]
      },
    }))
}