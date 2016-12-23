Ext.define('App.view.InfoPanelModel', {
    extend   : 'Ext.app.ViewModel',
    alias    : 'viewmodel.infopanel',
    data     : {
        //eventRecord should be defined here because otherwise we can't bind {!eventRecord}
        eventRecord    : null,
        defaultMinTime : null,
        defaultMaxTime : null,
        startDateValue : null
    },
    formulas : {
        StartDate : {
            bind : '{eventRecord.StartDate}',
            get  : function (date) {
                // this notify needs to be sure minValue will be bound before value
                this.set('startDateValue', date);
                this.notify();

                return date;
            },
            set  : function (date) {
                var eventRecord = this.get('eventRecord');
                if (!eventRecord || !date) return;

                var time = eventRecord.getStartDate();
                // use time from original record
                Sch.util.Date.copyTimeValues(date, time);

                // this notify needs to be sure minValue will be bound before value
                this.set('startDateValue', date);
                this.notify();

                // true to keep duration
                eventRecord.setStartDate(date, true);
            }
        },

        StartTime : {
            bind : '{eventRecord.StartDate}',
            get  : function (time) {
                return time;
            },
            set  : function (time) {
                var eventRecord = this.get('eventRecord');
                if (!eventRecord || !time) return;

                var date = Ext.Date.clone(eventRecord.getStartDate());
                // use date from original record
                Sch.util.Date.copyTimeValues(date, time);
                // true to keep duration
                eventRecord.setStartDate(date, true);
            }
        },

        EndDate : {
            bind : '{eventRecord.EndDate}',
            get  : function (date) {
                return date;
            },
            set  : function (date) {
                var eventRecord = this.get('eventRecord');
                if (!eventRecord || !date) return;

                var time = eventRecord.getEndDate();
                // use time from original record
                Sch.util.Date.copyTimeValues(date, time);
                eventRecord.setEndDate(date);
            }
        },

        EndTime : {
            bind : '{eventRecord.EndDate}',
            get  : function (time) {
                return time;
            },
            set  : function (time) {
                var eventRecord = this.get('eventRecord');
                if (!eventRecord || !time) return;

                var date = Ext.Date.clone(eventRecord.getEndDate());
                // use date from original record
                Sch.util.Date.copyTimeValues(date, time);
                eventRecord.setEndDate(date);
            }
        },

        minEndDate : function (get) {
            var date = get('startDateValue');
            // true to clone the date before time clearing
            return date ? Ext.Date.clearTime(date, true) : null;
        },

        minEndTime : function (get) {
            var startDate = get('StartDate'),
                    endDate   = get('EndDate');

            if (startDate && endDate) {
                var cleanStartDate = Ext.Date.clearTime(startDate, true),
                        cleanEndDate   = Ext.Date.clearTime(endDate, true);

                if (Ext.Date.isEqual(cleanStartDate, cleanEndDate)) {
                    return Ext.Date.clone(startDate);
                }
            }

            return get('defaultMinTime');
        }
    }
});