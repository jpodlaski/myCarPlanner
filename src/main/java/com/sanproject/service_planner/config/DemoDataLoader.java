package com.sanproject.service_planner.config;

import com.sanproject.service_planner.catalog.ResolvedVehicleConfiguration;
import com.sanproject.service_planner.catalog.VehicleCatalogService;
import com.sanproject.service_planner.domain.BookingStatus;
import com.sanproject.service_planner.domain.CancelledBy;
import com.sanproject.service_planner.domain.Customer;
import com.sanproject.service_planner.domain.EmployeeRole;
import com.sanproject.service_planner.domain.ServiceBooking;
import com.sanproject.service_planner.domain.Vehicle;
import com.sanproject.service_planner.domain.Worker;
import com.sanproject.service_planner.repository.CustomerRepository;
import com.sanproject.service_planner.repository.ServiceBookingRepository;
import com.sanproject.service_planner.repository.VehicleRepository;
import com.sanproject.service_planner.repository.WorkerRepository;
import com.sanproject.service_planner.security.PasswordService;
import com.sanproject.service_planner.service.InvoiceNumberService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Fills an empty database with workshop staff, two verified clients, their cars and one booking
 * in every status, so a demo can start on any screen. Rows are saved directly, so no emails are sent.
 * Runs only while there are no workers and no customers; disable with APP_DEMO_DATA=false.
 */
@Component
@ConditionalOnProperty(name = "app.demo-data.enabled", havingValue = "true")
public class DemoDataLoader implements CommandLineRunner {

    public static final String DEMO_PASSWORD = "secret123";

    private static final Logger log = LoggerFactory.getLogger(DemoDataLoader.class);

    private final WorkerRepository workerRepository;
    private final CustomerRepository customerRepository;
    private final VehicleRepository vehicleRepository;
    private final ServiceBookingRepository bookingRepository;
    private final VehicleCatalogService catalogService;
    private final PasswordService passwordService;
    private final InvoiceNumberService invoiceNumberService;

    public DemoDataLoader(WorkerRepository workerRepository,
                          CustomerRepository customerRepository,
                          VehicleRepository vehicleRepository,
                          ServiceBookingRepository bookingRepository,
                          VehicleCatalogService catalogService,
                          PasswordService passwordService,
                          InvoiceNumberService invoiceNumberService) {
        this.workerRepository = workerRepository;
        this.customerRepository = customerRepository;
        this.vehicleRepository = vehicleRepository;
        this.bookingRepository = bookingRepository;
        this.catalogService = catalogService;
        this.passwordService = passwordService;
        this.invoiceNumberService = invoiceNumberService;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (workerRepository.count() > 0 || customerRepository.count() > 0) {
            return;
        }

        String passwordHash = passwordService.hash(DEMO_PASSWORD);

        worker("Marta", "Zielińska", "consultant1", EmployeeRole.CLIENT_SERVICE_CONSULTANT, passwordHash);
        Worker mechanic = worker("Piotr", "Wójcik", "mechanic1", EmployeeRole.MECHANIC, passwordHash);
        Worker apprentice = worker("Kacper", "Lis", "apprentice1", EmployeeRole.APPRENTICE_MECHANIC, passwordHash);
        worker("Ewa", "Kamińska", "ceo", EmployeeRole.CEO, passwordHash);

        Customer anna = customer("Anna", "Nowak", "anna.nowak@example.com", passwordHash);
        Customer jan = customer("Jan", "Kowalski", "jan.kowalski@example.com", passwordHash);

        Vehicle annaThunder = vehicle(anna, "thunder-supercar-6.2-na-v10-8a", 2025, "HSYTHN25V10000101");
        Vehicle annaBastion = vehicle(anna, "bastion-suv-5.0-na-v8-7a", 2023, "HSYBST23V80000102");
        Vehicle janZefir = vehicle(jan, "zefir-roadster-5.0-na-v8-7a", 2024, "HSYZFR24V80000103");

        LocalDate today = LocalDate.now();

        ServiceBooking waiting = booking(annaBastion, BookingStatus.SCHEDULED,
                "Check engine light came on after a long motorway drive.");
        waiting.setEstimatedDropOffTime(today.plusDays(2).atTime(9, 0));
        waiting.setAvailabilityNotes("Mornings work best for me.");

        ServiceBooking confirmed = booking(janZefir, BookingStatus.READY_FOR_WORK,
                "Soft top makes a creaking noise when it opens.");
        confirmed.setEstimatedDropOffTime(today.plusDays(1).atTime(10, 0));
        confirmed.setScheduledDateTime(today.plusDays(1).atTime(10, 0));

        ServiceBooking inProgress = booking(annaThunder, BookingStatus.IN_PROGRESS,
                "Brakes squeal when cold.");
        inProgress.setEstimatedDropOffTime(today.atTime(8, 0));
        inProgress.setScheduledDateTime(today.atTime(8, 0));
        inProgress.setAssignedWorker(mechanic);
        inProgress.setServiceTypes(List.of("Brake inspection", "Brake pad replacement"));
        inProgress.setEstimatedCost(new BigDecimal("1200.00"));

        ServiceBooking completed = booking(annaThunder, BookingStatus.COMPLETED,
                "Annual service.");
        completed.setEstimatedDropOffTime(today.minusDays(14).atTime(7, 0));
        completed.setScheduledDateTime(today.minusDays(14).atTime(7, 0));
        completed.setAssignedWorker(apprentice);
        completed.setServiceTypes(List.of("Oil and filter service", "Diagnostic scan"));
        completed.setEstimatedCost(new BigDecimal("950.00"));
        completed.setFinalCost(new BigDecimal("1375.50"));
        invoiceNumberService.assign(completed, today.minusDays(14).atTime(15, 30));

        ServiceBooking rejected = booking(janZefir, BookingStatus.CANCELLED,
                "Detailing before a car show.");
        rejected.setEstimatedDropOffTime(today.minusDays(7).atTime(12, 0));
        rejected.setCancelledBy(CancelledBy.WORKSHOP);
        rejected.setCancellationReason("We don't offer detailing; please contact a detailing partner.");

        ServiceBooking cancelled = booking(annaBastion, BookingStatus.CANCELLED,
                "Tyre change to winter set.");
        cancelled.setEstimatedDropOffTime(today.minusDays(3).atTime(16, 0));
        cancelled.setCancelledBy(CancelledBy.CUSTOMER);
        cancelled.setCancellationReason("Changed the tyres myself.");

        bookingRepository.saveAll(List.of(waiting, confirmed, inProgress, completed, rejected, cancelled));
        log.info("Demo data loaded: staff and clients use the password '{}'", DEMO_PASSWORD);
    }

