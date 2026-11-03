package com.sanproject.service_planner;

import com.sanproject.service_planner.domain.Customer;
import com.sanproject.service_planner.domain.EmployeeRole;
import com.sanproject.service_planner.domain.Vehicle;
import com.sanproject.service_planner.domain.Worker;
import com.sanproject.service_planner.repository.CustomerRepository;
import com.sanproject.service_planner.repository.ServiceBookingRepository;
import com.sanproject.service_planner.repository.VehicleRepository;
import com.sanproject.service_planner.repository.WorkerRepository;
import com.sanproject.service_planner.security.JwtService;
import com.sanproject.service_planner.security.PasswordService;
import com.sanproject.service_planner.service.BookingNotificationService;
import com.sanproject.service_planner.service.CustomerNotificationService;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;

/**
 * End-to-end HTTP test of the booking state machine:
 * create → consultant accept → technician claim → complete
 * (plus reject/cancel paths in other methods).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class BookingLifecycleTest {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private ServiceBookingRepository bookingRepository;

    @Autowired
    private WorkerRepository workerRepository;

    @Autowired
    private PasswordService passwordService;

    @MockitoBean
    private BookingNotificationService bookingNotificationService;

    @MockitoBean
    private CustomerNotificationService customerNotificationService;

    private Customer customer;
    private Vehicle vehicle;
    private Worker consultant;
    private Worker worker;
    private Worker otherWorker;

    @BeforeEach
    void setUp() {
        customer = new Customer();
        customer.setFirstName("Casey");
        customer.setLastName("Client");
        customer.setEmail("casey@example.com");
        customer.setPasswordHash(passwordService.hash("secret"));
        customer.setEmailVerified(true);
        customer = customerRepository.save(customer);

        vehicle = new Vehicle();
        vehicle.setVin("VINCASEY000000001");
        vehicle.setConfigurationId("thunder-supercar-5.0-na-v8-7a");
        vehicle.setModelLine("Thunder");
        vehicle.setModel("Thunder V8");
        vehicle.setProductionEra("2024-present");
        vehicle.setProductionYear(2024);
        vehicle.setBodyStyle("Supercar");
        vehicle.setEngine("5.0L Naturally Aspirated V8");
        vehicle.setPower("560 PS");
        vehicle.setTransmission("7-speed Automatic");
        vehicle.setDrivetrain("RWD");
        vehicle.setCustomer(customer);
        vehicle = vehicleRepository.save(vehicle);

        consultant = new Worker();
        consultant.setFirstName("Chris");
        consultant.setLastName("Consultant");
        consultant.setEmail("chris@mycarplanner.local");
        consultant.setLogin("chris");
        consultant.setPasswordHash(passwordService.hash("secret"));
        consultant.setRole(EmployeeRole.CLIENT_SERVICE_CONSULTANT);
        consultant = workerRepository.save(consultant);

        worker = new Worker();
        worker.setFirstName("Morgan");
        worker.setLastName("Mechanic");
        worker.setEmail("morgan@mycarplanner.local");
        worker.setLogin("morgan");
        worker.setPasswordHash(passwordService.hash("secret"));
        worker.setRole(EmployeeRole.MECHANIC);
        worker = workerRepository.save(worker);

        otherWorker = new Worker();
        otherWorker.setFirstName("Riley");
        otherWorker.setLastName("Mechanic");
        otherWorker.setEmail("riley@mycarplanner.local");
        otherWorker.setLogin("riley");
        otherWorker.setPasswordHash(passwordService.hash("secret"));
        otherWorker.setRole(EmployeeRole.MECHANIC);
        otherWorker = workerRepository.save(otherWorker);
    }

    @Test
    void happyPath_createAcceptClaimComplete() throws Exception {
        long bookingId = createBookingAsClient();

        mockMvc.perform(get("/bookings/available").header("Authorization", consultantAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem((int) bookingId)));

        mockMvc.perform(get("/bookings/available").header("Authorization", workerAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + bookingId + ")]").isEmpty());

        String appointment = appointmentInTwoDays();
        mockMvc.perform(post("/bookings/{id}/accept", bookingId)
                        .header("Authorization", consultantAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledDateTime\":\"" + appointment + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY_FOR_WORK"))
                .andExpect(jsonPath("$.scheduledDateTime").value(appointment))
                .andExpect(jsonPath("$.assignedWorker").value(nullValue()));

        mockMvc.perform(get("/bookings/available").header("Authorization", workerAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem((int) bookingId)));

        mockMvc.perform(post("/bookings/{id}/claim", bookingId)
                        .header("Authorization", workerAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "estimatedCost": 350.00,
                                  "serviceTypes": ["Brake inspection", "Oil change"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.assignedWorker.id").value(worker.getId().intValue()))
                .andExpect(jsonPath("$.serviceTypes", hasSize(2)))
                .andExpect(jsonPath("$.estimatedCost").value(350.00));

        mockMvc.perform(post("/bookings/{id}/complete", bookingId)
                        .header("Authorization", workerAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"finalCost\": 375.50}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.finalCost").value(375.50));

        mockMvc.perform(get("/customers/me/bookings").header("Authorization", clientAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + bookingId + ")].status").value(hasItem("COMPLETED")));

        var completed = bookingRepository.findById(bookingId).orElseThrow();
        assertThat(completed.getInvoiceNumber()).matches("FV/\\d{4}/\\d{6}");
        assertThat(completed.getInvoiceIssuedAt()).isNotNull();

        byte[] pdf = mockMvc.perform(get("/bookings/{id}/invoice", bookingId)
                        .header("Authorization", clientAuth()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        String text;
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            text = new PDFTextStripper().getText(doc);
        }
        assertThat(text)
                .contains(completed.getInvoiceNumber())
                .contains("NIP 123-456-32-18")
                .contains("305.28 EUR")
                .contains("70.22 EUR")
                .contains("375.50 EUR");
    }

    @Test
    void consultantCanRejectUnclaimedBooking() throws Exception {
        long bookingId = createBookingAsClient();

        mockMvc.perform(post("/bookings/{id}/reject", bookingId)
                        .header("Authorization", consultantAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Cannot service this model at this site\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelledBy").value("WORKSHOP"))
                .andExpect(jsonPath("$.cancellationReason").value("Cannot service this model at this site"));

        mockMvc.perform(get("/bookings/available").header("Authorization", consultantAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + bookingId + ")]").isEmpty());
    }

    @Test
    void mechanicCannotAcceptOrReject() throws Exception {
        long bookingId = createBookingAsClient();

        String appointment = appointmentInTwoDays();
        mockMvc.perform(post("/bookings/{id}/accept", bookingId)
                        .header("Authorization", workerAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledDateTime\":\"" + appointment + "\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/bookings/{id}/reject", bookingId)
                        .header("Authorization", workerAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Nope\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void customerCanCancelOwnOpenBooking() throws Exception {
        long bookingId = createBookingAsClient();

        mockMvc.perform(post("/bookings/{id}/cancel", bookingId)
                        .header("Authorization", clientAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Plans changed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelledBy").value("CUSTOMER"))
                .andExpect(jsonPath("$.cancellationReason").value("Plans changed"));
    }

    @Test
    void assignedWorkerCanCancelWithReason() throws Exception {
        long bookingId = acceptBooking(createBookingAsClient());

        mockMvc.perform(post("/bookings/{id}/claim", bookingId)
                        .header("Authorization", workerAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceTypes\":[\"Diagnostics\"]}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/bookings/{id}/cancel", bookingId)
                        .header("Authorization", workerAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Parts unavailable\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelledBy").value("WORKER"))
                .andExpect(jsonPath("$.cancellationReason").value("Parts unavailable"));
    }

    @Test
    void otherWorkerCannotCompleteClaimedBooking() throws Exception {
        long bookingId = acceptBooking(createBookingAsClient());

        mockMvc.perform(post("/bookings/{id}/claim", bookingId)
                        .header("Authorization", workerAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceTypes\":[\"Alignment\"]}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/bookings/{id}/complete", bookingId)
                        .header("Authorization", otherWorkerAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"finalCost\": 100}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void cannotClaimAlreadyClaimedBooking() throws Exception {
        long bookingId = acceptBooking(createBookingAsClient());

        mockMvc.perform(post("/bookings/{id}/claim", bookingId)
                        .header("Authorization", workerAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceTypes\":[\"Inspection\"]}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/bookings/{id}/claim", bookingId)
                        .header("Authorization", otherWorkerAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceTypes\":[\"Inspection\"]}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void cannotRejectAcceptedBooking() throws Exception {
        long bookingId = acceptBooking(createBookingAsClient());

        mockMvc.perform(post("/bookings/{id}/reject", bookingId)
                        .header("Authorization", consultantAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Too late\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void clientCannotClaimBooking() throws Exception {
        long bookingId = acceptBooking(createBookingAsClient());

        mockMvc.perform(post("/bookings/{id}/claim", bookingId)
                        .header("Authorization", clientAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceTypes\":[\"Inspection\"]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void createBookingRequiresDropOffTime() throws Exception {
        mockMvc.perform(post("/bookings")
                        .header("Authorization", clientAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "vehicleId": %d,
                                  "customerDescription": "Noise only",
                                  "availabilityNotes": "Weekday mornings"
                                }
                                """.formatted(vehicle.getId())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createBookingRejectsDropOffOutsideWorkshopHours() throws Exception {
        for (String dropOff : new String[] {"2030-06-15T03:00:00", "2030-06-15T05:59:00", "2030-06-15T20:30:00"}) {
            mockMvc.perform(post("/bookings")
                            .header("Authorization", clientAuth())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(bookingRequest(dropOff)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void createBookingAcceptsDropOffAtOpeningAndClosingTime() throws Exception {
        for (String dropOff : new String[] {"2030-06-15T06:00:00", "2030-06-15T20:00:00"}) {
            mockMvc.perform(post("/bookings")
                            .header("Authorization", clientAuth())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(bookingRequest(dropOff)))
                    .andExpect(status().isCreated());
        }
    }

    @Test
    void consultantCannotScheduleOutsideWorkshopHours() throws Exception {
        long bookingId = createBookingAsClient();
        String lateEvening = LocalDateTime.now().plusDays(2)
                .withHour(21).withMinute(0).withSecond(0).withNano(0).format(ISO);

        mockMvc.perform(post("/bookings/{id}/accept", bookingId)
                        .header("Authorization", consultantAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledDateTime\":\"" + lateEvening + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void workshopCapacityReportsFreeMechanicWithinWorkshopHours() throws Exception {
        mockMvc.perform(get("/workers/me/workshop-capacity")
                        .param("at", appointmentInTwoDays())
                        .header("Authorization", consultantAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.withinWorkshopHours").value(true))
                .andExpect(jsonPath("$.available").value(true));
    }

    @Test
    void workshopCapacityIsUnavailableOutsideWorkshopHours() throws Exception {
        String lateEvening = LocalDateTime.now().plusDays(2)
                .withHour(21).withMinute(0).withSecond(0).withNano(0).format(ISO);

        mockMvc.perform(get("/workers/me/workshop-capacity")
                        .param("at", lateEvening)
                        .header("Authorization", consultantAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.withinWorkshopHours").value(false))
                .andExpect(jsonPath("$.available").value(false));
    }

    private String bookingRequest(String dropOff) {
        return """
                {
                  "vehicleId": %d,
                  "customerDescription": "Strange noise when braking",
                  "estimatedDropOffTime": "%s",
                  "availabilityNotes": "Weekday mornings"
                }
                """.formatted(vehicle.getId(), dropOff);
    }

    private static String appointmentInTwoDays() {
        return LocalDateTime.now().plusDays(2)
                .withHour(10).withMinute(30).withSecond(15).withNano(0).format(ISO);
    }

    private long createBookingAsClient() throws Exception {
        MvcResult result = mockMvc.perform(post("/bookings")
                        .header("Authorization", clientAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "vehicleId": %d,
                                  "customerDescription": "Strange noise when braking",
                                  "estimatedDropOffTime": "2030-06-15T10:30:00",
                                  "availabilityNotes": "Weekday mornings"
                                }
                                """.formatted(vehicle.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.assignedWorker").value(nullValue()))
                .andReturn();

        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private long acceptBooking(long bookingId) throws Exception {
        String appointment = appointmentInTwoDays();
        mockMvc.perform(post("/bookings/{id}/accept", bookingId)
                        .header("Authorization", consultantAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledDateTime\":\"" + appointment + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY_FOR_WORK"));
        return bookingId;
    }

    private String clientAuth() {
        return bearer(jwtService.createToken(
                customer.getId(),
                "CLIENT",
                customer.getFirstName() + " " + customer.getLastName()));
    }

    private String consultantAuth() {
        return bearer(jwtService.createToken(
                consultant.getId(),
                consultant.getRole().name(),
                consultant.getFirstName() + " " + consultant.getLastName()));
    }

    private String workerAuth() {
        return bearer(jwtService.createToken(
                worker.getId(),
                worker.getRole().name(),
                worker.getFirstName() + " " + worker.getLastName()));
    }

    private String otherWorkerAuth() {
        return bearer(jwtService.createToken(
                otherWorker.getId(),
                otherWorker.getRole().name(),
                otherWorker.getFirstName() + " " + otherWorker.getLastName()));
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
