package ua.edu.nupp.kpp.waste.server.model;

import java.math.BigDecimal;

public record Request(Long id, Long siteId, String title, BigDecimal fillLevel, BigDecimal sanitaryRisk,
                      Integer overdueHours, BigDecimal priority, RequestStatus status) { }
