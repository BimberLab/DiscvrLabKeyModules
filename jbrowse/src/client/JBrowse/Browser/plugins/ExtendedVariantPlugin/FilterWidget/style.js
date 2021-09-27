var _styles = require("@material-ui/core/styles")
export const style = (0, _styles.makeStyles)(function (theme) {
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
      button: {
         marginLeft: theme.spacing(2.5)
      }
   };
});
