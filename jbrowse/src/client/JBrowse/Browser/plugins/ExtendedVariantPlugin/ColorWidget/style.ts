var _styles = require("@material-ui/core/styles")
export const style = (_styles.makeStyles)(function (theme) {
   return {
      schemeControl: {
         margin: theme.spacing(2),
         padding: theme.spacing(1, 3, 1, 1),
         minWidth: 100,
      },
      table: {
         padding: 0,
         display: 'block'
      },
      tableCell: {
         textAlign: 'center',
         padding: theme.spacing(0.75, 0, 0.75, 1),
      },
   };
});
