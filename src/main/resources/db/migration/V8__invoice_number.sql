-- Invoice numbers are issued in order from one sequence when a booking is completed,
-- and the issue time is stored so a re-downloaded invoice keeps its original date.
CREATE SEQUENCE invoice_number_seq START WITH 1 INCREMENT BY 1;

ALTER TABLE service_booking
    ADD COLUMN invoice_number    VARCHAR(32),
    ADD COLUMN invoice_issued_at TIMESTAMP;

ALTER TABLE service_booking
    ADD CONSTRAINT service_booking_invoice_number_key UNIQUE (invoice_number);

-- Bookings completed before this migration get numbers in booking order. Their completion
-- time was never recorded, so the appointment time (or now) stands in as the issue date.
-- Status 2 is BookingStatus.COMPLETED (ordinal).
UPDATE service_booking b
SET invoice_issued_at = COALESCE(b.scheduled_date_time, now()),
    invoice_number    = 'FV/' || EXTRACT(YEAR FROM COALESCE(b.scheduled_date_time, now()))::int
                        || '/' || LPAD(numbered.seq::text, 6, '0')
FROM (SELECT id, nextval('invoice_number_seq') AS seq
      FROM (SELECT id FROM service_booking WHERE status = 2 ORDER BY id) ordered) numbered
WHERE b.id = numbered.id;
