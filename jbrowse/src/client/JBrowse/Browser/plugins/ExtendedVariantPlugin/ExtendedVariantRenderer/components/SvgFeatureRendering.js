// File copied and updated from original file in @jbrowse/plugin-svg/src/SvgFeatureRenderer/components

import { readConfObject } from '@jbrowse/core/configuration'
import { PropTypes as CommonPropTypes } from '@jbrowse/core/util/types/mst'
import { bpToPx, measureText } from '@jbrowse/core/util'
import SceneGraph from '@jbrowse/core/util/layouts/SceneGraph'
import { observer } from 'mobx-react'
import ReactPropTypes from 'prop-types'
import React, { useEffect, useRef, useState, useCallback } from 'react'
import FeatureGlyph from './FeatureGlyph' // FeatureGlyph copied over. Referencing original produces errors. Compare to line 11
import SvgOverlay from '@jbrowse/plugin-svg/src/SvgFeatureRenderer/components/SvgOverlay' // NEW: Updated SvgOverlay to reference original file in @jbrowse. No errors produced.
import { chooseGlyphComponent, layOut } from './util' // NEW: chooseGlyphComponent() in util updated to render SNVs as a diamond
import { expandFilters, expandedFilterStringToObj, isFilterStringExpanded } from '../../InfoFilterWidget/filterUtil' // NOTE: Now dependent on FilterWidget plugin
import jexl from 'jexl'
const renderingStyle = {
    position: 'relative',
}

// used to make features have a little padding for their labels
const nameWidthPadding = 2
const textVerticalPadding = 2

// used so that user can click-away-from-feature below the laid out features
// (issue #1248)
const svgHeightPadding = 100

function RenderedFeatureGlyph(props) {
    const { feature, bpPerPx, region, config, displayMode, layout } = props
    const { reversed } = region
    const start = feature.get(reversed ? 'end' : 'start')
    const startPx = bpToPx(start, region, bpPerPx)
    const labelsAllowed = displayMode !== 'compact' && displayMode !== 'collapsed'

    const rootLayout = new SceneGraph('root', 0, 0, 0, 0)
    const GlyphComponent = chooseGlyphComponent(feature)
    const featureLayout = (GlyphComponent.layOut || layOut)({
        layout: rootLayout,
        feature,
        bpPerPx,
        reversed,
        config,
    })
    let shouldShowName
    let shouldShowDescription
    let name
    let description
    let fontHeight
    let expansion
    if (labelsAllowed) {
        const showLabels = readConfObject(config, 'showLabels')
        fontHeight = readConfObject(config, ['labels', 'fontSize'], { feature })
        expansion = readConfObject(config, 'maxFeatureGlyphExpansion') || 0
        name = readConfObject(config, ['labels', 'name'], { feature }) || ''
        shouldShowName = /\S/.test(name) && showLabels

        description = readConfObject(config, ['labels', 'description'], { feature }) || ''
        shouldShowDescription = /\S/.test(description) && showLabels

        let nameWidth = 0
        if (shouldShowName) {
            nameWidth =
                    Math.round(
                            Math.min(measureText(name, fontHeight), rootLayout.width + expansion),
                    ) + nameWidthPadding
            rootLayout.addChild(
                    'nameLabel',
                    0,
                    featureLayout.bottom + textVerticalPadding,
                    nameWidth,
                    fontHeight,
            )
        }

        let descriptionWidth = 0
        if (shouldShowDescription) {
            const aboveLayout = shouldShowName
                    ? rootLayout.getSubRecord('nameLabel')
                    : featureLayout
            descriptionWidth =
                    Math.round(
                            Math.min(
                                    measureText(description, fontHeight),
                                    rootLayout.width + expansion,
                            ),
                    ) + nameWidthPadding
            rootLayout.addChild(
                    'descriptionLabel',
                    0,
                    aboveLayout.bottom + textVerticalPadding,
                    descriptionWidth,
                    fontHeight,
            )
        }
    }

    const topPx = layout.addRect(
            feature.id(),
            feature.get('start'),
            feature.get('start') + rootLayout.width * bpPerPx,
            rootLayout.height,
    )
    if (topPx === null) {
        return null
    }
    rootLayout.move(startPx, topPx)

    return (
            <FeatureGlyph
                    key={`svg-feature-${feature.id()}`}
                    feature={feature}
                    layout={layout}
                    rootLayout={rootLayout}
                    bpPerPx={bpPerPx}
                    config={config}
                    name={String(name)}
                    shouldShowName={shouldShowName}
                    description={String(description)}
                    shouldShowDescription={shouldShowDescription}
                    fontHeight={fontHeight}
                    allowedWidthExpansion={expansion}
                    reversed={region.reversed}
                    {...props}
            />
    )
}

