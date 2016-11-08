select
 Gpe_pk
,Gpe_pid_fk                    AS Pid_fk
,Gpe_gpp_fk                    AS Gpp_fk
,Gpe_percent
,Gpe_person_months             AS Person_months
,Gpe_nih_key_personnel_yn      AS Nih_key_personnel_yn
,Gpe_exclude_from_reports_yn   AS Exclude_from_reports_yn
,Objectid
,Date_time
from cnprcSrc_billing_fin.zgrant_person_effort
