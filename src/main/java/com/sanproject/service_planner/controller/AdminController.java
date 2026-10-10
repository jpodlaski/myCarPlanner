package com.sanproject.service_planner.controller;

import com.sanproject.service_planner.domain.Admin;
import com.sanproject.service_planner.domain.Worker;
import com.sanproject.service_planner.dto.AdminBookingResponse;
import com.sanproject.service_planner.dto.CreateAdminRequest;
import com.sanproject.service_planner.dto.CreateWorkerRequest;
import com.sanproject.service_planner.dto.UpdateWorkerRoleRequest;
import com.sanproject.service_planner.security.AuthSupport;
import com.sanproject.service_planner.service.AdminService;
import com.sanproject.service_planner.service.BookingService;
import com.sanproject.service_planner.service.WorkerService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Management API: workers CRUD and booking history; authorization from JWT.
@RestController
@RequestMapping("/admins")
public class AdminController {

    private final AdminService adminService;
    private final WorkerService workerService;
    private final BookingService bookingService;
    private final AuthSupport authSupport;

    public AdminController(
            AdminService adminService,
            WorkerService workerService,
            BookingService bookingService,
            AuthSupport authSupport) {
        this.adminService = adminService;
        this.workerService = workerService;
        this.bookingService = bookingService;
        this.authSupport = authSupport;
    }

    @GetMapping
    public List<Admin> getAllAdmins() {
        authSupport.requireCanManageWorkers();
        return adminService.getAllAdmins();
    }

    @GetMapping("/workers")
    public List<Worker> getWorkers() {
        authSupport.requireCanManageWorkers();
        return workerService.getAllWorkers();
    }

    @GetMapping("/bookings")
    public List<AdminBookingResponse> getBookings() {
        authSupport.requireCanManageWorkers();
        return bookingService.getAdminBookings();
    }

    @PostMapping("/workers")
    public ResponseEntity<Worker> createWorker(@Valid @RequestBody CreateWorkerRequest request) {
        authSupport.requireCanManageWorkers();
        Worker createdWorker = workerService.createWorker(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdWorker);
    }

    @PatchMapping("/workers/{id}/role")
    public ResponseEntity<Worker> updateWorkerRole(
            @PathVariable Long id,
            @Valid @RequestBody UpdateWorkerRoleRequest request) {
        authSupport.requireCanManageWorkers();
        Worker updatedWorker = workerService.updateWorkerRole(id, request);
        return ResponseEntity.ok(updatedWorker);
    }

    @DeleteMapping("/workers/{id}")
    public ResponseEntity<Void> deleteWorker(@PathVariable Long id) {
        authSupport.requireCanManageWorkers();
        workerService.deleteWorker(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Admin> getAdminById(@PathVariable Long id) {
        authSupport.requireCanManageWorkers();
        Admin admin = adminService.getAdminById(id);

        if (admin == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(admin);
    }

    @PostMapping
    public ResponseEntity<Admin> createAdmin(@Valid @RequestBody CreateAdminRequest request) {
        authSupport.requireCanManageWorkers();
        Admin createdAdmin = adminService.createAdmin(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdAdmin);
    }
}
