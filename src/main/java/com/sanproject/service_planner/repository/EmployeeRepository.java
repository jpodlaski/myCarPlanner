package com.sanproject.service_planner.repository;

import com.sanproject.service_planner.domain.Admin;
import com.sanproject.service_planner.domain.Employee;
import com.sanproject.service_planner.domain.Worker;

import org.springframework.data.jpa.repository.JpaRepository;

// Polymorphic repository over Admin and Worker for authorization lookups.
public interface EmployeeRepository extends JpaRepository<Employee, Long> {
}
