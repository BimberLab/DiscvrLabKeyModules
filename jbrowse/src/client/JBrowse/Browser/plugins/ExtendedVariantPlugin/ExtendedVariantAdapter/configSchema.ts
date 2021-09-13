import { types } from 'mobx-state-tree'
import { ConfigurationSchema } from '@jbrowse/core/configuration'
import configSchema from '@jbrowse/plugin-variants/src/VcfTabixAdapter/configSchema'

export default ConfigurationSchema(
  'ExtendedVariantAdapter',
  configSchema.jbrowseSchemaDefinition,
  { explicitlyTyped: true },
)