    private Worker worker(String firstName, String lastName, String login, EmployeeRole role, String passwordHash) {
        Worker worker = new Worker();
        worker.setFirstName(firstName);
        worker.setLastName(lastName);
        worker.setEmail(login + "@mycarplanner.local");
        worker.setLogin(login);
        worker.setPasswordHash(passwordHash);
        worker.setRole(role);
        return workerRepository.save(worker);
    }

    private Customer customer(String firstName, String lastName, String email, String passwordHash) {
        Customer customer = new Customer();
        customer.setFirstName(firstName);
        customer.setLastName(lastName);
        customer.setEmail(email);
        customer.setPasswordHash(passwordHash);
        customer.setEmailVerified(true);
        return customerRepository.save(customer);
    }

    private Vehicle vehicle(Customer owner, String configurationId, int productionYear, String vin) {
        ResolvedVehicleConfiguration configuration = catalogService.requireConfiguration(configurationId);
        catalogService.validateProductionYear(configuration.getProductionEra(), productionYear);

        Vehicle vehicle = new Vehicle();
        vehicle.setVin(vin);
        vehicle.setConfigurationId(configuration.getConfigurationId());
        vehicle.setModelLine(configuration.getModelLine());
        vehicle.setModel(configuration.getModel());
        vehicle.setProductionEra(configuration.getProductionEra());
        vehicle.setProductionYear(productionYear);
        vehicle.setBodyStyle(configuration.getBodyStyle());
        vehicle.setEngine(configuration.getEngine());
        vehicle.setPower(configuration.getPower());
        vehicle.setTransmission(configuration.getTransmission());
        vehicle.setDrivetrain(configuration.getDrivetrain());
        vehicle.setCustomer(owner);
        return vehicleRepository.save(vehicle);
    }

    private ServiceBooking booking(Vehicle vehicle, BookingStatus status, String description) {
        Customer owner = vehicle.getCustomer();
        ServiceBooking booking = new ServiceBooking();
        booking.setVehicle(vehicle);
        booking.setCustomerName(owner.getFirstName() + " " + owner.getLastName());
        booking.setCarModel(vehicle.getModel());
        booking.setCustomerDescription(description);
        booking.setStatus(status);
        return booking;
    }
}
