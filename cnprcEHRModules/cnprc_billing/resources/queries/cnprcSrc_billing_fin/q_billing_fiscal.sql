select
 Bf_period_end                  AS Period_end
,Bf_fiscal_year                 AS Fiscal_year
,Bf_fiscal_period               AS Fiscal_period
,Bf_basegrant_year              AS Basegrant_year
,Bf_basegrant_cycle             AS Basegrant_cycle
,Bf_fiscalyear_begin            AS Fiscalyear_begin
,Bf_fiscalyear_end              AS Fiscalyear_end
,Bf_fiscal_range                AS Fiscal_range
,Dafis_univ_fiscal_yr
,Dafis_univ_fiscal_prd_cd
,Bf_period_begin                AS Period_begin
,Bf_fiscal_range_abbrev_1       AS Fiscal_range_abbrev_1
,Bf_fiscal_range_abbrev_2       AS Fiscal_range_abbrev_2
,Bf_fiscal_range_abbrev_3       AS Fiscal_range_abbrev_3
,Dafis_univ_fy_range
,Dafis_univ_fy_range_abbrev_1       AS Univ_fy_range_abbrev_1
,Days_in_period
,Objectid
,Date_time
from cnprcSrc_billing_fin.zbilling_fiscal
