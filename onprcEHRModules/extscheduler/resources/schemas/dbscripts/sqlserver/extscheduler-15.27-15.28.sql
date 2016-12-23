
ALTER TRIGGER extscheduler.TR_OverlappingDateRanges ON extscheduler.Events FOR INSERT, UPDATE AS
BEGIN
    IF EXISTS(
        SELECT 1 FROM extscheduler.events R
        INNER JOIN inserted I
        ON (
            (R.Container = I.Container AND R.ResourceId = I.ResourceId AND R.StartDate < I.EndDate AND I.StartDate < R.EndDate)
            AND NOT (R.Id = I.Id)
        )
    )
    BEGIN
        RAISERROR('Start/End date ranges may not overlap for the same resource.', 16, 1)
        ROLLBACK
    END
END