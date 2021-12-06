import { ConfigurationSchema } from '@jbrowse/core/configuration';
import { default as rendererSchema } from '@jbrowse/plugin-svg/src/SvgFeatureRenderer/configSchema';

export default ConfigurationSchema(
    'ExtendedVariantRenderer',
    {
        palette: {
            type: 'string',
            description: 'The names of the palette to use for coloring features',
            defaultValue: 'IMPACT',
        },
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
    },
    { explicitlyTyped: true, baseConfiguration: rendererSchema },
)
