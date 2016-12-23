//This controller is watching for viewport and child views which don't have their own viewController.
Ext.define('App.view.ViewportController', {
    extend : 'Ext.app.ViewController',
    alias  : 'controller.viewport',

    control : {
        'schedulergrid' : {
            eventselect   : 'onEventSelect',
            eventdeselect : 'onEventDeselect',
            viewchange    : 'onViewChange'
        }
    },

    onEventSelect : function (selectionModel, eventRecord) {
        var refs = this.getReferences(),
            infopanelVM = refs.infopanel.getViewModel(),
            scheduler   = selectionModel.view.ownerGrid;

        infopanelVM.set({
            'eventRecord'    : eventRecord,
            'defaultMinTime' : scheduler.getStart(),
            'defaultMaxTime' : scheduler.getEnd()
        });

        //refs.eventResourceField.focus();
    },

    onEventDeselect : function () {
        var refs        = this.getReferences(),
                infopanelVM = refs.infopanel.getViewModel();

        infopanelVM.set({
            'eventRecord'    : null,
            'defaultMinTime' : null,
            'defaultMaxTime' : null
        });

        //refs.eventResourceField.focus();
    },

    onViewChange : function (timelinePanel) {
        var vm = this.getViewModel();

        vm.set('endDate', timelinePanel.getEndDate());
    },

    onResourceFilterChange : function (combo, selectedResourceIds) {
        // Both schedulers share store, so we can use event store from any of them
        var eventStore = this.getReferences().scheduler.eventStore;

        //true to avoid double full view repaints
        eventStore.clearFilter(true);

        eventStore.filterBy(function (eventRecord) {
            return eventRecord.getResourceId() in selectedResourceIds;
        });
    }
});