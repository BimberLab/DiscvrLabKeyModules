import BoxRendererType from '@jbrowse/core/pluggableElementTypes/renderers/BoxRendererType';

export { default as ReactComponent } from './components/ExtendedVariantRenderering'
export { default as configSchema } from './configSchema'

export class ExtendedVariantRenderer extends BoxRendererType {
    supportsSVG = true
}