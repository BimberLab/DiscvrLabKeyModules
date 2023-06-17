var _styles = require("@material-ui/core/styles")

export const style = (_styles.makeStyles)(function (theme) {
   return {
      filterGroup: {
         paddingTop: theme.spacing(2),
         paddingBottom: theme.spacing(2)
      },
      filterOption: {
         paddingLeft: theme.spacing(5),
         paddingRight: theme.spacing(5),
         paddingBottom: theme.spacing(1)
      },
      root: {
         padding: theme.spacing(1, 3, 1, 1),
         background: theme.palette.background.default,
         overflowX: 'hidden',
      },
      formControl: {
         margin: theme.spacing(1),
         minWidth: 100,
      },
      numValueControl: {
         margin: theme.spacing(1),
         width: 100,
      },
      addNewControl: {
         margin: theme.spacing(1),
         padding: theme.spacing(2),
         minWidth: 400,
         display: 'flex'
      },
      table: {
         padding: 0,
         display: 'block'
      },
      tableCell: {
         textAlign: 'center',
         padding: theme.spacing(0.75, 0, 0.75, 1),
      },
      button: {
         maxWidth: 150,
         marginRight: theme.spacing(2)
      },
   };
});