function isDisplayed(feature, filters){
    if (!filters){
        return true
    }
    for (const filter in filters){
        try {
            if (isFilterStringExpanded(filters[filter])){
                const filterObj = expandedFilterStringToObj(filters[filter])
                if (!jexl.evalSync(filterObj["expression"], feature)){
                    return false
                }
            }
        } catch (e){
            console.error("Error in filter execution: " + e)
            return true
        }
    }
    return true
}

function isVariant(gt) {
    return !(gt === "./." || gt === ".|." || gt === "0/0" || gt === "0|0")
}

function containsSampleIDs(feature, sampleIDs){
    if (!sampleIDs || sampleIDs.length === 0) {
        return true
    }

    if (!feature.variant.SAMPLES || Object.keys(feature.variant.SAMPLES).length === 0) {
        return false
    }

    // Preferentially use pre-computed values:
    if (feature.variant.INFO._variableSamples) {
        for (const sampleId of sampleIDs) {
            if (feature.variant.INFO._variableSamples.indexOf(sampleId) > -1) {
                return true
            }
        }

        return false
    }

    console.log('WARNING: re-computing variant samples')
    for (const sampleId of sampleIDs) {
        if (feature.variant.SAMPLES[sampleId]) {
            const gt = feature.variant.SAMPLES[sampleId]["GT"][0]

            // If any sample in the whitelist is non-WT, show this site. Otherwise filter.
            if (!isVariant(gt)) {
                console.log(sampleId)
                return true
            }
        }
    }

    return false
}

RenderedFeatureGlyph.propTypes = {
    layout: ReactPropTypes.shape({
        addRect: ReactPropTypes.func.isRequired,
        getTotalHeight: ReactPropTypes.func.isRequired,
    }).isRequired,

    displayMode: ReactPropTypes.string.isRequired,
    region: CommonPropTypes.Region.isRequired,
    bpPerPx: ReactPropTypes.number.isRequired,
    feature: ReactPropTypes.shape({
        id: ReactPropTypes.func.isRequired,
        get: ReactPropTypes.func.isRequired,
    }).isRequired,
    config: CommonPropTypes.ConfigSchema.isRequired,
}

const RenderedFeatures = observer(props => {
    const { features, rendererConfig } = props
    const { activeSamples, infoFilters } = rendererConfig
    const featuresRendered = []

    let expandedFilters = []
    if (infoFilters.toJSON()) {
        expandedFilters = expandFilters(infoFilters.toJSON())
        console.log(expandedFilters)
    }

    //TODO: restore this
    expandedFilters = []

    //TODO: restore this
    const sampleFilters = null //activeSamples.value ? activeSamples.value.split(',') : null
    features.forEach(function(feature) {
        if (isDisplayed(feature, expandedFilters)){
            if (containsSampleIDs(feature, sampleFilters)){
                featuresRendered.push(
                        <RenderedFeatureGlyph key={feature.id()} feature={feature} {...props} />,
                )
            }
        }
    })

    return <>{featuresRendered}</>
})
RenderedFeatures.propTypes = {
    features: ReactPropTypes.oneOfType([
        ReactPropTypes.instanceOf(Map),
        ReactPropTypes.arrayOf(ReactPropTypes.shape()),
    ]),
    layout: ReactPropTypes.shape({
        addRect: ReactPropTypes.func.isRequired,
        getTotalHeight: ReactPropTypes.func.isRequired,
    }).isRequired,
}

RenderedFeatures.defaultProps = {
    features: [],
}

