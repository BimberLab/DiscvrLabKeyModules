require("su2c/triggers").initScript(this);

function onInsert(row){
	row.date = row.date || new Date();
}