package com.sanproject.service_planner.repository;

import com.sanproject.service_planner.domain.EmployeeRole;
import com.sanproject.service_planner.domain.Worker;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;

public interface WorkerRepository extends JpaRepository<Worker, Long> {

    // Primary lookup for employee login.
    Optional<Worker> findByLoginIgnoreCase(String login);

    @Query("SELECT COUNT(w) FROM Worker w WHERE w.role IN :roles")
    long countByRoleIn(@Param("roles") Collection<EmployeeRole> roles);
}
