ALTER TABLE extscheduler.events DROP CONSTRAINT UQ_events_ContainerResourceName;
ALTER TABLE extscheduler.events ADD CONSTRAINT CHK_event_DateRangeValid CHECK (StartDate <= EndDate);

GO

CREATE TRIGGER TR_OverlappingDateRanges ON extscheduler.events FOR INSERT, UPDATE AS
BEGIN
    IF EXISTS(
        SELECT 1 FROM extscheduler.events R
        INNER JOIN inserted I
        ON (
            (R.Container = I.Container AND R.ResourceId = I.ResourceId AND R.StartDate < I.EndDate AND I.StartDate < R.EndDate)
            AND NOT (R.Container = I.Container AND R.ResourceId = I.ResourceId AND R.StartDate = I.StartDate AND I.EndDate = R.EndDate)
        )
    )
    BEGIN
        RAISERROR('Start/End date ranges may not overlap for the same resource.', 16, 1)
        ROLLBACK
    END
END