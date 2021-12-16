import { ConfigurationSchema } from '@jbrowse/core/configuration'
import { ElementId } from '@jbrowse/core/util/types/mst'
import ColorWidget from './ColorWidget'

export default jbrowse => {
    const { types } = jbrowse.jbrequire('mobx-state-tree')
    const configSchema = ConfigurationSchema('ColorWidget', {})
    const stateModel = types
            .model('ColorWidget', {
                id: ElementId,
                type: types.literal('ColorWidget'),
                track: types.safeReference(jbrowse.pluggableConfigSchemaType('track'))
            })


    const ReactComponent = jbrowse.jbrequire(ColorWidget)

    return { configSchema, stateModel, ReactComponent }
}
