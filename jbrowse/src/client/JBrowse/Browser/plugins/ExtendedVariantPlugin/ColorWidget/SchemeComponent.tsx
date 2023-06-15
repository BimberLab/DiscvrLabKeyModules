import {observer} from 'mobx-react'
import React from 'react'
import {style as styles} from "./style";
import {generateGradient} from "./colorUtil"

import {Box, Table, TableBody, TableCell, TableHead, TableRow} from '@material-ui/core'

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
   const classes = styles()
   let scheme = props.scheme as any
   let tableHeader = <></>
   let table = <></>
   const lastRow =
      <TableRow>
         <TableCell className={classes.tableCell}>
            Other
         </TableCell>
         <TableCell className={classes.tableCell}>
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
         </TableCell>
      </TableRow>

   if (scheme.dataType === "number"){
      let gradient = generateGradient(scheme.options.minVal, scheme.options.maxVal, scheme.gradientSteps, scheme.maxVal)
      tableHeader =
      <TableRow>
         <TableCell className={classes.tableCell}>
            Value
         </TableCell>
         <TableCell className={classes.tableCell}>
            Color
         </TableCell>
      </TableRow>
      let tableRows = []
      for (let i = 0; i < gradient.length - 1; i++){
         tableRows.push(
            <TableRow key={'gradient-' + i}>
               <TableCell className={classes.tableCell}>
                  {gradient[i].ub.toFixed(scheme.displaySigFigs) + ' to ' + gradient[i + 1].ub.toFixed(scheme.displaySigFigs) }
               </TableCell>
               <TableCell className={classes.tableCell}>
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
                </TableCell>
            </TableRow>)
      }

       table = <>{tableRows}{lastRow}</>
   } else if (scheme.dataType === "option"){
      tableHeader =
         <TableRow>
            <TableCell className={classes.tableCell}>
               Value
            </TableCell>
            <TableCell className={classes.tableCell}>
               Color
            </TableCell>
         </TableRow>

      let tableRows = Object.entries(scheme.options).map(([key, val]) =>
         <TableRow key={key}>
            <TableCell className={classes.tableCell}>
               {makeTitle(key)}
            </TableCell>
            <TableCell className={classes.tableCell}>
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
            </TableCell>
         </TableRow>
     )

     table = <>{tableRows}{lastRow}</>
   }
   return (
       <>
         <Table className={classes.table}>
             <TableHead>
               {tableHeader}
             </TableHead>
            <TableBody>
               {table}
            </TableBody>
         </Table>
      </>
   )
})

const SchemeTable = observer(props => {
   const { colorScheme } = props
   return (<SchemeComponent scheme={colorScheme}/>)
})


export default SchemeTable