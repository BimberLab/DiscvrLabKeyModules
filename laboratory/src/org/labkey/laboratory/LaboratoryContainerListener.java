package org.labkey.laboratory;

import org.apache.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.module.SimpleModuleContainerListener;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.view.HttpView;
import org.labkey.laboratory.query.LaboratoryWorkbooksTable;
import org.labkey.laboratory.query.WorkbookModel;

import java.beans.PropertyChangeEvent;

/**
 * User: bimber
 * Date: 1/31/13
 * Time: 7:19 PM
 */
public class LaboratoryContainerListener extends SimpleModuleContainerListener
{
    Logger _log = Logger.getLogger(LaboratoryContainerListener.class);

    public LaboratoryContainerListener(Module owner)
    {
        super(owner);
    }

    @Override
    public void containerCreated(Container c, User user)
    {
        super.containerCreated(c, user);

        if (c.isWorkbook())
        {
            if (c.getParent().getActiveModules().contains(ModuleLoader.getInstance().getModule(LaboratoryModule.class)))
            {
                try
                {
                    LaboratoryManager.get().initLaboratoryWorkbook(c, user);
                }
                catch (Exception e)
                {
                    _log.error("Unable to update laboratory workbooks table", e);
                }
            }
        }

        //attempt to populate default values on load
        if (user != null && !c.isWorkbook() && c.getActiveModules().contains(ModuleLoader.getInstance().getModule(LaboratoryModule.class)))
        {
            try
            {
                LaboratoryManager.get().populateDefaultData(user, c, null);
            }
            catch (IllegalArgumentException e)
            {
                _log.warn("Unable to populate default values for laboratory module", e);
            }
            catch (BatchValidationException e)
            {
                //ignore, since this may just indicate the table already has these values
            }
        }
    }

    @Override
    public void containerMoved(Container c, Container oldParent, User u)
    {
        super.containerMoved(c, oldParent, u);

        if (c.isWorkbook())
        {
            //determine if we have a record of this workbook, and delete if true:
            TableInfo ti = LaboratorySchema.getInstance().getTable(LaboratorySchema.TABLE_WORKBOOKS);
            TableSelector ts = new TableSelector(ti, new SimpleFilter(FieldKey.fromString(LaboratoryWorkbooksTable.WORKBOOK_COL), c.getId()), null);
            if (ts.exists())
            {
                Table.delete(ti, c.getId());
            }

            //then re-register
            if (c.getActiveModules().contains(ModuleLoader.getInstance().getModule(LaboratoryModule.class)))
            {
                try
                {
                    LaboratoryManager.get().initLaboratoryWorkbook(c, u);
                }
                catch (Exception e)
                {
                    _log.error("Unable to init laboratory workbook", e);
                }
            }
        }
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt)
    {
        super.propertyChange(evt);

        if (evt.getPropertyName().equals(ContainerManager.Property.Name.name()))
        {
            if (evt instanceof ContainerManager.ContainerPropertyChangeEvent)
            {
                ContainerManager.ContainerPropertyChangeEvent ce = (ContainerManager.ContainerPropertyChangeEvent) evt;
                if (ce.container.isWorkbook())
                {
                    TableInfo ti = LaboratorySchema.getInstance().getTable(LaboratorySchema.TABLE_WORKBOOKS);
                    TableSelector ts = new TableSelector(ti, new SimpleFilter(FieldKey.fromString(LaboratoryWorkbooksTable.WORKBOOK_COL), ce.container.getId()), null);
                    if (ts.exists())
                    {
                        try
                        {
                            Integer rowId = Integer.parseInt(ce.container.getName());
                            WorkbookModel w = ts.getObject(ce.container.getId(), WorkbookModel.class);
                            w.setWorkbookId(rowId);
                            Table.update(ce.user, ti, w, ce.container.getId());
                        }
                        catch (NumberFormatException e)
                        {

                        }
                    }
                }
            }
        }
        else if (evt.getPropertyName().equals(ContainerManager.Property.Policy.name()))
        {
            if (evt instanceof ContainerManager.ContainerPropertyChangeEvent)
            {
                ContainerManager.ContainerPropertyChangeEvent ce = (ContainerManager.ContainerPropertyChangeEvent)evt;

                User u = ce.user;
                if (u == null && HttpView.hasCurrentView())
                    u = HttpView.currentView().getViewContext().getUser();

                if (u == null || !ce.container.hasPermission(u, InsertPermission.class))
                    return;

                if (ce.container.getActiveModules().contains(ModuleLoader.getInstance().getModule(LaboratoryModule.class)))
                {
                    try
                    {
                        LaboratoryManager.get().initWorkbooksForContainer(u, ce.container);
                    }
                    catch (Exception e)
                    {
                        _log.error("Unable to update laboratory workbooks table", e);
                    }

                    //attempt to populate default values on load
                    try
                    {
                        LaboratoryManager.get().populateDefaultData(u, ce.container, null);
                    }
                    catch (IllegalArgumentException e)
                    {
                        _log.error("Unable to populate defaults in laboratory module tables", e);
                    }
                    catch (BatchValidationException e)
                    {
                        //ignore, since this may just indicate the table already has these values
                    }
                }
            }
        }
    }

    @Override
    protected void purgeTable(UserSchema userSchema, TableInfo table, Container c, User u)
    {
        if (table.getName().equalsIgnoreCase(LaboratorySchema.TABLE_WORKBOOKS))
        {
            if (!c.isWorkbook())
            {
                SQLFragment sql = new SQLFragment("DELETE FROM laboratory.workbooks WHERE " + LaboratoryWorkbooksTable.PARENT_COL + " = ?", c.getId());
                new SqlExecutor(table.getSchema()).execute(sql);
            }
        }
        else
        {
            super.purgeTable(userSchema, table, c, u);
        }
    }
}
