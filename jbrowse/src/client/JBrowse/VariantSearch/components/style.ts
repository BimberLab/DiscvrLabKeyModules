import { styled } from '@mui/material/styles'

const PREFIX = 'VS';
export const classes = {
    root: `${PREFIX}-root`,
    formControl: `${PREFIX}-formControl`,
    actionWrapper: `${PREFIX}-actionWrapper`,
    filterContainer: `${PREFIX}-filterContainer`,
    filterRow: `${PREFIX}-filterRow`,
    textField: `${PREFIX}-textField`,
    submitButton: `${PREFIX}-submitButton`,
    valueInput: `${PREFIX}-valueInput`,
    submitAndExternal: `${PREFIX}-submitAndExternal`,
    centeredContent: `${PREFIX}-centeredContent`,
    arrowPaginationWrapper: `${PREFIX}-arrowPaginationWrapper`,
    cardActions: `${PREFIX}-cardActions`,
    formScroll: `${PREFIX}-formScroll`,
    addFilterExternalWrapper: `${PREFIX}-addFilterExternalWrapper`,
    card: `${PREFIX}-card`,
    highlighted: `${PREFIX}-highlighted`
}


export const Root = styled('div')(({ theme }) => ({
    [`&.${classes.formControl}`]: {
            minWidth: 200,
            marginRight: theme.spacing(2),
        },
    [`&.${classes.actionWrapper}`]: {
            display: 'flex',
            gap: theme.spacing(4)
        },
    [`&.${classes.filterContainer}`]: {
            display: 'flex',
            justifyContent: 'center',
            width: '100%',
            marginTop: theme.spacing(2),
        },
    [`&.${classes.filterRow}`]: {
            display: "flex",
            alignItems: "center",
            marginTop: theme.spacing(2),
            justifyContent: "center", // Add this line to center the contents of the filter map call
        },
    [`&.${classes.textField}`]: {
            marginLeft: theme.spacing(2),
            marginRight: theme.spacing(2),
        },
    [`&.${classes.submitButton}`]: {
            marginTop: theme.spacing(2),
        },
    [`&.${classes.valueInput}`]: {
            width: 200,
            marginLeft: theme.spacing(2),
        },
    [`&.${classes.submitAndExternal}`]: {
            display: "flex",
            gap: theme.spacing(2),
        },
    [`&.${classes.centeredContent}`]: {
            display: "flex",
            flexDirection: "column",
            justifyContent: "flex-start",
            alignItems: "center",
            "& > :not(:first-child)": {
                textAlign: "center",
            },
        },
    [`&.${classes.arrowPaginationWrapper}`]: {
            maxWidth: "50%",
            display: 'flex'
        },
    [`&.${classes.cardActions}`]: {
            display: 'flex',
            justifyContent: 'flex-end',
        },
    [`&.${classes.formScroll}`]: {
            width: '100%',
            margin: '0 auto',
            maxHeight: 'calc(100vh - 200px)',
            overflowY: 'auto',
        },
    [`&.${classes.addFilterExternalWrapper}`]: {
            display: 'flex',
            justifyContent: 'space-between',
            width: '100%',
        },
    [`&.${classes.card}`]: {
            backgroundColor: 'transparent',
            border: '1px solid rgba(0, 0, 0, 0.12)'
        },
    [`&.${classes.highlighted}`]: {
            border: '2px solid red',
            borderRadius: '4px'
        }
}))
