select
 Gch_pk
,Gch_gpp_fk        AS Gpp_fk
,Gch_pc_charge_id  AS Pc_charge_id
,Gch_pc_account_id AS Pc_account_id
,Gch_status        AS Status
,Objectid
,Date_time
from cnprcSrc_billing_fin.zgrant_charge
