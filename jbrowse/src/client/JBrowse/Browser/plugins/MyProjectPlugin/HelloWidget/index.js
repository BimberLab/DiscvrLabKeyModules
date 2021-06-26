import { ConfigurationSchema } from '@jbrowse/core/configuration'
import { ElementId } from '@jbrowse/core/util/types/mst'
import HelloWidget from './HelloWidget'

export default jbrowse => {
  const { types } = jbrowse.jbrequire('mobx-state-tree')
  const configSchema = ConfigurationSchema('HelloWidget', {})
  const stateModel = types
    .model('HelloWidget', {
      id: ElementId,
      type: types.literal('HelloWidget'),
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

  const ReactComponent = jbrowse.jbrequire(HelloWidget)

  return { configSchema, stateModel, ReactComponent }
}
