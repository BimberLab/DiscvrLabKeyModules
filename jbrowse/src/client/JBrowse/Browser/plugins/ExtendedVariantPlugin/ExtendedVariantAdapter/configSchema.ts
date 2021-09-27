import { types } from 'mobx-state-tree'
import { ConfigurationSchema } from '@jbrowse/core/configuration'
import configSchema from '@jbrowse/plugin-variants/src/VcfTabixAdapter/configSchema'

let EVConfigSchema = configSchema.jbrowseSchemaDefinition
EVConfigSchema["filters"] =
{
   type: 'string',
   defaultValue: '{}',
   description:
    'Track filters',
}

export default ConfigurationSchema(
  'ExtendedVariantAdapter',
  EVConfigSchema,
  { explicitlyTyped: true },
)
