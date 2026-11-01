package com.sanproject.service_planner.service;

import com.sanproject.service_planner.domain.ServiceBooking;
import com.sanproject.service_planner.repository.ServiceBookingRepository;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Issues invoice numbers like "FV/2026/000042" from a database sequence, so numbers never repeat
 * and stay in order even when two jobs are completed at the same moment.
 */
@Service
public class InvoiceNumberService {

    private final ServiceBookingRepository bookingRepository;

    public InvoiceNumberService(ServiceBookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }

    /** Numbers the booking unless it already has an invoice; the number is never changed later. */
    public void assign(ServiceBooking booking, LocalDateTime issuedAt) {
        if (booking.getInvoiceNumber() != null) {
            return;
        }
        long sequence = bookingRepository.nextInvoiceSequence();
        booking.setInvoiceNumber(String.format("FV/%d/%06d", issuedAt.getYear(), sequence));
        booking.setInvoiceIssuedAt(issuedAt);
    }
}
