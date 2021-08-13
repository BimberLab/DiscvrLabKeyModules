import {fields} from "./fields";
import {ActionURL} from "@labkey/api";
import {Chart} from "react-google-charts";
import {style as styles} from "./style";

export default jbrowse => {
    const {
        Paper,
        Table,
        TableBody,
        TableCell,
        TableHead,
        TableRow
    } = jbrowse.jbrequire('@material-ui/core')
    const { observer, PropTypes: MobxPropTypes } = jbrowse.jbrequire('mobx-react')
    const React = jbrowse.jbrequire('react')
    const { useState, useEffect } = React
    const { FeatureDetails, BaseCard } = jbrowse.jbrequire(
            '@jbrowse/core/BaseFeatureWidget/BaseFeatureDetail',
    )

    function round(value, decimals) {
        return Number(Math.round(value+'e'+decimals)+'e-'+decimals);
    }

    function makeTable(data, classes){
        const geneNames = []
        const tableBodyRows = []
        for (let i in data){
            let line = data[i].split('|')
            if (line[10]){
                line[10] = <div>{line[10]}</div>
            }

            const geneName = line[3] + (line[4] ? " (" + line[4] + ")" : "");
            if (!geneNames.includes(geneName)){
                tableBodyRows.push(
                        <TableRow>
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
                    <Table className={classes.table}>
                        <TableHead>
                            <TableRow className={classes.paperRoot}>
                                <TableCell>Effect</TableCell>
                                <TableCell>Impact</TableCell>
                                <TableCell>Gene Name</TableCell>
                                <TableCell>Position/Consequence</TableCell>
                            </TableRow>
                        </TableHead>
                        <TableBody>
                            {tableBodyRows}
                        </TableBody>
                    </Table>
                </BaseCard>
        )
    }

    function makeDisplays(feat, displays, classes){
        const propertyJSX = []
        for (let display in displays){
            const tempProp = []
            for (let property in displays[display].properties){
                if (feat["INFO"][displays[display].properties[property]]){
                    if (feat["INFO"][displays[display].properties[property]].length === 1){
                        let tempName
                        if (fields[displays[display].properties[property]]){
                            tempName = fields[displays[display].properties[property]].title
                        }
                        else {
                            tempName = displays[display].properties[property]
                        }
                        tempProp.push(
                                <div className={classes.field}>
                                    <div className={classes.fieldName}>
                                        {tempName}
                                    </div>
                                    <div className={classes.fieldValue}>
                                        {feat["INFO"][displays[display].properties[property]]}
                                    </div>
                                </div>
                        )
                    }
                    else if (feat["INFO"][displays[display].properties[property]].length > 1){
                        const children = []
                        for (let val in feat["INFO"][displays[display].properties[property]]){
                            children.push(
                                    <div className={classes.fieldSubValue}>
                                        {feat["INFO"][displays[display].properties[property]][val]}
                                    </div>
                            )
                        }
                        tempProp.push(
                                <div className={classes.field}>
                                    <div className={classes.fieldName}>
                                        {displays[display].properties[property]}
                                    </div>
                                    {children}
                                </div>
                        )
                    }
                }
            }

            if (tempProp.length !== 0){
                propertyJSX.push(tempProp)
            }
        }
        const displayJSX = []
        for (let i = 0; i < propertyJSX.length; i++){
            displayJSX.push(
                    <BaseCard title={displays[i].name}>
                        {propertyJSX[i]}
                    </BaseCard>
            )
        }
        return displayJSX
    }

    function makeChart(samples, feat, classes){
        // Abort if there are no samples
        if (Object.keys(samples).length === 0) {
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
            for (let i in alt){
                alleleCounts[alt[i]] = 0
            }
            const gtCounts = {}
            let gtTotal = 0
            for (let sample in samples){
                const gt = samples[sample]["GT"]
                for (let entry in gt){
                    // CASES
                    // ./. -- no call
                    const nc = "No Call"
                    if (gt[entry] === "./."){
                        if (gtCounts[nc]){                          // if gtCounts entry is not null, or we have a preexisting entry for it
                            gtCounts[nc] = gtCounts[nc] + 1 // increment count for that gt
                            gtTotal = gtTotal + 1                         // increment our total count
                        }
                        else {                                   // else if gtCounts entry is null, or we don't have an entry, set to 1
                            gtCounts[nc] = 1
                            gtTotal = gtTotal + 1                // increment our total count
                        }
                    }
                    // int / int
                    else if (gt[entry]){
                        let gtKey
                        let regex = /\/|\|/                     // unphased gts split on /, phased on |
                        if (regex.exec(gt[entry])){
                            gtKey = gt[entry].split(regex)
                        }
                        let alleles = [ref].concat(alt)
                        for (let gtVal in gtKey){
                            gtKey[gtVal] = alleles[gtKey[gtVal]]
                            alleleCounts[gtKey[gtVal]] = alleleCounts[gtKey[gtVal]] + 1 // tick up allele count
                            alleleTotal = alleleTotal + 1
                        }
                        gtKey = gtKey[0] + "/" + gtKey[1]         // for the purposes of the chart, phased/unphased can be counted as the same
                        if (gtCounts[gtKey]){                       // if gtCounts entry is not null, or we have a preexisting entry for it
                            gtCounts[gtKey] = gtCounts[gtKey] + 1 // increment count for that gt
                            gtTotal = gtTotal + 1                 // increment our total count
                        }
                        else {                                    // else if gtCounts entry is null, or we don't have an entry, set to 1
                            gtCounts[gtKey] = 1
                            gtTotal = gtTotal + 1                 // increment our total count
                        }
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
                let rounded = round(gtCounts[entry]/gtTotal*100, decimal)
                if (rounds !== 100){
                    rounded = "~" + rounded
                }
                gtBarData.push(
                        [entry, gtCounts[entry], "#0088FF", rounded+"%"]
                )
            }

            let alleleTableRows = []
            for (let allele in alleleCounts){
                alleleTableRows.push(
                        <TableRow>
                            <TableCell>{allele}</TableCell>
                            <TableCell>{round(alleleCounts[allele]/alleleTotal, 4)}</TableCell>
                            <TableCell>{alleleCounts[allele]}</TableCell>
                        </TableRow>
                )
            }
            const gtTitle = "Genotype Frequency (" + gtTotal.toString() + ")"

            // TODO - get variables, prepare genotypeTable link
            const trackId = 0
            const contig = feat["CHROM"];
            const start = feat["POS"];
            const end = feat["end"];

            const link = ActionURL.buildURL("jbrowse", "genotypeTable.view", null, {trackId: trackId, chr: contig, start: start, stop: end})
            const href = <a href={link} target="_blank">Click here to view sample-level genotypes</a>

            setState(
                    <div>
                        <BaseCard title="Allele Frequencies">
                            <Table className={classes.table}>
                                <TableHead>
                                    <TableRow className={classes.paperRoot}>
                                        <TableCell>Sequence</TableCell>
                                        <TableCell>Fraction</TableCell>
                                        <TableCell>Count</TableCell>
                                    </TableRow>
                                </TableHead>
                                <TableBody>
                                    {alleleTableRows}
                                </TableBody>
                            </Table>
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
                                        width: 300,
                                        height: 200,
                                        bar: { groupWidth: '95%' },
                                        legend: { position: 'none' },
                                    }}
                                    // For tests
                                    rootProps={{ 'data-testid': '6' }}
                            />
                            <div className={classes.link}>
                                {href}
                            </div>
                        </BaseCard>
                    </div>)
        }, []);
        return state
    }
    function NewTable(props) {
        const classes = styles()
        const { model } = props
        const feat = JSON.parse(JSON.stringify(model.featureData))
        const samples = model.featureData.samples
        feat["samples"] = null

        const configDisplays = model.extendedVariantDisplayConfig
        const displays = makeDisplays(feat, configDisplays, classes)
        for (let i in configDisplays){
            for (let j in configDisplays[i].properties){
                feat["INFO"][configDisplays[i].properties[j]] = null;
            }
        }

        let annTable;
        if (feat["INFO"]["ANN"]){
            annTable = makeTable(feat["INFO"]["ANN"], classes);
            feat["INFO"]["ANN"] = null;
        }

        let message;
        if (model.message){
            message = <div className={classes.message} >{model.message}</div>
        }

        const infoConfig = [{
            name: "Info",
            properties: []
        }]
        for (let infoEntry in feat["INFO"]){
            infoConfig[0].properties.push(infoEntry)
        }

        const infoDisplays = makeDisplays(feat, infoConfig, classes)
        feat["INFO"] = null

        return (
                <Paper className={classes.root} data-testid="extended-variant-widget">
                    {message}
                    <FeatureDetails
                            feature={feat}
                            {...props}
                    />
                    {infoDisplays}
                    {displays}
                    {annTable}
                    {makeChart(samples, feat, classes)}
                </Paper>
        )
    }

    NewTable.propTypes = {
        model: MobxPropTypes.observableObject.isRequired,
    }

    return observer(NewTable)
}