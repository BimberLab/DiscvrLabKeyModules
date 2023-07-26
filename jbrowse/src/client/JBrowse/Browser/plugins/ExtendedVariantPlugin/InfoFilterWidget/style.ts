import { styled } from '@mui/material/styles'

const PREFIX = 'IFW';
export const classes = {
   root: `${PREFIX}-root`,
   filterGroup: `${PREFIX}-filterGroup`,
   filterOption: `${PREFIX}-filterOption`,
   formControl: `${PREFIX}-formControl`,
   numValueControl: `${PREFIX}-numValueControl`,
   addNewControl: `${PREFIX}-addNewControl`,
   table: `${PREFIX}-table`,
   tableCell: `${PREFIX}-tableCell`,
   button: `${PREFIX}-button`
}

export const Root = styled('div')(({ theme }) => ({
   [`&.${classes.filterGroup}`]: {
         paddingTop: theme.spacing(2),
         paddingBottom: theme.spacing(2)
      },
   [`&.${classes.filterOption}`]: {
         paddingLeft: theme.spacing(5),
         paddingRight: theme.spacing(5),
         paddingBottom: theme.spacing(1)
      },
   [`&.${classes.root}`]: {
         padding: theme.spacing(1, 3, 1, 1),
         background: theme.palette.background.default,
         overflowX: 'hidden',
      },
   [`&.${classes.formControl}`]: {
         margin: theme.spacing(1),
         minWidth: 100,
      },
   [`&.${classes.numValueControl}`]: {
         margin: theme.spacing(1),
         width: 100,
      },
   [`&.${classes.addNewControl}`]: {
         margin: theme.spacing(1),
         padding: theme.spacing(2),
         minWidth: 400,
         display: 'flex'
      },
   [`&.${classes.table}`]: {
         padding: 0,
         display: 'block'
      },
   [`&.${classes.tableCell}`]: {
         textAlign: 'center',
         padding: theme.spacing(0.75, 0, 0.75, 1),
      },
   [`&.${classes.button}`]: {
         maxWidth: 150,
         marginRight: theme.spacing(2)
      }
}))
