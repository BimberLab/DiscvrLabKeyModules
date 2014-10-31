SELECT
alias,
count(*) as total

FROM onprc_billing.aliases
GROUP BY alias
HAVING COUNT(*) > 1