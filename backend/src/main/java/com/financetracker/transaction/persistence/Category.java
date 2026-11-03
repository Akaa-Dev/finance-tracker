package com.financetracker.transaction.persistence;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
@Entity @Table(name="categories")
public class Category { @Id public UUID id; public UUID userId; public String name; public String kind; public Instant createdAt; }
