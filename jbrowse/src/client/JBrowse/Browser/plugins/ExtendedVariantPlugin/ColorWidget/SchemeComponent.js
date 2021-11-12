import AddIcon from '@material-ui/icons/Add'
import ClearIcon from '@material-ui/icons/Clear'
import { observer } from 'mobx-react'
import React, { useState } from 'react'
import { readConfObject } from '@jbrowse/core/configuration'
import {colorSchemes} from "./colorSchemes"
import {style as styles} from "./style";
import { generateGradient } from "./colorUtil"

import {
  MenuItem,
  FormControl,
  Select,
  IconButton,
  List,
  ListItem,
  Tooltip,
  makeStyles,
  TextField,
  TableCell,
  TableRow,
  Table,
  TableBody,
  Box
} from '@material-ui/core'

function makeTitle(key){
   if (key == "minVal"){
      return "Lower"
   }
   if (key == "maxVal"){
      return "Upper"
   }
   return key
}

const SchemeComponent = observer(props => {
   const classes = styles()
   let scheme = props.scheme
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
                 backgroundColor: 'grey',
                 position: 'relative',
                 left: '50%',
                 right: '50%'
               }}
             />
         </TableCell>
      </TableRow>

   if(scheme.dataType == "number"){
      let gradient = generateGradient(scheme.options.minVal, scheme.options.maxVal, scheme.gradientSteps, scheme.maxVal)
      let tableHeader =
      <TableRow>
         <TableCell className={classes.tableCell}>
            Value
         </TableCell>
         <TableCell className={classes.tableCell}>
            Color
         </TableCell>
      </TableRow>
      let tableRows = []
      for(let i = 0; i < gradient.length; i++){
         tableRows.push(
            <TableRow>
               <TableCell className={classes.tableCell}>
                  {gradient[i].ub.toFixed(scheme.displaySigFigs)}
               </TableCell>
               <TableCell className={classes.tableCell}>
                  <Box
                     sx={{
                       width: 10,
                       height: 10,
                       backgroundColor: '#'+gradient[i].hex,
                       position: 'relative',
                       left: '50%',
                       right: '50%'
                     }}
                   />
                </TableCell>
            </TableRow>)
      }

       table = <> {tableHeader} {tableRows} {lastRow} </>
   } else if (scheme.dataType == "option"){
      let tableHeader =
         <TableRow>
            <TableCell className={classes.tableCell}>
               Value
            </TableCell>
            <TableCell className={classes.tableCell}>
               Color
            </TableCell>
         </TableRow>

      let tableRows = Object.entries(scheme.options).map(([key, val]) =>
         <TableRow>
            <TableCell className={classes.tableCell}>
               {makeTitle(key)}
            </TableCell>
            <TableCell className={classes.tableCell}>
               <Box
                  sx={{
                    width: 10,
                    height: 10,
                    backgroundColor: val,
                    position: 'relative',
                    left: '50%',
                    right: '50%'
                  }}
                />
            </TableCell>
         </TableRow>
     )

     table = <> {tableHeader} {tableRows} {lastRow} </>
   }
   return (
       <>
         <Table className={classes.table}>
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