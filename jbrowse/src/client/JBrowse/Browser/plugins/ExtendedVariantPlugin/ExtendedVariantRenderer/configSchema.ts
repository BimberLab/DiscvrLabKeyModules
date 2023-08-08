import { ConfigurationSchema } from '@jbrowse/core/configuration';
import { svgFeatureRendererConfigSchema} from '@jbrowse/plugin-svg'

import { generateSchemeJexl } from "../ColorWidget/colorUtil";

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
        supportsLuceneIndex: {
            type: 'boolean',
            defaultValue: false
        }
    },
    {
        explicitlyTyped: true,
        baseConfiguration: svgFeatureRendererConfigSchema,
        preProcessSnapshot: s => {
            const snap = JSON.parse(JSON.stringify(s))
            snap.palette = snap.palette || 'IMPACT'
            if (snap.palette) {
                snap.color1 = generateSchemeJexl(snap.palette)
            }

            return {...snap}
        }
    }
)
