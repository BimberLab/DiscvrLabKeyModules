package org.labkey.onprc_billing.table;

import org.apache.log4j.Logger;
import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.ehr.EHRService;
import org.labkey.api.ldk.LDKService;
import org.labkey.api.ldk.table.AbstractTableCustomizer;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.QueryForeignKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.onprc_billing.ONPRC_BillingManager;
import org.labkey.onprc_billing.ONPRC_BillingSchema;

/**
 * User: bimber
 * Date: 1/7/14
 * Time: 5:57 PM
 */
public class ONPRC_BillingCustomizer extends AbstractTableCustomizer
{
    private static final Logger _log = Logger.getLogger(ONPRC_BillingCustomizer.class);

    public void customize(TableInfo table)
    {
        if (table instanceof AbstractTableInfo)
        {
            customizeSharedColumns((AbstractTableInfo)table);

            if (matches(table, "onprc_billing", "invoicedItems"))
            {
                customizeInvoicedItems((AbstractTableInfo) table);
            }
            else if (matches(table, "onprc_billing", "invoiceRuns"))
            {
                customizeInvoiceRuns((AbstractTableInfo) table);
            }
            else if (matches(table, "onprc_billing", "miscCharges"))
            {
                customizeMiscCharges((AbstractTableInfo) table);
            }
            else if (matches(table, "onprc_billing", "dataAccess"))
            {
                customizeDataAccess((AbstractTableInfo) table);
            }
            else if (matches(table, "onprc_billing", "chargeableItems"))
            {
                customizeChargeableItems((AbstractTableInfo) table);
            }
            else if (matches(table, "onprc_billing_public", "chargeableItems"))
            {
                customizeChargeableItems((AbstractTableInfo) table);
            }
            else if (matches(table, "onprc_billing", "aliases"))
            {
                customizeAliases((AbstractTableInfo) table);
            }
        }
    }

    private UserSchema _billingUserSchema = null;

    private UserSchema getBillingUserSchema(AbstractTableInfo table)
    {
        if (_billingUserSchema != null)
        {
            return _billingUserSchema;
        }

        Container c = ONPRC_BillingManager.get().getBillingContainer(table.getUserSchema().getContainer());
        if (c != null)
        {
            UserSchema us = QueryService.get().getUserSchema(table.getUserSchema().getUser(), c, ONPRC_BillingSchema.NAME);
            if (us != null && us.getContainer().hasPermission(table.getUserSchema().getUser(), ReadPermission.class))
            {
                _billingUserSchema = us;
                return us;
            }
        }

        return null;
    }

    private void customizeDataAccess(AbstractTableInfo table)
    {
        Container billingContainer = ONPRC_BillingManager.get().getBillingContainer(table.getUserSchema().getContainer());
        if (billingContainer == null)
        {
            return;
        }

        Container publicContainer = billingContainer.getChild("Public");
        if (publicContainer == null)
        {
            return;
        }

        UserSchema us = getUserSchema(table, "core", publicContainer);
        if (us == null)
        {
            return;
        }

        table.getColumn("userid").setFk(new QueryForeignKey(us, publicContainer, "UsersAndGroups", "UserId", "DisplayName"));
    }

    private void customizeMiscCharges(AbstractTableInfo table)
    {
        UserSchema us = getBillingUserSchema(table);
        if (us == null)
        {
            return;
        }

        ColumnInfo invoicedItemId = table.getColumn("invoicedItemId");
        if (invoicedItemId != null)
        {
            invoicedItemId.setFk(new QueryForeignKey(us, us.getContainer(), "invoicedItems", "objectid", "rowid"));
        }

        ColumnInfo sourceInvoicedItem = table.getColumn("sourceInvoicedItem");
        if (sourceInvoicedItem != null)
        {
            sourceInvoicedItem.setFk(new QueryForeignKey(us, us.getContainer(), "invoicedItems", "objectid", "rowid"));
        }

        ColumnInfo invoiceId = table.getColumn("invoiceId");
        if (invoiceId != null)
        {
            invoiceId.setFk(new QueryForeignKey(us, us.getContainer(), "invoiceRuns", "objectid", "rowid"));
        }
    }

