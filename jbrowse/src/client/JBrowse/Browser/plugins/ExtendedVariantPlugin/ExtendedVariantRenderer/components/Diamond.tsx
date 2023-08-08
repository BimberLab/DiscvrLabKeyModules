import { readConfObject } from '@jbrowse/core/configuration';
import { PropTypes as CommonPropTypes } from '@jbrowse/core/util/types/mst';
import { emphasize } from '@jbrowse/core/util/color';
import { observer } from 'mobx-react';
import ReactPropTypes from 'prop-types';
import React from 'react';
import { Feature } from '@jbrowse/core/util/simpleFeature';

const utrHeightFraction = 0.65

function isUTR(feature: Feature) {
  return /(\bUTR|_UTR|untranslated[_\s]region)\b/.test(
      feature.get('type') || '',
  )
}

function Diamond(props) {
  const {
    feature,
    bpPerPx,
    region,
    config,
    featureLayout,
    selected,
    reversed,
  } = props

  const screenWidth = (region.end - region.start) / bpPerPx
  const width = Math.max(featureLayout.absolute.width, 1)
  const { left } = featureLayout.absolute
  let { top, height } = featureLayout.absolute
  if (isUTR(feature)) {
    top += ((1 - utrHeightFraction) / 2) * height
    height *= utrHeightFraction
  }

  // @ts-ignore
  const color = readConfObject(config, (isUTR(feature) ? 'color3' : 'color1'), { feature })

  let emphasizedColor
  try {
    emphasizedColor = emphasize(color, 0.3)
  } catch (error) {
    emphasizedColor = color
  }
  const color2 = readConfObject(config, 'color2', { feature })

  if (left + width < 0) {
    return null
  }
  const leftWithinBlock = Math.max(left, 0)
  const diff = leftWithinBlock - left
  const widthWithinBlock = Math.max(1, Math.min(width - diff, screenWidth))

  return (
    <>
        <polygon
          stroke={selected ? color2 : undefined}
          fill={selected ? emphasizedColor : color}
          points={[
            [left + (width)/2, top].join(","),
            [left + (width)/2 + (height/2), top + height / 2].join(","),
            [left + (width)/2, top + height].join(","),
            [left + (width)/2 - (height/2), top + height / 2].join(","),
          ].join(" ")}
        />
    </>
  )
}

Diamond.propTypes = {
  feature: ReactPropTypes.shape({
    id: ReactPropTypes.func.isRequired,
    get: ReactPropTypes.func.isRequired,
  }).isRequired,
  region: CommonPropTypes.Region.isRequired,
  bpPerPx: ReactPropTypes.number.isRequired,
  featureLayout: ReactPropTypes.shape({
    absolute: ReactPropTypes.shape({
      top: ReactPropTypes.number.isRequired,
      left: ReactPropTypes.number.isRequired,
      width: ReactPropTypes.number.isRequired,
      height: ReactPropTypes.number.isRequired,
    }),
  }).isRequired,
  selected: ReactPropTypes.bool,
  config: CommonPropTypes.ConfigSchema.isRequired,
  reversed: ReactPropTypes.bool,
}

Diamond.defaultProps = {
  selected: false,
  reversed: false,
}

export default observer(Diamond)
