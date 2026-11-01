package com.sanproject.service_planner.domain;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

/** Back-office account; shares the employee login table with Worker. */
@Entity
@DiscriminatorValue("ADMIN")
public class Admin extends Employee {

    public Admin() {
    }
}
