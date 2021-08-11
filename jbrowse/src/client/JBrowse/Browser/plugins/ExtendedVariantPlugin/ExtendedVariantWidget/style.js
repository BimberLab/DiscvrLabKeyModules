var _styles = require("@material-ui/core/styles")
export const style = (0, _styles.makeStyles)(function (theme) {
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
     field: {
       display: 'flex',
       flexWrap: 'wrap'
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
     }

     };
    });
