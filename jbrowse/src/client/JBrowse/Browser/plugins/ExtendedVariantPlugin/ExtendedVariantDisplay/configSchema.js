import { ConfigurationSchema } from '@jbrowse/core/configuration'
import {types} from "mobx-state-tree";

export const variantDetailsConfig = ConfigurationSchema('VariantDetailsConfig', {
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

export default pluginManager => {
  const { baseLinearDisplayConfigSchema } = pluginManager.getPlugin(
    'LinearGenomeViewPlugin',
  ).exports
  return ConfigurationSchema(
    'ExtendedVariantDisplay',
    {
      mouseover: {
          type: 'string',
          description: 'what to display in a given mouseover',
          defaultValue: `jexl:get(feature,'name')`,

          contextVariable: ['feature'],
      },
      renderer: pluginManager.pluggableConfigSchemaType('renderer'),
      detailsConfig: variantDetailsConfig
    },
    { baseConfiguration: baseLinearDisplayConfigSchema, explicitlyTyped: true },
  )
}
