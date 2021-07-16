import { ConfigurationSchema } from '@jbrowse/core/configuration'

export default pluginManager => {
  const { baseLinearDisplayConfigSchema } = pluginManager.getPlugin(
    'LinearGenomeViewPlugin',
  ).exports
  return ConfigurationSchema(
    'ExtendedVariantDisplay',
    { renderer: pluginManager.pluggableConfigSchemaType('renderer') },
    { baseConfiguration: baseLinearDisplayConfigSchema, explicitlyTyped: true },
  )
}
