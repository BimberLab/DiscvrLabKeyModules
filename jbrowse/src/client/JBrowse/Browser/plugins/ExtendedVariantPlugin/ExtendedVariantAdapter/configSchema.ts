import { ConfigurationSchema } from '@jbrowse/core/configuration'
import configSchema from '@jbrowse/plugin-variants/src/VcfTabixAdapter/configSchema'
import { types } from 'mobx-state-tree'

let EVConfigSchema = configSchema.jbrowseSchemaDefinition
EVConfigSchema["filters"] = {
   type: 'stringArray',
   defaultValue: [],
   description: 'Active track filters',
}

//TODO: add this eventually
// EVConfigSchema["colorMap"] = {
//    type: types.map,
//    defaultValue: {},
//    description: 'Variant color mapping',
// }

export default ConfigurationSchema(
  'ExtendedVariantAdapter',
  EVConfigSchema,
  { explicitlyTyped: true },
)
