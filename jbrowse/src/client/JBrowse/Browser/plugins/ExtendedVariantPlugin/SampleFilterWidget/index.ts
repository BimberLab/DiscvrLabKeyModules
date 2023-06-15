import { ConfigurationSchema } from '@jbrowse/core/configuration'
import { ElementId } from '@jbrowse/core/util/types/mst'
import SampleFilterWidget from './SampleFilterWidget'

export default jbrowse => {
  const { types } = jbrowse.jbrequire('mobx-state-tree')
  const configSchema = ConfigurationSchema('SampleFilterWidget', {})
  const stateModel = types
    .model('SampleFilterWidget', {
      id: ElementId,
      type: types.literal('SampleFilterWidget'),
      track: types.safeReference(jbrowse.pluggableConfigSchemaType('track'))
     })


  const ReactComponent = jbrowse.jbrequire(SampleFilterWidget)

  return { configSchema, stateModel, ReactComponent }
}
