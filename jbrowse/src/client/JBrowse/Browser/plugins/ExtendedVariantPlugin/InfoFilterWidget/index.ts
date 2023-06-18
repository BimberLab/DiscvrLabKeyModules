import { ConfigurationSchema } from '@jbrowse/core/configuration'
import { ElementId } from '@jbrowse/core/util/types/mst'
import InfoFilterWidget from './InfoFilterWidget'

export default jbrowse => {
  const { types } = jbrowse.jbrequire('mobx-state-tree')
  const configSchema = ConfigurationSchema('InfoFilterWidget', {})
  const stateModel = types
    .model('InfoFilterWidget', {
      id: ElementId,
      type: types.literal('InfoFilterWidget'),
      track: types.safeReference(jbrowse.pluggableConfigSchemaType('track'))
     })

  const ReactComponent = jbrowse.jbrequire(InfoFilterWidget)

  return { configSchema, stateModel, ReactComponent }
}
