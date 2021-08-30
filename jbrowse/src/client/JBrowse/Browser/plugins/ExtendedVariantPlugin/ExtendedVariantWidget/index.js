import { ConfigurationSchema } from '@jbrowse/core/configuration'
import { ElementId } from '@jbrowse/core/util/types/mst'
import ExtendedVariantWidget from './ExtendedVariantWidget'

export default jbrowse => {
  const { types } = jbrowse.jbrequire('mobx-state-tree')
  const configSchema = ConfigurationSchema('ExtendedVariantWidget', {})
  const stateModel = types
    .model('ExtendedVariantWidget', {
      id: ElementId,
      type: types.literal('ExtendedVariantWidget'),
      featureData: types.frozen({}),
      extendedVariantDisplayConfig: types.frozen([]),
      message: types.string

    })
    .actions(self => ({
      setFeatureData(data) {
        self.featureData = data
      },
      clearFeatureData() {
        self.featureData = {}
      }
    }))

  const ReactComponent = jbrowse.jbrequire(ExtendedVariantWidget)

  return { configSchema, stateModel, ReactComponent }
}
