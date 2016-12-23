Ext.define('App.view.Scheduler', {
    extend        : 'Sch.panel.SchedulerGrid',
    alias         : 'widget.scheduler',
    reference     : 'scheduler',
    startDate     : new Date(),
    //endDate       : new Date(),
    startTime     : 6,
    endTime       : 20,
    resourceStore : 'resource',
    eventStore    : 'event',
    style         : 'border: 1px solid #d0d0d0;',

    readOnly             : true,  // disables the abilitiy to click in calendar to create event
    showTodayLine        : true,
    calendarViewPreset   : 'week',
    mode                 : 'calendar',
    eventResizeHandles   : 'none',
    eventBodyTemplate    :'<b>{Name:htmlEncode}</b><br/>{ResourceName:htmlEncode}<br/>{UserDisplayName:htmlEncode}',
    snapToIncrement      : true,
    highlightCurrentTime : true,
    calendarTimeAxisCfg  : {
        height : 30
    },

    tbar: [
        {
            text : 'Previous',
            iconCls: 'x-fa fa-arrow-circle-left',
            handler: function (btn) {
                var scheduler = btn.up('scheduler');
                scheduler.timeAxis.shift(-7, Sch.util.Date.DAY);
            }
        },
        {
            text : 'Today',
            handler: function (btn) {
                var scheduler = btn.up('scheduler');
                // Clear time here so date adjustment wouldn't result in 2 days span
                scheduler.setStart(Sch.util.Date.clearTime(new Date()));
            }
        },
        {
            text : 'Next',
            iconCls: 'x-fa fa-arrow-circle-right',
            iconAlign: 'right',
            handler: function (btn) {
                var scheduler = btn.up('scheduler');
                scheduler.timeAxis.shift(7, Sch.util.Date.DAY);
            }
        },
        '',
        {
            text         : 'Select Date...',
            scope        : this,
            menu         : Ext.create('Ext.menu.DatePicker', {
                handler : function (dp, date) {
                    var scheduler = dp.up('scheduler');
                    scheduler.setStart(Sch.util.Date.clearTime(date));
                }
            })
        },        '->',
        {
            text   : 'Create New Event',
            iconCls: 'x-fa fa-plus-circle',
            hidden: !LABKEY.user.canInsert,
            scope: this,
            handler: function (btn) {
                var scheduler = btn.up('scheduler');

                Ext.create('Ext.window.Window', {
                    title : 'Create New Event',
                    autoShow : true,
                    modal : true,
                    items : [{
                        xtype: 'eventform',
                        editable : true,
                        scheduler : scheduler
                    }]
                });
            }
        }
    ],

    eventRenderer : function (event, resource, data) {
        data.style = 'border-color:' + resource.get('Color');
        event.data['ResourceName'] = resource.get('Name');
        var userRecord = Ext.getStore('users').findRecord('UserId', event.get('UserId'));
        event.data['UserDisplayName'] = userRecord != null ? userRecord.get('DisplayName') : event.get('UserId');
        return event.data;
    },

    onEventCreated : function (newEventRecord) {
        this.getEventSelectionModel().select(newEventRecord);
    }
});