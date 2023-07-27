import { FIELD_NAME_MAP } from './fields';
import { Chart } from 'react-google-charts';
import { FieldModel, getGenotypeURL } from '../../../../utils';
import { ActionURL, Ajax } from '@labkey/api';
import React, { useEffect, useState } from 'react';
import { BaseCard, FeatureDetails } from '@jbrowse/core/BaseFeatureWidget/BaseFeatureDetail';
import { Paper, Table, TableBody, TableCell, TableHead, TableRow, Tooltip } from '@mui/material';
import { observer, PropTypes as MobxPropTypes } from 'mobx-react';
import { styled } from '@mui/material/styles';

export default jbrowse => {
    const TableNoPaddingBlock = styled(Table)(({ theme }) => ({
        padding: 0,
        display: 'block'
    }))

    const TableRowBgGrey = styled(TableRow)(({ theme }) => ({
        background: theme.palette.grey[100]
    }))

    const Link = styled('div')(({ theme }) => ({
        padding: theme.spacing(5)
    }))

    const TableRowFlexWrap = styled(TableRow)(({ theme }) => ({
        display: 'flex',
        flexWrap: 'wrap'
    }))

    const TableCellFieldName = styled(TableCell)(({ theme }) => ({
        wordBreak: 'break-all',
        minWidth: 120,
        borderBottom: '1px solid #0003',
        background: theme.palette.grey[200],
        marginRight: theme.spacing(1),
        padding: theme.spacing(0.5)
    }))

    const TableCellFieldValue = styled(TableCell)(({ theme }) => ({
        wordBreak: 'break-word',
        maxWidth: 500,
        padding: theme.spacing(0.5),
        overflow: 'auto'
    }))

    const Message = styled('div')(({ theme }) => ({
        paddingTop: theme.spacing(5),
        paddingLeft: theme.spacing(5),
        paddingRight: theme.spacing(5),
        maxWidth: 500
    }))

    function round(value, decimals) {
        return Number(Math.round(Number(value+'e'+decimals)) + 'e-'+decimals);
    }

    function makeAnnTable(data){
        const geneNames = []
        const tableBodyRows = []
        for (let lineStr of data){
            let line = lineStr.split('|')
            if (line[10]){
                line[10] = <div>{line[10]}</div>
            }

            const geneName = line[3] + (line[4] ? " (" + line[4] + ")" : "");
            if (!geneNames.includes(geneName)){
                tableBodyRows.push(
                        <TableRow key={geneName}>
                            <TableCell>{line[1]}</TableCell>
                            <TableCell>{line[2]}</TableCell>
                            <TableCell>{geneName}</TableCell>
                            <TableCell>{line[9]} {line[10]}</TableCell>
                        </TableRow>
                )
                geneNames.push(geneName)
            }
        }

        return(
            <BaseCard title="Predicted Function">
                <TableNoPaddingBlock>
                    <TableHead>
                        <TableRowBgGrey>
                            <TableCell>Effect</TableCell>
                            <TableCell>Impact</TableCell>
                            <TableCell>Gene Name</TableCell>
                            <TableCell>Position/Consequence</TableCell>
                        </TableRowBgGrey>
                    </TableHead>
                    <TableBody>
                        {tableBodyRows}
                    </TableBody>
                </TableNoPaddingBlock>
            </BaseCard>
        )
    }

    function makeDisplays(feat, displays, featureInfoFields, infoFields){
        if (!infoFields) {
            return null
        }

        const propertyJSX = []
        for (let display of displays){
            const tempProp = []
            for (let propertyName of display.properties){
                // This value is not in the header, and is probably injected programmatically, so skip:
                if (!featureInfoFields[propertyName]) {
                    continue
                }

                const value = feat["INFO"][propertyName]
                const fieldTitle = infoFields[propertyName]?.label || FIELD_NAME_MAP[propertyName]?.title || propertyName
                const tooltip = infoFields[propertyName]?.description || featureInfoFields[propertyName]?.Description
                if (value){
                    tempProp.push(
                            <TableRowFlexWrap key={propertyName + "-field"}>
                                <Tooltip title={tooltip}>
                                    <TableCellFieldName>
                                        {fieldTitle}
                                    </TableCellFieldName>
                                </Tooltip>
                                <TableCellFieldValue key={propertyName + "-val"}>
                                    {/* TODO: use JEXL and formatString */}
                                    {Array.isArray(value) ? value.join(', ') :  value}
                                </TableCellFieldValue>
                            </TableRowFlexWrap>
                    )
                }
            }

            if (tempProp.length !== 0){
                propertyJSX.push(tempProp)
            }
        }

        const displayJSX = []
        for (let i = 0; i < propertyJSX.length; i++){
            displayJSX.push(
                    <BaseCard key={displays[i].title} title={displays[i].title}>
                        <TableNoPaddingBlock>
                            <TableBody>
                                {propertyJSX[i]}
                            </TableBody>
                        </TableNoPaddingBlock>
                    </BaseCard>
            )
        }
        return displayJSX
    }

    function inferSections(feat, infoFields: Map<string, FieldModel>) {
        if (!infoFields) {
            return []
        }

        const sectionMap = {}
        for (const [key, fieldDescriptor] of Object.entries(infoFields)) {
            if (!fieldDescriptor.category) {
                continue
            }

            if (!sectionMap[fieldDescriptor.category]) {
                sectionMap[fieldDescriptor.category] = {
                    title: fieldDescriptor.category,
                    properties: []
                }
            }

            sectionMap[fieldDescriptor.category].properties.push(fieldDescriptor.name)
        }

        const keys = Object.keys(sectionMap).sort()
        return keys.map((x) => sectionMap[x])
    }

    function makeChart(samples, feat, trackId){
        // Abort if there are no samples
        if (!samples || Object.keys(samples).length === 0) {
            return null;
        }

        const ref = feat["REF"];
        const alt = feat["ALT"]

        const [state, setState] = useState(null)
        useEffect(() => {
            setState(
                    <BaseCard title="Genotypes">
                        <div>
                            Loading genotypes...
                        </div>
                    </BaseCard>
            )
            const alleleCounts = {}
            let alleleTotal = 0
            alleleCounts[ref] = 0
            for (let allele of alt){
                alleleCounts[allele] = 0
            }
            const gtCounts = {}
            let gtTotal = 0

            // unphased gts split on /, phased on |
            const regex = /\/|\|/
            for (let sample in samples){
                const gt = samples[sample]["GT"]
                for (let genotype of gt){
                    const nc = "No Call"
                    if (genotype === "./." || genotype === ".|."){
                        gtCounts[nc] = gtCounts[nc] ? gtCounts[nc] + 1 : 1
                        gtTotal = gtTotal + 1
                    }
                    else {
                        const genotypes = genotype.split(regex)
                        const alleles = [ref].concat(alt)

                        // Calculate per-base values:
                        for (let gtIdx = 0; gtIdx < genotypes.length; gtIdx++){
                            if (!alleles[genotypes[gtIdx]]) {
                                console.error('Unable to parse genotype: ' + genotype)
                                continue
                            }

                            genotypes[gtIdx] = alleles[genotypes[gtIdx]]
                            alleleCounts[genotypes[gtIdx]] = alleleCounts[genotypes[gtIdx]] + 1 // tick up allele count
                            alleleTotal = alleleTotal + 1
                        }

                        // Then by genotype:
                        const genotypeString = genotypes.join("/")
                        gtCounts[genotypeString] = gtCounts[genotypeString] ? gtCounts[genotypeString] + 1 : 1
                        gtTotal = gtTotal + 1
                    }
                }
            }

            const gtBarData = [[
                'Genotype',
                'Total Count',
                { role: 'style' },
                {
                    sourceColumn: 0,
                    role: 'annotation',
                    type: 'string',
                    calc: 'stringify',
                },
            ]]

            let rounds = 0
            let decimal = 1
            while (decimal !== 2){
                for (let entry in gtCounts){
                    rounds = rounds + round(gtCounts[entry]/gtTotal*100, decimal)
                }
                if (rounds === 100){
                    break
                }
                rounds = 0
                decimal = decimal + 1
            }

            for (let entry in gtCounts){
                let rounded:any = round(gtCounts[entry]/gtTotal*100, decimal)
                if (rounds !== 100){
                    rounded = "~" + rounded
                }

                gtBarData.push([entry, gtCounts[entry], "#0088FF", rounded+"%"])
            }

            const alleleTableRows = []
            for (let allele in alleleCounts){
                alleleTableRows.push(
                        <TableRow key={allele}>
                            <TableCell>{allele}</TableCell>
                            <TableCell>{round(alleleCounts[allele]/alleleTotal, 4)}</TableCell>
                            <TableCell>{alleleCounts[allele]}</TableCell>
                        </TableRow>
                )
            }
            const gtTitle = "Genotype Frequency (" + gtTotal.toString() + ")"
            const contig = feat["CHROM"];
            const start = feat["POS"];
            const end = feat["end"];

            const href = <a href={getGenotypeURL(trackId, contig, start, end)} target="_blank">Click here to view sample-level genotypes</a>

            setState(
                    <div>
                        <BaseCard title="Allele Frequencies">
                            <TableNoPaddingBlock>
                                <TableHead>
                                    <TableRowBgGrey>
                                        <TableCell>Sequence</TableCell>
                                        <TableCell>Fraction</TableCell>
                                        <TableCell>Count</TableCell>
                                    </TableRowBgGrey>
                                </TableHead>
                                <TableBody>
                                    {alleleTableRows}
                                </TableBody>
                            </TableNoPaddingBlock>
                        </BaseCard>
                        <BaseCard title={gtTitle}>
                            <Chart
                                    width={'250px'}
                                    height={'200px'}
                                    chartType="BarChart"
                                    loader={<div>Loading Chart</div>}
                                    data={gtBarData}
                                    options={{
                                        title: "Genotypes",
                                        width: 400,
                                        height: 200,
                                        bar: { groupWidth: '60%' },
                                        legend: { position: 'none' },
                                        fontSize: 14
                                    }}
                                    // For tests
                                    rootProps={{ 'data-testid': '6' }}
                            />
                            <Link>
                                {href}
                            </Link>
                        </BaseCard>
                    </div>)
        }, []);
        return state
    }
    function CreatePanel(props) {
        const { model } = props
        const detailsConfig = JSON.parse(JSON.stringify(model.detailsConfig || {}))
        const feature = model.featureData
        const featureInfoFields = feature.parser.metadata.INFO
        const [infoFields, setInfoFields] = useState<Map<string, FieldModel>>(null)

        const feat = JSON.parse(JSON.stringify(feature))
        const { samples } = feat
        feat["samples"] = null

        const trackId = model.trackId
        if (!trackId) {
            console.error('Error! No trackId')
        }

        const infoKeys = Object.keys(feat.INFO)
        let isApiSubscribed = true
        useEffect(() => {
            Ajax.request({
                url: ActionURL.buildURL('jbrowse', 'resolveVcfFields.api'),
                method: 'POST',
                success: async function(res){
                    if (isApiSubscribed) {
                        const fields: Map<string, FieldModel> = JSON.parse(res.response);
                        setInfoFields(fields)
                    }
                },
                failure: function(res){
                    console.error("There was an error while fetching field types: " + res.status + "\n Status Body: " + res.statusText)
                },
                params: {infoKeys: infoKeys},
            });

            return () => {
                isApiSubscribed = false;
            };
        }, [infoKeys])

        let annTable;
        if (feat["INFO"]["ANN"]){
            annTable = makeAnnTable(feat["INFO"]["ANN"])
            delete feat["INFO"]["ANN"]
        }

        const sections = detailsConfig.sections || inferSections(feat, infoFields)
        const displays = makeDisplays(feat, sections, featureInfoFields, infoFields)

        // If a given INFO field is used in a specific section, dont include in the catch-all INFO section:
        for (let i in sections){
            for (let j in sections[i].properties){
                delete feat["INFO"][sections[i].properties[j]]
            }
        }

        if (infoFields) {
            const ignoredFields = Object.values(infoFields).filter((x: FieldModel) => !!x.isHidden).map((x: FieldModel) => x.name)
            for (const fieldName of ignoredFields) {
                if (feat["INFO"][fieldName]) {
                    delete feat["INFO"][fieldName]
                }
            }
        }

        const message = detailsConfig.message ? <Message >{detailsConfig.message}</Message> : null
        const infoConfig = [{
            title: "Other Fields",
            properties: []
        }]

        for (let infoEntry in feat["INFO"]){
            infoConfig[0].properties.push(infoEntry)
        }

        const infoDisplays = makeDisplays(feat, infoConfig, featureInfoFields, infoFields)
        feat["INFO"] = null

        return (
                <Paper data-testid="extended-variant-widget">
                    {message}
                    <FeatureDetails
                            feature={feat}
                            {...props}
                    />
                    {annTable}
                    {displays}
                    {infoDisplays}
                    {makeChart(samples, feat, trackId)}
                </Paper>
        )
    }

    CreatePanel.propTypes = {
        model: MobxPropTypes.observableObject.isRequired,
    }

    return observer(CreatePanel)
}