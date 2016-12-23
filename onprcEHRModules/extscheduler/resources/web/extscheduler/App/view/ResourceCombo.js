
Ext.define('App.view.ResourceCombo', {
    extend       : 'Ext.form.field.ComboBox',
    alias        : 'widget.resourcecombo',
    store        : 'resource',
    queryMode    : 'local',
    valueField   : 'Id',
    displayField : 'Name',
    editable     : false
});