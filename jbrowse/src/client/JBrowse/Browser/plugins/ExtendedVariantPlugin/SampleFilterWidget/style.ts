var _styles = require("@material-ui/core/styles")
export const style = (_styles.makeStyles)(function (theme) {
   return {
      formControl: {
         margin: theme.spacing(1),
         minWidth: 100,
      },
      button: {
         maxWidth: 125,
         marginLeft: theme.spacing(2)
      }
   };
});
