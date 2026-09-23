package com.harshboss.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "jobs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Job {
    @Id @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false) private String source;
    @Column(name = "source_id", nullable = false) private String sourceId;
    @Column(nullable = false) private String title;
    @Column private String company;
    @Column private String location;
    @Column(nullable = false, columnDefinition = "TEXT") private String description;
    @Column private String salary;
    @Column(name = "job_type") private String jobType;
    @Column(nullable = false) private boolean remote;
    @Column(name = "apply_url", nullable = false) private String applyUrl;
    @Column(name = "posted_at") private Instant postedAt;
    @Column(name = "fetched_at", nullable = false) private Instant fetchedAt;
}
