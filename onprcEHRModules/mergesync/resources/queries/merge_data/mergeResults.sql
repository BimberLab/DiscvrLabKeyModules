select
r.RE_FINAL as isFinal,
t.TS_Stat as status,

pr.PR_FNAME,
pr.PR_LNAME,

o.O_ACCNUM as accession,
p.Pt_Lname as animalId,
o.O_DATE as date,
i.INS_NAME as project,
o.O_COLLDT as datecollected,

--NOTE: THIS MIGHT BE THE BEST PROXY FOR A TIMESTAMP
r.RE_DATE as resultDate,

ti.T_ABBR as servicename_abbr,
ti.T_NAME as servicename,
rs.RT_ABBR as testid_abbr,
rs.RT_RDSCR as testid,

r.re_data as text_result,
r.RE_FLVAL as numeric_result,
r.re_Text as remark,
cm.RSC_COMMENT as runRemark

--one row per batch of orders
FROM Orders o

--many panels could be ordered at a time
--TODO: what about failures and repeats?
LEFT JOIN tests t ON (
	t.TS_ACCNR = o.O_ACCNUM
)

--contains reference info about that panel
LEFT JOIN TESTINFO ti ON (
	ti.T_TSTNUM = t.TS_TNUM
)

--there will usually be many results per panel
LEFT JOIN results r ON (
  r.RE_TIDX = t.TS_INDEX
	--AND r.RE_ACCNR = o.O_ACCNUM
)

--translate test name
LEFT JOIN RSLTTYP rs ON (
	rs.RT_RIDX = r.RE_RIDX
)

--append result comment.  is this guaranteed 1:1 with results??
left join RESULT_COMMENTS cm on (
	cm.RSC_ACCNR = r.RE_ACCNR
	and cm.RSC_RIDX = r.RE_RIDX
	and cm.RSC_COMMENT != '>^'  --this might mean something
)

--there should only be 1 patient per order
LEFT JOIN patients p ON (
	o.O_PTNUM = p.PT_NUM
	--and Isnumeric(p.pt_lname) = 1   ---- Only process numeric last names
)

--performed by
LEFT JOIN PRSNL pr ON (
	o.O_DOCTOR = pr.pr_num
)

--another userid
LEFT JOIN PRSNL pr2 ON (
	o.O_EPRSN = pr.pr_num
)

--there should only be 1 visit per order
LEFT JOIN VISITS v ON (
	o.O_VID = v.V_ID
)

--join to visit/insurance
LEFT JOIN Vis_Ins vi ON (
	vi.VINS_VID = v.V_ID
)

--join to insurance for project
left join INSURANCE i ON (
	i.INS_INDEX = vi.VINS_INS1
)