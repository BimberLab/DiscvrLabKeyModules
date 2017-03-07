select
 Gyr_pk
,Gyr_gpp_fk                   AS Gpp_fk
,Gyr_budget_yr                AS Budget_yr
,Gyr_award_status             AS Award_status
,Gyr_supplement_yn            AS Supplement_yn
,Gyr_begin_date               AS Begin_date
,Gyr_end_date                 AS End_date
,Gyr_direct_costs             AS Direct_costs
,Gyr_total_costs              AS Total_costs
,Gyr_brate_direct_costs       AS Brate_direct_costs
,Gyr_brate_total_costs        AS Brate_total_costs
,Gyr_sponsored_pgm_ref_no     AS Sponsored_pgm_ref_no
,Gyr_gpp_totals_indicator_yn  AS Totals_indicator_yn
,Gyr_base_grant_year_end      AS Base_grant_year_end
,Gyr_submission_date          AS Submission_date
,Gyr_special_funding_type     AS Special_funding_type
,Gyr_last_reported_date       AS Last_reported_date
,Objectid
,Date_time
from cnprcSrc_billing_fin.zgrant_year
