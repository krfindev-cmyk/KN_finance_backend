package com.fcms.naveen.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** A vegetable/goods supplier that Naveen buys stock from and settles in small part-payments. */
@Entity
@Table(name = "naveen_suppliers")
public class NaveenSupplier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String mobile;

    @Column(length = 1000)
    private String address;

    private LocalDateTime createdAt;

    public NaveenSupplier() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getMobile() { return mobile; }
    public void setMobile(String mobile) { this.mobile = mobile; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
