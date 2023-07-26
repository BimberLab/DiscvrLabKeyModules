import { styled } from '@mui/material/styles'

const PREFIX = 'EVW';
export const classes = {
    root: `${PREFIX}-root`,
    table: `${PREFIX}-table`,
    link: `${PREFIX}-link`,
    message: `${PREFIX}-message`,
    paperRoot: `${PREFIX}-paperRoot`,
    fieldRow: `${PREFIX}-fieldRow`,
    fieldName: `${PREFIX}-fieldName`,
    fieldValue: `${PREFIX}-fieldValue`,
}

export const Root = styled('div')(({ theme }) => ({
    [`&.${classes.table}`]: {
        padding: 0,
        display: 'block'
    },
    [`&.${classes.link}`]: {
        padding: theme.spacing(5)
    },
    [`&.${classes.message}`]: {
        paddingTop: theme.spacing(5),
        paddingLeft: theme.spacing(5),
        paddingRight: theme.spacing(5),
        maxWidth: 500
    },
    [`&.${classes.paperRoot}`]: {
        background: theme.palette.grey[100]
    },
    [`&.${classes.fieldRow}`]: {
        display: 'flex',
        flexWrap: 'wrap'
    },
    [`&.${classes.fieldName}`]: {
        wordBreak: 'break-all',
        minWidth: 120,
        borderBottom: '1px solid #0003',
        background: theme.palette.grey[200],
        marginRight: theme.spacing(1),
        padding: theme.spacing(0.5)
    },
    [`&.${classes.fieldValue}`]: {
        wordBreak: 'break-word',
        maxWidth: 500,
        padding: theme.spacing(0.5),
        overflow: 'auto'
    }
}))
