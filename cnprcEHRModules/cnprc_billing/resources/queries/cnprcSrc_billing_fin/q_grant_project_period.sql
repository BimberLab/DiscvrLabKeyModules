select
 Gpp_pk
,Gpp_grant_type              AS Grant_type
,Gpp_ref_no                  AS Ref_no
,Gpp_title                   AS Title
,Gpp_agency                  AS Agency
,Gpp_status                  AS Status
,Gpp_ucd_per_fk              AS Ucd_per_fk
,Gpp_center_unit_fk          AS Center_unit_fk
,Gpp_funding_type            AS Funding_type
,Gpp_begin_date              AS Begin_date
,Gpp_end_date                AS End_date
,Gpp_direct_costs            AS Direct_costs
,Gpp_total_costs             AS Total_costs
,Gpp_brate_direct_costs      AS Brate_direct_costs
,Gpp_brate_total_costs       AS Brate_total_costs
,Gpp_admin_department        AS Admin_department
,Gpp_primate_yn              AS Primate_yn
,Gpp_aids_grant_yn           AS Aids_grant_yn
,Gpp_phs_yn                  AS Phs_yn
,Gpp_service_only_yn         AS Service_only_yn
,Gpp_flag_1                  AS Flag_1
,Gpp_flag_2                  AS Flag_2
,Gpp_sub_institution         AS Sub_institution
,Gpp_sub_pi_name             AS Sub_pi_name
,Gpp_comment_1               AS Comment_1
,Gpp_comment_2               AS Comment_2
,Gpp_dafis_award_num         AS Dafis_award_num
,Gpp_sub_agency              AS Sub_agency
,Gpp_grant_affiliation_type  AS Grant_affiliation_type
,Gpp_sub_pi_per_fk           AS Sub_pi_per_fk
,Gpp_submission_date         AS Submission_date
,Gpp_special_funding_type    AS Special_funding_type
,Gpp_last_reported_date      AS Last_reported_date
,Gpp_base_grant_indicator_yn AS Base_grant_indicator_yn
,Objectid
,Date_time
from cnprcSrc_billing_fin.zgrant_project_period
