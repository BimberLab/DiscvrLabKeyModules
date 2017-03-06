SELECT
i.date,
CAST(i.date as date) as dateOnly,
cast(dayofyear(i.date) as integer) as DayOfYear,
cast(dayofmonth(i.date) as integer) as DayOfMonth,
cast(dayofweek(i.date) as integer) as DayOfWeek,
ceiling(cast(dayofmonth(i.date) as float) / 7.0) as WeekOfMonth,
cast(week(i.date) as integer) as WeekOfYear,
Cast(month(curdate()) as integer) as currentMonth,
cast(year(curDate()) as integer) as currentYear

FROM (SELECT

timestampadd('SQL_TSI_DAY', i.value, CAST(COALESCE(TIMESTAMPAdd('SQL_TSI_DAY', -30, curdate()), curdate()) AS TIMESTAMP)) as date

FROM Site.{substitutePath moduleProperty('EHR','EHRStudyContainer')}.ldk.integers i

) i

WHERE i.date <=curDate()

