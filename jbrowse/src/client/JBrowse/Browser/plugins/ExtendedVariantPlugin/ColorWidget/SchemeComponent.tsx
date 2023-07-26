import { observer } from 'mobx-react';
import React from 'react';
import { generateGradient } from './colorUtil';

import { Box, Table, TableBody, TableCell, TableHead, TableRow } from '@mui/material';
import { styled } from '@mui/material/styles';

function makeTitle(key){
    if (key === "minVal"){
        return "Lower"
    }
    if (key === "maxVal"){
        return "Upper"
    }
    return key
}

const SchemeComponent = observer(props => {
    let scheme = props.scheme as any
    let tableHeader = <></>
    let table = <></>

    const TableS = styled(Table)(({ theme }) => ({
        padding: 0,
        display: 'block'
    }))

    const TableCellS = styled(TableCell)(({ theme }) => ({
        textAlign: 'center',
        padding: theme.spacing(0.75, 0, 0.75, 1)
    }))
    
    const lastRow =
        <TableRow>
            <TableCellS>
                Other
            </TableCellS>
            <TableCellS>
                <Box
                    sx={{
                        width: 10,
                        height: 10,
                        position: 'relative',
                        left: '50%',
                        right: '50%'
                    }}
                    bgcolor='gray'
                />
            </TableCellS>
        </TableRow>

    if (scheme.dataType === "number"){
        let gradient = generateGradient(scheme.options.minVal, scheme.options.maxVal, scheme.gradientSteps, scheme.maxVal)
        tableHeader =
            <TableRow>
                <TableCellS>
                    Value
                </TableCellS>
                <TableCellS>
                    Color
                </TableCellS>
            </TableRow>
        let tableRows = []
        for (let i = 0; i < gradient.length - 1; i++){
            tableRows.push(
                <TableRow key={'gradient-' + i}>
                    <TableCellS>
                        {gradient[i].ub.toFixed(scheme.displaySigFigs) + ' to ' + gradient[i + 1].ub.toFixed(scheme.displaySigFigs) }
                    </TableCellS>
                    <TableCellS>
                        <Box
                            sx={{
                                width: 10,
                                height: 10,
                                position: 'relative',
                                left: '50%',
                                right: '50%'
                            }}
                            bgcolor={'#'+gradient[i].hex}
                        />
                    </TableCellS>
                </TableRow>)
        }

        table = <>{tableRows}{lastRow}</>
    } else if (scheme.dataType === "option"){
        tableHeader =
            <TableRow>
                <TableCellS>
                    Value
                </TableCellS>
                <TableCellS>
                    Color
                </TableCellS>
            </TableRow>

        let tableRows = Object.entries(scheme.options).map(([key, val]) =>
            <TableRow key={key}>
                <TableCellS>
                    {makeTitle(key)}
                </TableCellS>
                <TableCellS>
                    <Box
                        sx={{
                            width: 10,
                            height: 10,
                            position: 'relative',
                            left: '50%',
                            right: '50%'
                        }}
                        bgcolor={val as string}
                    />
                </TableCellS>
            </TableRow>
        )

        table = <>{tableRows}{lastRow}</>
    }
    return (
        <>
            <TableS>
                <TableHead>
                    {tableHeader}
                </TableHead>
                <TableBody>
                    {table}
                </TableBody>
            </TableS>
        </>
    )
})

const SchemeTable = observer(props => {
    const { colorScheme } = props
    return (<SchemeComponent scheme={colorScheme}/>)
})


export default SchemeTable