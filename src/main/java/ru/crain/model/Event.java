package ru.crain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "events")
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "upload_id", nullable = false)
    private Upload upload;

    @Column(name = "event_time", nullable = false)
    private LocalDateTime eventTime;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private String severity;

    protected Event() {
    }

    public Event(Upload upload, LocalDateTime eventTime, String eventType, String description, String severity) {
        this.upload = upload;
        this.eventTime = eventTime;
        this.eventType = eventType;
        this.description = description;
        this.severity = severity;
    }

    public Long getId() {
        return id;
    }

    public Upload getUpload() {
        return upload;
    }

    public LocalDateTime getEventTime() {
        return eventTime;
    }

    public String getEventType() {
        return eventType;
    }

    public String getDescription() {
        return description;
    }

    public String getSeverity() {
        return severity;
    }
}
