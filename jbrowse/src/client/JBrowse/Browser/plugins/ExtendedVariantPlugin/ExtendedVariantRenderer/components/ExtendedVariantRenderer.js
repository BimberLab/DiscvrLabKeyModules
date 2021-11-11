import React from 'react'
import { SvgFeatureRendererReactComponent } from '@jbrowse/plugin-svg'
import Diamond from './Diamond'
import { expandFilters, expandedFilterStringToObj, isFilterStringExpanded } from '../../FilterWidget/filterUtil'
import jexl from 'jexl'

function ExtendedVariantRenderer(props) {
  function diamondValidator(feature) {
    if(feature.get('type') === "SNV") {
      return true
    } else {
      return false
    }
  }

  function isFeatureDisplayed(feature) {
    const filters = expandFilters(props.adapterConfig.filters)

     if(!filters){
          return true
     }
     for(const filter in filters){
          try {
              if(isFilterStringExpanded(filters[filter])){
                  const filterObj = expandedFilterStringToObj(filters[filter])
                  if(!jexl.evalSync(filterObj["expression"], feature)){
                      return false
                  }
              } else {
                  continue
              }
          } catch (e){
              console.error("Error in filter execution: "+e)
              return true
          }
     }
     return true
  }

  function getDiamondValidator() {
    return {
      glyph: Diamond,
      validator: diamondValidator
    }
  }

  return (
    <SvgFeatureRendererReactComponent featureDisplayHandler={isFeatureDisplayed} extraGlyphs={[getDiamondValidator()]} {...props} />
  )
}

export default observer(ExtendedVariantRenderer)
