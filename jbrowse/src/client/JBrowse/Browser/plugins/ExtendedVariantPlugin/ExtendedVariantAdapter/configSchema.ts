import { types } from 'mobx-state-tree'
import { ConfigurationSchema } from '@jbrowse/core/configuration'

// TODO: avoid this duplication
const ExtendedVariantAdapter = ConfigurationSchema(
    'ExtendedVariantAdapter',
    {
        /**
         * #slot
         */
        vcfGzLocation: {
            type: 'fileLocation',
            defaultValue: { uri: '/path/to/my.vcf.gz', locationType: 'UriLocation' },
        },
        index: ConfigurationSchema('VcfIndex', {
            /**
             * #slot index.indexType
             */
            indexType: {
                model: types.enumeration('IndexType', ['TBI', 'CSI']),
                type: 'stringEnum',
                defaultValue: 'TBI',
            },
            /**
             * #slot index.location
             */
            location: {
                type: 'fileLocation',
                defaultValue: {
                    uri: '/path/to/my.vcf.gz.tbi',
                    locationType: 'UriLocation',
                },
            },
        }),
    },
    { explicitlyTyped: true },
)

export default ExtendedVariantAdapter
