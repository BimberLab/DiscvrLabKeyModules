<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    Container c = getContainer();
    User user = getUser();
%>
<div id="app" class='App'></div>
<script src="/labkey/snprc_scheduler/app/app.js" type="application/javascript"></script>