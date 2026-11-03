package com.sanproject.service_planner.email;

import com.sanproject.service_planner.domain.BookingStatus;
import com.sanproject.service_planner.domain.Customer;
import com.sanproject.service_planner.domain.ServiceBooking;
import com.sanproject.service_planner.domain.Vehicle;
import com.sanproject.service_planner.repository.ServiceBookingRepository;
import com.sanproject.service_planner.service.InvoicePdfService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The completion email must only claim an attached invoice when the PDF was actually generated. */
class BookingNotificationDeliveryTest {

    private final ServiceBookingRepository bookingRepository = mock(ServiceBookingRepository.class);
    private final EmailRendererClient emailRendererClient = mock(EmailRendererClient.class);
    private final MailService mailService = mock(MailService.class);
    private final InvoicePdfService invoicePdfService = mock(InvoicePdfService.class);

    private final BookingNotificationDelivery delivery = new BookingNotificationDelivery(
            bookingRepository, emailRendererClient, mailService, invoicePdfService);

    private final RenderedEmail rendered = new RenderedEmail();

    @BeforeEach
    void setUp() {
        Customer customer = new Customer();
        customer.setFirstName("Pat");
        customer.setLastName("Client");
        customer.setEmail("pat@example.com");

        Vehicle vehicle = new Vehicle();
        vehicle.setModelLine("Thunder");
        vehicle.setCustomer(customer);

        ServiceBooking booking = new ServiceBooking();
        booking.setId(7L);
        booking.setVehicle(vehicle);
        booking.setStatus(BookingStatus.COMPLETED);

        when(bookingRepository.findByIdWithDetails(7L)).thenReturn(Optional.of(booking));
        when(emailRendererClient.render(any())).thenReturn(Optional.of(rendered));
    }

    @Test
    void completionEmailSaysInvoiceIsAttachedWhenPdfIsGenerated() {
        byte[] pdf = {1, 2, 3};
        when(invoicePdfService.createInvoicePdf(any(), any())).thenReturn(pdf);

        delivery.deliver(7L, "booking_completed", BookingStatus.IN_PROGRESS);

        assertThat(renderedPayload().getInvoiceAttached()).isTrue();
        verify(mailService).send("pat@example.com", rendered, pdf, "invoice-booking-7.pdf");
    }

    @Test
    void completionEmailDoesNotClaimAnAttachmentWhenPdfFails() {
        when(invoicePdfService.createInvoicePdf(any(), any())).thenThrow(new IllegalStateException("font missing"));

        delivery.deliver(7L, "booking_completed", BookingStatus.IN_PROGRESS);

        assertThat(renderedPayload().getInvoiceAttached()).isFalse();
        verify(mailService).send("pat@example.com", rendered);
        verify(mailService, never()).send(anyString(), any(), any(), anyString());
    }

    @Test
    void otherEventsDoNotGenerateAnInvoice() {
        delivery.deliver(7L, "booking_cancelled", BookingStatus.SCHEDULED);

        assertThat(renderedPayload().getInvoiceAttached()).isNull();
        verify(invoicePdfService, never()).createInvoicePdf(any(), any());
        verify(mailService).send(eq("pat@example.com"), eq(rendered));
    }

    private BookingEmailPayload renderedPayload() {
        ArgumentCaptor<BookingEmailPayload> captor = ArgumentCaptor.forClass(BookingEmailPayload.class);
        verify(emailRendererClient).render(captor.capture());
        return captor.getValue();
    }
}
