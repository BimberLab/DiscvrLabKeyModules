import { ConfigurationSchema } from '@jbrowse/core/configuration'
import { ElementId } from '@jbrowse/core/util/types/mst'
import VariantWidget from './VariantWidget'

export default jbrowse => {
  const { types } = jbrowse.jbrequire('mobx-state-tree')
  const configSchema = ConfigurationSchema('VariantWidget', {})
  const stateModel = types
    .model('VariantWidget', {
      id: ElementId,
      type: types.literal('VariantWidget'),
      featureData: types.frozen({}),
    })
    .actions(self => ({
      setFeatureData(data) {
        self.featureData = data
      },
      clearFeatureData() {
        self.featureData = {}
      },
    }))

  const ReactComponent = jbrowse.jbrequire(VariantWidget)

  return { configSchema, stateModel, ReactComponent }
}
