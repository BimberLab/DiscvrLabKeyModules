import { ConfigurationSchema } from '@jbrowse/core/configuration'
import configSchema from '@jbrowse/plugin-variants/src/VcfTabixAdapter/configSchema'

let EVConfigSchema = configSchema.jbrowseSchemaDefinition
EVConfigSchema["filters"] =
{
   type: 'stringArray',
   defaultValue: [],
   description:
    'Track filters',
}
EVConfigSchema["sampleFilters"] =
{
   type: 'string',
   defaultValue: '',
   description:
      'comma-delineated string of sample IDs to filter by'
}

export default ConfigurationSchema(
  'ExtendedVariantAdapter',
  EVConfigSchema,
  { explicitlyTyped: true },
)
