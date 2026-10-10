package com.sanproject.service_planner.domain;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

// Back-office account; uses the shared Employee login table with discriminator ADMIN.
@Entity
@DiscriminatorValue("ADMIN")
public class Admin extends Employee {

    public Admin() {
    }
}
