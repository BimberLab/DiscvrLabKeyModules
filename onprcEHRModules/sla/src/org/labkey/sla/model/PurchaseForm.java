package org.labkey.sla.model;

import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.action.CustomApiForm;
import org.labkey.api.data.Container;
import org.labkey.api.util.GUID;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class PurchaseForm implements CustomApiForm
{
    private Integer _rowid;
    private String _objectid;
    private GUID _containerid;

    private Integer _project;
    private String _account;
    private String _requestorid;
    private String _vendorid;
    private String _hazardslist;
    private Integer _dobrequired;

    // fields not provided by non-admin order submission
    private String _confirmationnum;
    private Date _orderdate;
    private String _orderby;
    private Integer _housingconfirmed;

    List<PurchaseDetails> _purchaseDetails = new ArrayList<>();

    public void setRowid(Integer rowid)
    {
        _rowid = rowid;
    }

    public Integer getRowid()
    {
        return _rowid;
    }

    public void setObjectid(String objectid)
    {
        _objectid = objectid;
    }

    public String getObjectid()
    {
        return _objectid;
    }

    public void setProject(Integer project)
    {
        _project = project;
    }

    public Integer getProject()
    {
        return _project;
    }

    public void setAccount(String account)
    {
        _account = account;
    }

    public String getAccount()
    {
        return _account;
    }

    public void setRequestorid(String requestorid)
    {
        _requestorid = requestorid;
    }

    public String getRequestorid()
    {
        return _requestorid;
    }

    public void setVendorid(String vendorid)
    {
        _vendorid = vendorid;
    }

    public String getVendorid()
    {
        return _vendorid;
    }

    public void setHazardslist(String hazardslist)
    {
        _hazardslist = hazardslist;
    }

    public String getHazardslist()
    {
        return _hazardslist;
    }

    public void setDobrequired(Integer dobrequired)
    {
        _dobrequired = dobrequired;
    }

    public Integer getDobrequired()
    {
        return _dobrequired;
    }

    public String getConfirmationnum()
    {
        return _confirmationnum;
    }

    public void setConfirmationnum(String confirmationnum)
    {
        _confirmationnum = confirmationnum;
    }

    public Date getOrderdate()
    {
        return _orderdate;
    }

    public void setOrderdate(Date orderdate)
    {
        _orderdate = orderdate;
    }

    public String getOrderby()
    {
        return _orderby;
    }

    public void setOrderby(String orderby)
    {
        _orderby = orderby;
    }

    public Integer getHousingconfirmed()
    {
        return _housingconfirmed;
    }

    public void setHousingconfirmed(Integer housingconfirmed)
    {
        _housingconfirmed = housingconfirmed;
    }

    public List<PurchaseDetails> getPurchaseDetails()
    {
        return _purchaseDetails;
    }

    public void setPurchaseDetails(List<PurchaseDetails> purchaseDetails)
    {
        _purchaseDetails = purchaseDetails;
    }

    public void addPurchaseDetail(PurchaseDetails purchaseDetail)
    {
        _purchaseDetails.add(purchaseDetail);
    }

    public GUID getContainerid()
    {
        return _containerid;
    }

    public void setContainerid(GUID containerid)
    {
        _containerid = containerid;
    }

    @Override
    public void bindProperties(Map<String, Object> props)
    {
        // set the purchase level properties
        if (props.containsKey("rowid") && props.get("rowid") != null)
            _rowid = Integer.parseInt(props.get("rowid").toString());
        if (props.containsKey("objectid") && props.get("objectid") != null)
            _objectid = props.get("objectid").toString();
        if (props.containsKey("project") && props.get("project") != null)
            _project = Integer.parseInt(props.get("project").toString());
        if (props.containsKey("account") && props.get("account") != null)
            _account = props.get("account").toString();
        if (props.containsKey("requestorid") && props.get("requestorid") != null)
            _requestorid = props.get("requestorid").toString();
        if (props.containsKey("vendorid") && props.get("vendorid") != null)
            _vendorid = props.get("vendorid").toString();
        if (props.containsKey("hazardslist") && props.get("hazardslist") != null)
            _hazardslist = props.get("hazardslist").toString();
        if (props.containsKey("dobrequired") && props.get("dobrequired") != null)
            _dobrequired = Integer.parseInt(props.get("dobrequired").toString());

        // parse the array of purchase details records
        if (props.containsKey("details"))
        {
            JSONArray details = (JSONArray) props.get("details");
            for (JSONObject detail : details.toJSONObjectArray())
            {
                addPurchaseDetail(new PurchaseDetails(detail));
            }
        }
    }
}