    private void customizeInvoicedItems(AbstractTableInfo table)
    {
        table.getButtonBarConfig().setAlwaysShowRecordSelectors(true);
        table.setDetailsURL(DetailsURL.fromString("/onprc_billing/invoicedItemDetails.view?invoicedItem=${objectid}"));

        ColumnInfo col = table.getColumn("invoicedItemId");
        if (col != null)
        {
            UserSchema us = getBillingUserSchema(table);
            if (us != null)
            {
                col.setFk(new QueryForeignKey(us, null, "invoicedItems", "objectid", "rowid"));
            }
        }

        ColumnInfo idCol = table.getColumn("Id");
        if (idCol != null)
        {
            Container ehrContainer = EHRService.get().getEHRStudyContainer(table.getUserSchema().getContainer());
            if (ehrContainer != null)
            {
                idCol.setFk(new QueryForeignKey("study", ehrContainer, ehrContainer, table.getUserSchema().getUser(), "animal", "Id", "Id"));
                EHRService.get().appendCalculatedIdCols(table, "date");
            }
        }

        addAliasLookup(table, "debitedaccount");
        addAliasLookup(table, "creditedaccount");
    }

    private void addAliasLookup(AbstractTableInfo table, String sourceColName)
    {
        ColumnInfo sourceCol = table.getColumn(sourceColName);
        if (sourceCol != null && sourceCol.getFk() == null)
        {
            UserSchema us = getUserSchema(table, "onprc_billing_public");
            if (us == null)
            {
                us = getUserSchema(table, "onprc_billing");
            }

            if (us != null)
            {
                sourceCol.setFk(new QueryForeignKey(us, us.getContainer(), "aliases", "alias", "alias", true));
                sourceCol.setURL(DetailsURL.fromString("/query/executeQuery.view?schemaName=onprc_billing_public&query.queryName=aliases&query.alias~eq=${" + sourceColName + "}"));
            }
        }
    }

    private void customizeSharedColumns(AbstractTableInfo ti)
    {
        boolean found = false;
        for (String field : new String[]{"grant", "grantNumber"})
        {
            if (found)
                continue; //a table should never contain both of these anyway

            ColumnInfo grant = ti.getColumn(field);
            if (grant != null)
            {
                found = true;
                if (!ti.getName().equalsIgnoreCase("grants") && grant.getFk() == null)
                {
                    UserSchema us = getUserSchema(ti, "onprc_billing_public");
                    if (us != null)
                        grant.setFk(new QueryForeignKey(us, null, "grants", "grantNumber", "grantNumber"));
                }
            }
        }

        ColumnInfo account = ti.getColumn("account");
        if (account != null && !ti.getName().equalsIgnoreCase("accounts"))
        {
            account.setLabel("Alias");
            if (account.getFk() == null)
            {
                UserSchema us = getUserSchema(ti, "onprc_billing_public");
                if (us != null)
                {
                    account.setFk(new QueryForeignKey(us, null, "aliases", "alias", "alias", true));
                    account.setURL(DetailsURL.fromString("/query/executeQuery.view?schemaName=onprc_billing_public&query.queryName=aliases&query.alias~eq=${account}", us.getContainer()));
                }
            }
        }

        ColumnInfo projectNumber = ti.getColumn("projectNumber");
        if (projectNumber != null && !ti.getName().equalsIgnoreCase("grantProjects"))
        {
            UserSchema us = getUserSchema(ti, "onprc_billing_public");
            if (us != null)
            {
                projectNumber.setURL(DetailsURL.fromString("/query/executeQuery.view?schemaName=onprc_billing_public&query.queryName=grantProjects&query.projectNumber~eq=${projectNumber}", us.getContainer()));
            }
        }

        ColumnInfo chargeId = ti.getColumn("chargeId");
        if (chargeId != null)
        {
            UserSchema us = getUserSchema(ti, "onprc_billing_public");
            if (us != null){
                chargeId.setFk(new QueryForeignKey(us, null, "chargeableItems", "rowid", "name"));
            }
            chargeId.setLabel("Charge Name");
        }

        ColumnInfo rateId = ti.getColumn("rateId");
        if (rateId != null)
        {
            UserSchema us = getUserSchema(ti, "onprc_billing_public");
            if (us != null){
                rateId.setFk(new QueryForeignKey(us, null, "chargeableRates", "rowid", "rowid"));
            }
            rateId.setLabel("Rate");
        }

        ColumnInfo exemptionId = ti.getColumn("exemptionId");
        if (exemptionId != null)
        {
            UserSchema us = getUserSchema(ti, "onprc_billing_public");
            if (us != null){
                exemptionId.setFk(new QueryForeignKey(us, null, "chargeableRateExemptions", "rowid", "rowid"));
            }
            exemptionId.setLabel("Rate Exemption");
        }
    }

