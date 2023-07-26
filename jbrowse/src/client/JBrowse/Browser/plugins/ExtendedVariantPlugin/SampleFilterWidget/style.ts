import { styled } from '@mui/material/styles'

const PREFIX = 'SFW';
export const classes = {
   root: `${PREFIX}-root`,
   formControl: `${PREFIX}-formControl`,
   button: `${PREFIX}-button`
}

export const Root = styled('div')(({ theme }) => ({
   [`&.${classes.formControl}`]: {
         margin: theme.spacing(1),
         minWidth: 100,
      },
   [`&.${classes.button}`]: {
         maxWidth: 125,
         marginLeft: theme.spacing(2)
      }
}))
