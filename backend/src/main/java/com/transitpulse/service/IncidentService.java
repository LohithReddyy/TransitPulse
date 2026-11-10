package com.transitpulse.service;

import com.transitpulse.dto.IncidentRequest;
import com.transitpulse.entity.Incident;
import com.transitpulse.exception.InvalidRequestException;
import com.transitpulse.exception.ResourceNotFoundException;
import com.transitpulse.repository.IncidentRepository;
import com.transitpulse.repository.RouteRepository;
import com.transitpulse.repository.StopRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** Create, update and query operator disruption annotations. */
@Service
@Transactional(readOnly = true)
public class IncidentService {

    private final IncidentRepository incidentRepository;
    private final RouteRepository routeRepository;
    private final StopRepository stopRepository;

    public IncidentService(IncidentRepository incidentRepository,
                           RouteRepository routeRepository,
                           StopRepository stopRepository) {
        this.incidentRepository = incidentRepository;
        this.routeRepository = routeRepository;
        this.stopRepository = stopRepository;
    }

    public List<Incident> findAll() {
        return incidentRepository.findAll();
    }

    public Incident findById(String id) {
        return incidentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Incident", id));
    }

    public List<Incident> findByRoute(String routeId) {
        return incidentRepository.findByRouteIdOrderByStartTimeDesc(routeId);
    }

    public List<Incident> findByStatus(String status) {
        return incidentRepository.findByStatus(status);
    }

    public List<Incident> findDiversions() {
        return incidentRepository.findByIsDiversion("Y");
    }

    @Transactional
    public Incident create(IncidentRequest request) {
        validate(request);
        Incident incident = new Incident();
        incident.setIncidentId(nextId());
        apply(incident, request);
        return incidentRepository.save(incident);
    }

    @Transactional
    public Incident update(String id, IncidentRequest request) {
        Incident incident = findById(id);
        validate(request);
        apply(incident, request);
        return incidentRepository.save(incident);
    }

    @Transactional
    public void delete(String id) {
        Incident incident = findById(id);
        incidentRepository.delete(incident);
    }

    private void apply(Incident incident, IncidentRequest request) {
        incident.setIncidentType(request.getIncidentType());
        incident.setRouteId(request.getRouteId());
        incident.setStopId(blankToNull(request.getStopId()));
        incident.setStartTime(request.parsedStartTime());
        incident.setEndTime(request.parsedEndTime());
        incident.setSeverity(request.getSeverity());
        incident.setDescription(request.getDescription());
        incident.setIsDiversion(request.getIsDiversion());
        incident.setStatus(request.getStatus());
        incident.setReportedBy(request.getReportedBy() == null
                ? "operations" : request.getReportedBy());
    }

    private void validate(IncidentRequest request) {
        if (!routeRepository.existsById(request.getRouteId())) {
            throw new InvalidRequestException(
                    "Unknown routeId: " + request.getRouteId());
        }
        String stopId = blankToNull(request.getStopId());
        if (stopId != null && !stopRepository.existsById(stopId)) {
            throw new InvalidRequestException("Unknown stopId: " + stopId);
        }
        LocalDateTime start = request.parsedStartTime();
        LocalDateTime end = request.parsedEndTime();
        if (end != null && end.isBefore(start)) {
            throw new InvalidRequestException(
                    "endTime (" + end + ") cannot be before startTime (" + start + ")");
        }
        // A diversion needs to say which stop was skipped, otherwise the
        // schedule comparison cannot be corrected for it.
        if ("Y".equals(request.getIsDiversion())
                && blankToNull(request.getSkippedStopId()) == null
                && stopId == null) {
            throw new InvalidRequestException(
                    "A diversion incident must identify the affected stop: set "
                            + "skippedStopId or stopId so stop-to-stop comparisons "
                            + "can be excluded for it.");
        }
    }

    /** Next sequential id of the form INCnnn. */
    private String nextId() {
        int max = 0;
        for (Incident existing : incidentRepository.findAll()) {
            String id = existing.getIncidentId();
            if (id != null && id.startsWith("INC")) {
                try {
                    max = Math.max(max, Integer.parseInt(id.substring(3)));
                } catch (NumberFormatException ignored) {
                    // non-conforming id: skip it rather than fail the create
                }
            }
        }
        return String.format("INC%03d", max + 1);
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