    private void customizeChargeableItems(AbstractTableInfo ti)
    {
        String activeRate = "activeRate";
        if (ti.getColumn(activeRate) == null)
        {
            SQLFragment sql = new SQLFragment("(SELECT max(rowid) as expr FROM " + ONPRC_BillingSchema.NAME + "." + ONPRC_BillingSchema.TABLE_CHARGE_RATES + " cr WHERE cr.chargeid = " + ExprColumn.STR_TABLE_ALIAS + ".rowid AND (cr.enddate IS NULL OR cr.enddate > {fn curdate()}) AND cr.startdate <= {fn curdate()})");
            ExprColumn col = new ExprColumn(ti, activeRate, sql, JdbcType.INTEGER, ti.getColumn("rowid"));
            col.setLabel("Active Rate");
            col.setIsUnselectable(true);
            ti.addColumn(col);
        }
        //NOTE: this is separated to allow linked schemas to use the same column
        ti.getColumn(activeRate).setFk(new QueryForeignKey(ti.getUserSchema(), null, ONPRC_BillingSchema.TABLE_CHARGE_RATES, "rowid", "rowid"));

        String totalExemptions = "totalExemptions";
        if (ti.getColumn(totalExemptions) == null)
        {
            SQLFragment sql = new SQLFragment("(SELECT count(rowid) as expr FROM " + ONPRC_BillingSchema.NAME + "." + ONPRC_BillingSchema.TABLE_CHARGE_RATE_EXEMPTIONS + " cr WHERE cr.chargeid = " + ExprColumn.STR_TABLE_ALIAS + ".rowid AND (cr.enddate IS NULL OR cr.enddate > {fn curdate()}) AND cr.startdate <= {fn curdate()})");
            ExprColumn col = new ExprColumn(ti, totalExemptions, sql, JdbcType.INTEGER, ti.getColumn("rowid"));
            col.setLabel("# Active Exemptions");
            col.setURL(DetailsURL.fromString("/query/executeQuery.view?schemaName=" + ONPRC_BillingSchema.NAME + "&query.queryName=" + ONPRC_BillingSchema.TABLE_CHARGE_RATE_EXEMPTIONS + "&query.chargeId~eq=${rowid}"));
            ti.addColumn(col);
        }

        String activeCreditAccount = "activeCreditAccount";
        if (ti.getColumn(activeCreditAccount) == null)
        {
            SQLFragment sql = new SQLFragment("(SELECT max(rowid) as expr FROM " + ONPRC_BillingSchema.NAME + "." + ONPRC_BillingSchema.TABLE_CREDIT_ACCOUNT + " cr WHERE cr.chargeid = " + ExprColumn.STR_TABLE_ALIAS + ".rowid AND (cr.enddate IS NULL OR cr.enddate > {fn curdate()}) AND cr.startdate <= {fn curdate()})");
            ExprColumn col = new ExprColumn(ti, activeCreditAccount, sql, JdbcType.INTEGER, ti.getColumn("rowid"));
            col.setLabel("Active Credit Alias");
            col.setIsUnselectable(true);
            ti.addColumn(col);
        }
        //NOTE: this is separated to allow linked schemas to use the same column
        ti.getColumn(activeCreditAccount).setFk(new QueryForeignKey(ti.getUserSchema(), null, ONPRC_BillingSchema.TABLE_CREDIT_ACCOUNT, "rowid", "rowid"));
    }

    private void customizeAliases(AbstractTableInfo ti)
    {
        LDKService.get().appendCalculatedDateColumns(ti, null, "budgetEndDate");
    }

    private void customizeInvoiceRuns(AbstractTableInfo table)
    {
        EHRService.get().customizeDateColumn(table, "billingPeriodStart");
    }
}
