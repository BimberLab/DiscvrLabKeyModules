
export default jbrowse => {
  const {
    Divider,
    Paper,
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableRow,
    Chip,
    Tooltip,
    Link,
  } = jbrowse.jbrequire('@material-ui/core')
    var Chart = require("react-google-charts").Chart
    var Ajax = require('@labkey/api').Ajax
    var Utils = require('@labkey/api').Utils
    var ActionURL = require('@labkey/api').ActionURL
    const { makeStyles } = jbrowse.jbrequire('@material-ui/core/styles')
    var _interopRequireDefault = require("@babel/runtime/helpers/interopRequireDefault");
    var _core = require("@material-ui/core");
    var _styles = require("@material-ui/core/styles")
    var _SanitizedHTML = _interopRequireDefault(require("@jbrowse/core/ui/SanitizedHTML"));
    const queryParam = new URLSearchParams(window.location.search);
    const session = queryParam.get('session')


    const { observer, PropTypes: MobxPropTypes } = jbrowse.jbrequire('mobx-react')
    const PropTypes = jbrowse.jbrequire('prop-types')
    const React = jbrowse.jbrequire('react')
    const { useState, useEffect } = React

    const { FeatureDetails, BaseFeatureDetails, BaseCard } = jbrowse.jbrequire(
      '@jbrowse/core/BaseFeatureWidget/BaseFeatureDetail',
    )
    const fields = {
        ANN: {
            title: 'Predicted Impact (SnpEff)'
        },
        CADD_PH: {
            title: 'CADD Functional Prediction Score'
        },
        ENC: {
            title: 'ENCODE Class'
        },
        ENN: {
            title: 'ENCODE Feature Name'
        },
        ENCDNA_SC: {
            title: 'ENCODE DNase Sensitivity Score'
        },
        ENCTFBS_SC: {
            title: 'ENCODE Transcription Factor Binding Site Score'
        },
        ENCTFBS_TF: {
            title: 'ENCODE Transcription Factors'
        },
        ENCSEG_NM: {
            title: 'ENCODE Segmentation Status'
        },
        FS_EN: {
            title: 'Funseq Annotated?'
        },
        FS_NS: {
            title: 'Funseq Noncoding Score',
            range: [0,5.4]
        },
        FS_SN: {
            title: 'Funseq Sensitive Region?'
        },
        FS_TG: {
            title: 'Funseq Target Gene?'
        },
        FS_US: {
            title: 'Funseq Ultra-Sensitive Region?'
        },
        FS_WS: {
            title: 'Funseq Score'
        },
        FS_SC: {
            title: 'Funseq Non-coding Score',
            range: [0,6]
        },
        SF: {
            title: 'Swiss Prot Protein Function'
        },
        SX: {
            title: 'Swiss Prot Expression'
        },
        SD: {
            title: 'Swiss Prot Disease Annotations'
        },
        SM: {
            title: 'Swiss Prot Post-translational Modifications'
        },
        RFG: {
            title: 'Mutation Type (RefSeq)'
        },
        PC_PL: {
            title: 'Conservation Score Among 46 Placental Mammals (PhastCons)'
        },
        PC_PR: {
            title: 'Conservation Score Among 46 Primates (PhastCons)'
        },
        PC_VB: {
            title: 'Conservation Score Among 100 Vertebrate Species (PhastCons)'
        },
        PP_PL: {
            title: 'Conservation Score Among 46 Placental Mammals (Phylop)'
        },
        PP_PR: {
            title: 'Conservation Score Among 46 Primates (Phylop)'
        },
        PP_VB: {
            title: 'Conservation Score Among 100 Vertebrate Species (Phylop)'
        },
        RDB_WS: {
            title: 'RegulomeDB Score',
            range: [1,6]
        },
        RDB_MF: {
            title: 'RegulomeDB Motifs'
        },
        MAF: {
            title: 'Minor Allele Frequency'
        },
        OMIMN: {
            title: 'OMIM Number'
        },
        OMIMM: {
            title: 'OMIM Method'
        },
        OMIMC: {
            title: 'OMIM Comments'
        },
        OMIMMUS: {
            title: 'OMIM Mouse Correlate'
        },
        OMIMD: {
            title: 'OMIM Disorders'
        },
        OMIMS: {
            title: 'OMIM Status'
        },
        OMIMT: {
            title: 'OMIM Title'
        },
        GRASP_PH: {
            title: 'GRASP Phenotype'
        },
        GRASP_AN: {
            title: 'GRASP Ancestry'
        },
        GRASP_P: {
            title: 'GRASP p-value'
        },
        GRASP_PL: {
            title: 'GRASP Platform'
        },
        GRASP_PMID: {
            title: 'GRASP PMID'
        },
        GRASP_RS: {
            title: 'GRASP SNP RS Number'
        },
        ERBCTA_NM: {
            title: 'Ensembl Regulatory Build Predicted State'
        },
        LF: {
            title: 'Unable to Lift to Human'
        },
        NE: {
            title: 'PolyPhen2 Score'
        },
        NF: {
            title: 'PolyPhen2 Prediction'
        },
        NG: {
            title: 'nsdb LRT Score'
        },
        NH: {
            title: 'nsdb LRT Prediction'
        },
        NK: {
            title: 'MutationAssessor Score'
        },
        NL: {
            title: 'MutationAssessor Prediction'
        },
        NC: {
            title: 'nsdb SIFT Score'
        },
        NJ: {
            title: 'MutationTaster Prediction'
        },
        LOF: {
            title: 'Predicted Loss-of-function'
        },
        FC: {
            title: 'Probable Promoter (FAMTOM5)'
        },
        FE: {
            title: 'Probable Enhancer (FANTOM5)'
        },
        TMAF: {
            title: '1000Genomes Allele Frequency'
        },
        CLN_SIG: {
            title: 'ClinVar Significance'
        },
        CLN_DN: {
            title: 'ClinVar Disease/Concept'
        },
        CLN_DNINCL: {
            title: 'ClinVar Disease/Concept'
        },
        CLN_ALLELE: {
            title: 'ClinVar Allele'
        },
        CLN_ALLELEID: {
            title: 'ClinVar Allele ID'
        },
        CLN_VI: {
            title: 'ClinVar Sources'
        },
        CLN_DBVARID: {
            title: 'ClinVar dbVAR Accessions'
        },
        CLN_GENEINFO: {
            title: 'ClinVar Gene(s)'
        },
        CLN_RS: {
            title: 'ClinVar RS Numbers'
        },
        LiftedContig: {
            title: 'Contig (Lifted to Human)'
        },
        LiftedStart: {
            title: 'Start (Lifted to Human)'
        }
    }
   var useStyles = (0, _styles.makeStyles)(function (theme) {
      return {
          table: {
              padding: 0,
              display: 'block'
          },
          link: {
              color: 'rgb(0, 0, 238)',
          },
          message: {
              paddingTop: theme.spacing(5),
              paddingLeft: theme.spacing(5),
              paddingRight: theme.spacing(5),
              maxWidth: 500
          },
        expansionPanelDetails: {
          display: 'block',
          padding: theme.spacing(1)
        },
        expandIcon: {
          color: '#FFFFFF'
        },
        paperRoot: {
          background: theme.palette.grey[100]
        },
        field: {
          display: 'flex',
          flexWrap: 'wrap'
        },
        fieldDescription: {
          '&:hover': {
            background: 'yellow'
          }
        },
        fieldName: {
          wordBreak: 'break-all',
          minWidth: '90px',
          borderBottom: '1px solid #0003',
          background: theme.palette.grey[200],
          marginRight: theme.spacing(1),
          padding: theme.spacing(0.5)
        },
        fieldValue: {
          wordBreak: 'break-word',
          maxHeight: 300,
          padding: theme.spacing(0.5),
          overflow: 'auto'
        },
        fieldSubValue: {
          wordBreak: 'break-word',
          maxHeight: 300,
          padding: theme.spacing(0.5),
          background: theme.palette.grey[100],
          border: "1px solid ".concat(theme.palette.grey[300]),
          boxSizing: 'border-box',
          overflow: 'auto'
        },
        accordionBorder: {
          marginTop: '4px',
          border: '1px solid #444'
        }
      };
    });

    function round(value, decimals) {
        return Number(Math.round(value+'e'+decimals)+'e-'+decimals);
    }

    function makeTable(data, classes){
        var geneNames = []
        var tableBodyRows = []
        for(var i in data){
            var line = data[i].split('|')
            if(line[10]){
                line[10] = <div>{line[10]}</div>
            }
            var geneName = line[3]+" "+line[4]
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

// A|intron_variant|MODIFIER|NTNG1|ENSMMUG00000008197|transcript|ENSMMUT00000072133.2|protein_coding|2/9|c.247-3863G>T||||||
// 0|1             |2       |3    |4                 |5         |6                   |7             |8  |9            ||||||
// Effect | Impact | Gene Name | Position / Consequence
// downstream_gene_variant | MODIFIER | LTB-TNF(ENSMMUG...-ENSMMUT...) | c.247-3863G
// 1 | 2 | 3+4 ask | 9+10

    }

    function makeDisplays(feat, displays, classes){
        var propertyJSX = []

        for(var display in displays){
            var tempProp = []
            for(var property in displays[display].properties){
                if(feat["INFO"][displays[display].properties[property]]){
                    if(feat["INFO"][displays[display].properties[property]].length == 1){
                        var tempName
                        if(fields[displays[display].properties[property]]){
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
                    }else if(feat["INFO"][displays[display].properties[property]].length > 1){
                        var children = []
                        for(var val in feat["INFO"][displays[display].properties[property]]){
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
            if(tempProp.length != 0){
                propertyJSX.push(tempProp)
            }
        }
                 /* <div style={{ padding: '7px', width: '100%', maxHeight: 600, overflow: 'auto' }}>
                        <Table className={displays[i].name}>
                            <TableHead>
                                {propertyJSX[i]}
                            </TableHead>
                        </Table>
                   </div>*/
        var displayJSX = []
        for(var i = 0; i < propertyJSX.length; i++){
            displayJSX.push(
                <BaseCard title={displays[i].name}>
                    {propertyJSX[i]}
                </BaseCard>
            )
        }
        return displayJSX
    }

    function makeChart(samples, ref, alt, classes){
        const [state, setState] = useState(null)
        useEffect(() => {
            setState(
                <BaseCard title="Genotypes">
                    <div>
                    Loading genotypes...
                    </div>
                </BaseCard>
            )
            var alleleCounts = {}
            var alleleTotal = 0
            alleleCounts[ref] = 0
            for(var i in alt){
                alleleCounts[alt[i]] = 0
            }
            var gtCounts = {}
            var gtTotal = 0
            for(var sample in samples){
                var gt = samples[sample]["GT"]
                for(var entry in gt){
                    // CASES
                    // ./. -- no call
                    if(gt[entry] == "./."){
                        if(gtCounts["No Call"]){                          // if gtCounts entry is not null, or we have a preexisting entry for it
                            gtCounts["No Call"] = gtCounts["No Call"] + 1 // increment count for that gt
                            gtTotal = gtTotal + 1                         // increment our total count
                        }
                        else {                                   // else if gtCounts entry is null, or we don't have an entry, set to 1
                            gtCounts["No Call"] = 1
                            gtTotal = gtTotal + 1                // increment our total count
                        }
                    }
                    // int / int
                    else if(gt[entry]){
                        var gtKey;                               // should be an array of len 2 after split
                        if (gt[entry].includes("/")){            // unphased gts split on /
                            gtKey = gt[entry].split("/")
                        }
                        else if(gt[entry].includes("|")){        // phased gts split on |
                            gtKey = gt[entry].split("|")
                        }
                        for(var gtVal in gtKey){
                            if(gtKey[gtVal] == 0){
                                gtKey[gtVal] = ref
                                alleleCounts[ref] = alleleCounts[ref] + 1 // tick up allele count
                                alleleTotal = alleleTotal + 1
                            }
                            else{
                                gtKey[gtVal] = alt[gtKey[gtVal]-1]
                                alleleCounts[gtKey[gtVal]] = alleleCounts[gtKey[gtVal]] + 1 // tick up allele count
                                alleleTotal = alleleTotal + 1
                            }
                        }
                        gtKey = gtKey[0] + "/" + gtKey[1]         // for the purposes of the chart, phased/unphased can be counted as the same
                       if(gtCounts[gtKey]){                       // if gtCounts entry is not null, or we have a preexisting entry for it
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
            var gtBarData = [
            [
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
            for(var entry in gtCounts){
                var rounded = round(gtCounts[entry]/gtTotal*100, 1)
                gtBarData.push(
                    [entry, gtCounts[entry], "#0088FF", rounded+"%"]
                )
            }

            var alleleTableRows = []
            for(var allele in alleleCounts){
                alleleTableRows.push(
                    <TableRow>
                            <TableCell>{allele}</TableCell>
                            <TableCell>{round(alleleCounts[allele]/alleleTotal, 4)}</TableCell>
                    </TableRow>
                )
            }
            setState(
            <div>
                <BaseCard title="Allele Frequencies">
                    <Table className={classes.table}>
                        <TableHead>
                            <TableRow className={classes.paperRoot}>
                                    <TableCell>Sequence</TableCell>
                                    <TableCell>Fraction</TableCell>
                            </TableRow>
                        </TableHead>
                        <TableBody>
                            {alleleTableRows}
                        </TableBody>
                    </Table>
                </BaseCard>
                <BaseCard title="Genotypes">
                     <Chart
                       width={'250px'}
                       height={'200px'}
                       chartType="BarChart"
                       loader={<div>Loading Chart</div>}
                       data={gtBarData}
                       options={{
                         title: 'Genotype Frequency',
                         width: 300,
                         height: 200,
                         bar: { groupWidth: '95%' },
                         legend: { position: 'none' },
                       }}
                       // For tests
                       rootProps={{ 'data-testid': '6' }}
                     />
                </BaseCard>
            </div>)
        }, []);
        return state
    }
    function NewTable(props) {
        const classes = useStyles()
        const { feature } = props
        const { model } = props
        const feat = JSON.parse(JSON.stringify(model.featureData))
        const samples = model.featureData.samples
        console.log(model.featureData)
        feat["samples"] = null

        var displays;
        var configDisplays = model.extendedVariantDisplayConfig
        displays = makeDisplays(feat, configDisplays, classes)
        for(var i in configDisplays){
            for(var j in configDisplays[i].properties){
                feat["INFO"][configDisplays[i].properties[j]] = null
            }
        }

        var annTable;
        if (feat["INFO"]["ANN"]){
            annTable = makeTable(feat["INFO"]["ANN"], classes)
            feat["INFO"]["ANN"] = null
        }

        var message;
        if (model.message){
            message = <div className={classes.message} >{model.message}</div>
        }

        var infoConfig = [{
            name: "Info",
            properties: []
        }]
        for(var infoEntry in feat["INFO"]){
            infoConfig[0].properties.push(infoEntry)
        }
        var infoDisplays = makeDisplays(feat, infoConfig, classes)
        feat["INFO"] = null
        /*for(var infoEntry in feat["INFO"]){
            if(fields[infoEntry]){
                feat[fields[infoEntry].title] = feat["INFO"][infoEntry]
                feat["INFO"][infoEntry] = null
            }
        }*/

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
                 {makeChart(samples, feat["REF"], feat["ALT"], classes)}
            </Paper>
        )
    }

  NewTable.propTypes = {
    model: MobxPropTypes.observableObject.isRequired,
  }

  return observer(NewTable)
}