function SvgFeatureRendering(props) {
    const {
        layout,
        blockKey,
        regions,
        bpPerPx,
        features,
        config,
        displayModel,
        exportSVG,
    } = props
    const [region] = regions || []
    const width = (region.end - region.start) / bpPerPx
    const displayMode = readConfObject(config, 'displayMode')

    const ref = useRef()
    const [mouseIsDown, setMouseIsDown] = useState(false)
    const [movedDuringLastMouseDown, setMovedDuringLastMouseDown] = useState(
            false,
    )
    const [height, setHeight] = useState(0)
    const {
        onMouseOut,
        onMouseDown,
        onMouseLeave,
        onMouseEnter,
        onMouseOver,
        onMouseMove,
        onMouseUp,
        onClick,
    } = props

    const mouseDown = useCallback(
            event => {
                setMouseIsDown(true)
                setMovedDuringLastMouseDown(false)
                const handler = onMouseDown
                if (!handler) {
                    return undefined
                }
                return handler(event)
            },
            [onMouseDown],
    )

    const mouseUp = useCallback(
            event => {
                setMouseIsDown(false)
                const handler = onMouseUp
                if (!handler) {
                    return undefined
                }
                return handler(event)
            },
            [onMouseUp],
    )

    const mouseEnter = useCallback(
            event => {
                const handler = onMouseEnter
                if (!handler) {
                    return undefined
                }
                return handler(event)
            },
            [onMouseEnter],
    )

    const mouseLeave = useCallback(
            event => {
                const handler = onMouseLeave
                if (!handler) {
                    return undefined
                }
                return handler(event)
            },
            [onMouseLeave],
    )

    const mouseOver = useCallback(
            event => {
                const handler = onMouseOver
                if (!handler) {
                    return undefined
                }
                return handler(event)
            },
            [onMouseOver],
    )

    const mouseOut = useCallback(
            event => {
                const handler = onMouseOut
                if (!handler) {
                    return undefined
                }
                return handler(event)
            },
            [onMouseOut],
    )

    const mouseMove = useCallback(
            event => {
                if (mouseIsDown) {
                    setMovedDuringLastMouseDown(true)
                }
                let offsetX = 0
                let offsetY = 0
                if (ref.current) {
                    offsetX = ref.current.getBoundingClientRect().left
                    offsetY = ref.current.getBoundingClientRect().top
                }
                offsetX = event.clientX - offsetX
                offsetY = event.clientY - offsetY
                const px = region.reversed ? width - offsetX : offsetX
                const clientBp = region.start + bpPerPx * px

                const featureIdCurrentlyUnderMouse = displayModel.getFeatureOverlapping(
                        blockKey,
                        clientBp,
                        offsetY,
                )

                if (onMouseMove) {
                    onMouseMove(event, featureIdCurrentlyUnderMouse)
                }
            },
            [
                blockKey,
                bpPerPx,
                mouseIsDown,
                onMouseMove,
                region.reversed,
                region.start,
                displayModel,
                width,
            ],
    )

    const click = useCallback(
            event => {
                // don't select a feature if we are clicking and dragging
                if (movedDuringLastMouseDown) {
                    return
                }
                if (onClick) {
                    onClick(event)
                }
            },
            [movedDuringLastMouseDown, onClick],
    )

    useEffect(() => {
        setHeight(layout.getTotalHeight())
    }, [layout])
    if (exportSVG) {
        return (
                <RenderedFeatures
                        features={features}
                        displayMode={displayMode}
                        {...props}
                        region={region}
                />
        )
    }
    return (
            <div style={renderingStyle}>
                <svg
                        ref={ref}
                        className="SvgFeatureRendering"
                        width={width}
                        height={height + svgHeightPadding}
                        onMouseDown={mouseDown}
                        onMouseUp={mouseUp}
                        onMouseEnter={mouseEnter}
                        onMouseLeave={mouseLeave}
                        onMouseOver={mouseOver}
                        onMouseOut={mouseOut}
                        onMouseMove={mouseMove}
                        onFocus={mouseEnter}
                        onBlur={mouseLeave}
                        onClick={click}
                        style={{ display: 'block' }}
                >
                    <RenderedFeatures
                            features={features}
                            displayMode={displayMode}
                            {...props}
                            region={region}
                            movedDuringLastMouseDown={movedDuringLastMouseDown}
                    />
                    <SvgOverlay {...props} region={region} />
                </svg>
            </div>
    )
}

SvgFeatureRendering.propTypes = {
    layout: ReactPropTypes.shape({
        addRect: ReactPropTypes.func.isRequired,
        getTotalHeight: ReactPropTypes.func.isRequired,
    }).isRequired,

    regions: ReactPropTypes.arrayOf(CommonPropTypes.Region).isRequired,
    bpPerPx: ReactPropTypes.number.isRequired,
    features: ReactPropTypes.oneOfType([
        ReactPropTypes.instanceOf(Map),
        ReactPropTypes.arrayOf(ReactPropTypes.shape()),
    ]),
    config: CommonPropTypes.ConfigSchema.isRequired,
    displayModel: ReactPropTypes.shape({
        configuration: ReactPropTypes.shape({}),
        getFeatureOverlapping: ReactPropTypes.func,
        selectedFeatureId: ReactPropTypes.string,
        featureIdUnderMouse: ReactPropTypes.string,
    }),

    onMouseDown: ReactPropTypes.func,
    onMouseUp: ReactPropTypes.func,
    onMouseEnter: ReactPropTypes.func,
    onMouseLeave: ReactPropTypes.func,
    onMouseOver: ReactPropTypes.func,
    onMouseOut: ReactPropTypes.func,
    onMouseMove: ReactPropTypes.func,
    onClick: ReactPropTypes.func,
    onContextMenu: ReactPropTypes.func,
    onFeatureClick: ReactPropTypes.func,
    onFeatureContextMenu: ReactPropTypes.func,
    blockKey: ReactPropTypes.string,
    exportSVG: ReactPropTypes.shape({}),
}

SvgFeatureRendering.defaultProps = {
    displayModel: {},
    exportSVG: undefined,

    features: new Map(),
    blockKey: undefined,

    onMouseDown: undefined,
    onMouseUp: undefined,
    onMouseEnter: undefined,
    onMouseLeave: undefined,
    onMouseOver: undefined,
    onMouseOut: undefined,
    onMouseMove: undefined,
    onClick: undefined,
    onContextMenu: undefined,
    onFeatureClick: undefined,
    onFeatureContextMenu: undefined,
}

export default observer(SvgFeatureRendering)
