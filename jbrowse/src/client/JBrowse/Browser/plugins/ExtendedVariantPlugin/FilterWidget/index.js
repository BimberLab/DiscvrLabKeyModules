import { ConfigurationSchema } from '@jbrowse/core/configuration'
import { ElementId } from '@jbrowse/core/util/types/mst'
import FilterWidget from './FilterWidget'

export default jbrowse => {
  const { types } = jbrowse.jbrequire('mobx-state-tree')
  const configSchema = ConfigurationSchema('FilterWidget', {})
  const stateModel = types
    .model('FilterWidget', {
      id: ElementId,
      type: types.literal('FilterWidget'),
      track: types.safeReference(jbrowse.pluggableConfigSchemaType('track'))
     })
    .actions(self => ({
      setFeatureData(data) {
        self.featureData = data
      },
      clearFeatureData() {
        self.featureData = {}
      }
    }))

  const ReactComponent = jbrowse.jbrequire(FilterWidget)

  return { configSchema, stateModel, ReactComponent }
}
