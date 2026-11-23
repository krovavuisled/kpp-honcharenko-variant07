package ua.edu.nupp.kpp.waste.server.model;

public record Trip(Long id, Long requestId, Long vehicleId, String scheduledDate, String notes) { }
