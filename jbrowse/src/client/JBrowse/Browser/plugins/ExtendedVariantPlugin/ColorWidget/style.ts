import { styled } from '@mui/material/styles'

const PREFIX = 'CW';
export const classes = {
   root: `${PREFIX}-root`,
   schemeControl: `${PREFIX}-schemeControl`,
   table: `${PREFIX}-table`,
   tableCell: `${PREFIX}-tableCell`
}

export const Root = styled('div')(({ theme }) => ({
   [`&.${classes.schemeControl}`]: {
      margin: theme.spacing(2),
      padding: theme.spacing(1, 3, 1, 1),
      minWidth: 100,
   },
   [`&.${classes.table}`]: {
      padding: 0,
      display: 'block'
   },
   [`&.${classes.tableCell}`]: {
      textAlign: 'center',
      padding: theme.spacing(0.75, 0, 0.75, 1),
   }
}))
