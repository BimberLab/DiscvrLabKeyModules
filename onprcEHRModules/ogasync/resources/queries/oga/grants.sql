SELECT
  v.OGA_AWARD_NUMBER,

  count(distinct PI_EMP_NUM) as total_PI_EMP_NUM,
  max(PI_EMP_NUM) as PI_EMP_NUM,

  count(distinct PROJECT_TITLE) as total_PROJECT_TITLE,
  max(PROJECT_TITLE) as PROJECT_TITLE,

  count(distinct AWARD_STATUS) as total_AWARD_STATUS,
  max(AWARD_STATUS) as AWARD_STATUS,

  count(distinct PI_EMP_NUM) as total_PI_EMP_NUM,
  max(PI_EMP_NUM) as PI_EMP_NUM,


FROM oga.ZGMS_PRIM_ALL_V v
group by OGA_AWARD_NUMBER