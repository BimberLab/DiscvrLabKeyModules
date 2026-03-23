import { ConfigurationSchema } from '@jbrowse/core/configuration'
import {types} from "@jbrowse/mobx-state-tree";


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
      renderer: types.optional(
        pluginManager.pluggableConfigSchemaType('renderer'),
        { type: 'CanvasFeatureRenderer' },
      ),
      detailsConfig: variantDetailsConfig,
      infoFilters: {
          type: 'stringArray',
          description: 'the active filter set by the user',
          defaultValue: []
      },
      activeSamples: {
          type: 'string',
          defaultValue: '',
          description: 'comma-delineated string of sample IDs to filter'
      },
      supportsLuceneIndex: {
          type: 'boolean',
          defaultValue: false
      },
      palette: {
          type: 'string',
          description: 'The names of the palette to use for coloring features',
          defaultValue: 'IMPACT',
      }
    },
    { baseConfiguration: baseLinearDisplayConfigSchema, explicitlyTyped: true },
  )
}
