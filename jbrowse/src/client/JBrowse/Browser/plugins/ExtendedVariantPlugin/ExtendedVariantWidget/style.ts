var _styles = require("@material-ui/core/styles")
export const style = (_styles.makeStyles)(function (theme) {
    return {
        table: {
            padding: 0,
            display: 'block'
        },
        link: {
            padding: theme.spacing(5)
        },
        message: {
            paddingTop: theme.spacing(5),
            paddingLeft: theme.spacing(5),
            paddingRight: theme.spacing(5),
            maxWidth: 500
        },
        paperRoot: {
            background: theme.palette.grey[100]
        },
        fieldRow: {
            display: 'flex',
            flexWrap: 'wrap'
        },
        fieldName: {
            wordBreak: 'break-all',
            minWidth: '120px',
            borderBottom: '1px solid #0003',
            background: theme.palette.grey[200],
            marginRight: theme.spacing(1),
            padding: theme.spacing(0.5)
        },
        fieldValue: {
            wordBreak: 'break-word',
            maxWidth: 500,
            padding: theme.spacing(0.5),
            overflow: 'auto'
        }
    };
});
