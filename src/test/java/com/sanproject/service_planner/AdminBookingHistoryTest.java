package com.sanproject.service_planner;

import com.sanproject.service_planner.domain.Admin;
import com.sanproject.service_planner.domain.BookingStatus;
import com.sanproject.service_planner.domain.Customer;
import com.sanproject.service_planner.domain.EmployeeRole;
import com.sanproject.service_planner.domain.ServiceBooking;
import com.sanproject.service_planner.domain.Vehicle;
import com.sanproject.service_planner.repository.AdminRepository;
import com.sanproject.service_planner.repository.CustomerRepository;
import com.sanproject.service_planner.repository.ServiceBookingRepository;
import com.sanproject.service_planner.repository.VehicleRepository;
import com.sanproject.service_planner.security.JwtService;
import com.sanproject.service_planner.security.PasswordService;
import com.sanproject.service_planner.service.BookingNotificationService;
import com.sanproject.service_planner.service.CustomerNotificationService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Management booking history is paged and searched on the server. Other tests commit bookings to
 * the same database, so these match on a customer name no other test uses.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminBookingHistoryTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordService passwordService;

    @Autowired
    private AdminRepository adminRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private ServiceBookingRepository bookingRepository;

    @MockitoBean
    private BookingNotificationService bookingNotificationService;

    @MockitoBean
    private CustomerNotificationService customerNotificationService;

    private String adminToken;
    private String clientToken;
    private final List<Long> bookingIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        Admin admin = adminRepository.findByLoginIgnoreCase("admin").orElseGet(() -> {
            Admin seeded = new Admin();
            seeded.setFirstName("Admin");
            seeded.setLastName("User");
            seeded.setEmail("admin@mycarplanner.local");
            seeded.setLogin("admin");
            seeded.setPasswordHash(passwordService.hash("admin"));
            seeded.setRole(EmployeeRole.ADMIN);
            return adminRepository.save(seeded);
        });
        adminToken = jwtService.createToken(admin.getId(), "ADMIN", "Admin User");

        Customer customer = new Customer();
        customer.setFirstName("Paginia");
        customer.setLastName("Testova");
        customer.setEmail("paginia.testova@example.com");
        customer.setPasswordHash(passwordService.hash("secret123"));
        customer.setEmailVerified(true);
        customer = customerRepository.save(customer);
        clientToken = jwtService.createToken(customer.getId(), "CLIENT", "Paginia Testova");

        Vehicle vehicle = new Vehicle();
        vehicle.setVin("HSYPAGETEST000001");
        vehicle.setConfigurationId("zefir-roadster-5.0-na-v8-7a");
        vehicle.setModelLine("Zefir");
        vehicle.setModel("Zefir V8");
        vehicle.setProductionEra("2024-present");
        vehicle.setProductionYear(2024);
        vehicle.setCustomer(customer);
        vehicle = vehicleRepository.save(vehicle);

        for (BookingStatus status : List.of(
                BookingStatus.SCHEDULED, BookingStatus.READY_FOR_WORK, BookingStatus.COMPLETED)) {
            ServiceBooking booking = new ServiceBooking();
            booking.setVehicle(vehicle);
            booking.setCustomerName("Paginia Testova");
            booking.setCarModel("Zefir V8");
            booking.setCustomerDescription("Pagination check");
            booking.setStatus(status);
            bookingIds.add(bookingRepository.save(booking).getId());
        }
    }

    @Test
    void searchResultsArePagedNewestFirst() throws Exception {
        mockMvc.perform(get("/admins/bookings")
                        .param("q", "paginia")
                        .param("size", "2")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].id").value(bookingIds.get(2)))
                .andExpect(jsonPath("$.items[1].id").value(bookingIds.get(1)));

        mockMvc.perform(get("/admins/bookings")
                        .param("q", "PAGINIA")
                        .param("size", "2")
                        .param("page", "1")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id").value(bookingIds.get(0)));
    }

    @Test
    void searchMatchesBookingNumberVinAndStatus() throws Exception {
        mockMvc.perform(get("/admins/bookings")
                        .param("q", "#" + bookingIds.get(0))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id", hasItem(bookingIds.get(0).intValue())));

        mockMvc.perform(get("/admins/bookings")
                        .param("q", "hsypagetest")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(3));

        mockMvc.perform(get("/admins/bookings")
                        .param("q", "ready for work")
                        .param("size", "100")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].status", everyItem(is("READY_FOR_WORK"))))
                .andExpect(jsonPath("$.items[*].id", hasItem(bookingIds.get(1).intValue())));
    }

    @Test
    void clientsCannotReadBookingHistory() throws Exception {
        mockMvc.perform(get("/admins/bookings")
                        .header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isForbidden());
    }
}
