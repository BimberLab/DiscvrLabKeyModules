import { ConfigurationSchema } from '@jbrowse/core/configuration'
import {types} from "mobx-state-tree";

export default pluginManager => {
  const { baseLinearDisplayConfigSchema } = pluginManager.getPlugin(
    'LinearGenomeViewPlugin',
  ).exports
  return ConfigurationSchema(
    'ExtendedVariantDisplay',
    {
      renderer: pluginManager.pluggableConfigSchemaType('renderer'),
      detailsConfig: ConfigurationSchema('VariantDetailsConfig', {
        sections: types.array(ConfigurationSchema('VariantDetailsSection', {
          title: {
            type: 'string',
            description: 'The title for this section',
            defaultValue: ''
          },
          properties: {
            type: 'stringArray',
            description: 'The list of INFO attributes to display',
            defaultValue: []
          },
        })),
        message: {
          type: 'string',
          description: 'Additional text that will appear at the top of the details view',
          defaultValue: ''
        }
      })
    },
    { baseConfiguration: baseLinearDisplayConfigSchema, explicitlyTyped: true },
  )
}
