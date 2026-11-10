package com.transitpulse.service;

import com.transitpulse.entity.ActualMovement;
import com.transitpulse.entity.Incident;
import com.transitpulse.entity.PassengerVolume;
import com.transitpulse.entity.Route;
import com.transitpulse.entity.RouteStop;
import com.transitpulse.entity.ScheduledMovement;
import com.transitpulse.entity.Stop;
import com.transitpulse.entity.SystemConfiguration;
import com.transitpulse.entity.TransferConnection;
import com.transitpulse.entity.Trip;
import com.transitpulse.entity.Vehicle;
import com.transitpulse.repository.ActualMovementRepository;
import com.transitpulse.repository.IncidentRepository;
import com.transitpulse.repository.PassengerVolumeRepository;
import com.transitpulse.repository.RouteRepository;
import com.transitpulse.repository.RouteStopRepository;
import com.transitpulse.repository.ScheduledMovementRepository;
import com.transitpulse.repository.StopRepository;
import com.transitpulse.repository.SystemConfigurationRepository;
import com.transitpulse.repository.TransferConnectionRepository;
import com.transitpulse.repository.TripRepository;
import com.transitpulse.repository.VehicleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Loads the synthetic CSV datasets into the database on startup.
 *
 * <p>Active in the {@code dev} and {@code docker} profiles, where the schema
 * is created by Hibernate. In production the SQL scripts in
 * {@code database/mysql} are the loading path instead.
 *
 * <p>Rows that reference a parent which does not exist are skipped and
 * counted rather than aborting the load, so a partially consistent dataset
 * still yields a usable application and a clear report of what was dropped.
 */
@Service
@Profile({"dev", "docker", "default"})
public class DatasetLoaderService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DatasetLoaderService.class);
    private static final DateTimeFormatter TS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Path datasetsDir;
    private final boolean loadOnStartup;

    private final RouteRepository routeRepository;
    private final StopRepository stopRepository;
    private final RouteStopRepository routeStopRepository;
    private final VehicleRepository vehicleRepository;
    private final TripRepository tripRepository;
    private final ScheduledMovementRepository scheduledRepository;
    private final ActualMovementRepository actualRepository;
    private final PassengerVolumeRepository passengerVolumeRepository;
    private final TransferConnectionRepository connectionRepository;
    private final IncidentRepository incidentRepository;
    private final SystemConfigurationRepository configRepository;

    public DatasetLoaderService(
            @Value("${transitpulse.datasets.dir:../datasets}") String datasetsDir,
            @Value("${transitpulse.datasets.load-on-startup:true}") boolean loadOnStartup,
            RouteRepository routeRepository,
            StopRepository stopRepository,
            RouteStopRepository routeStopRepository,
            VehicleRepository vehicleRepository,
            TripRepository tripRepository,
            ScheduledMovementRepository scheduledRepository,
            ActualMovementRepository actualRepository,
            PassengerVolumeRepository passengerVolumeRepository,
            TransferConnectionRepository connectionRepository,
            IncidentRepository incidentRepository,
            SystemConfigurationRepository configRepository) {
        this.datasetsDir = Paths.get(datasetsDir);
        this.loadOnStartup = loadOnStartup;
        this.routeRepository = routeRepository;
        this.stopRepository = stopRepository;
        this.routeStopRepository = routeStopRepository;
        this.vehicleRepository = vehicleRepository;
        this.tripRepository = tripRepository;
        this.scheduledRepository = scheduledRepository;
        this.actualRepository = actualRepository;
        this.passengerVolumeRepository = passengerVolumeRepository;
        this.connectionRepository = connectionRepository;
        this.incidentRepository = incidentRepository;
        this.configRepository = configRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!loadOnStartup) {
            log.info("Dataset loading disabled (transitpulse.datasets.load-on-startup=false)");
            return;
        }
        if (routeRepository.count() > 0) {
            log.info("Database already populated ({} routes); skipping dataset load",
                    routeRepository.count());
            seedConfiguration();
            return;
        }
        if (!Files.isDirectory(datasetsDir)) {
            log.warn("Datasets directory not found at {}. The API will start with an "
                            + "empty database. Generate datasets with: "
                            + "cd data-engine && python generate_datasets.py",
                    datasetsDir.toAbsolutePath());
            seedConfiguration();
            return;
        }
        try {
            load();
        } catch (IOException e) {
            log.error("Failed to load datasets from {}: {}",
                    datasetsDir.toAbsolutePath(), e.getMessage());
        }
    }

    @Transactional
    protected void load() throws IOException {
        long start = System.currentTimeMillis();
        Map<String, Integer> skipped = new HashMap<>();

        // ---- routes ----
        List<Route> routes = new ArrayList<>();
        for (Map<String, String> r : readCsv("routes.csv")) {
            Route route = new Route();
            route.setRouteId(r.get("route_id"));
            route.setRouteName(r.get("route_name"));
            route.setMode(r.get("mode"));
            route.setOriginName(r.get("origin_name"));
            route.setDestinationName(r.get("destination_name"));
            route.setStopCount(intOf(r.get("stop_count"), 0));
            route.setStatus(r.get("status"));
            route.setDescription(r.get("description"));
            routes.add(route);
        }
        routeRepository.saveAll(routes);
        Set<String> routeIds = new LinkedHashSet<>(routes.stream()
                .map(Route::getRouteId).toList());

        // ---- stops ----
        List<Stop> stops = new ArrayList<>();
        for (Map<String, String> r : readCsv("stops.csv")) {
            Stop stop = new Stop();
            stop.setStopId(r.get("stop_id"));
            stop.setStopName(r.get("stop_name"));
            stop.setStopType(r.get("stop_type"));
            stop.setLatitude(decimalOf(r.get("latitude")));
            stop.setLongitude(decimalOf(r.get("longitude")));
            stop.setIsInterchange(r.get("is_interchange"));
            stop.setStepFree(intOf(r.get("step_free"), 0));
            stops.add(stop);
        }
        stopRepository.saveAll(stops);
        Set<String> stopIds = new LinkedHashSet<>(stops.stream()
                .map(Stop::getStopId).toList());

        // ---- route_stops ----
        List<RouteStop> routeStops = new ArrayList<>();
        for (Map<String, String> r : readCsv("route_stops.csv")) {
            if (!routeIds.contains(r.get("route_id"))
                    || !stopIds.contains(r.get("stop_id"))) {
                count(skipped, "route_stops");
                continue;
            }
            RouteStop rs = new RouteStop();
            rs.setRouteStopId(r.get("route_stop_id"));
            rs.setRouteId(r.get("route_id"));
            rs.setStopId(r.get("stop_id"));
            rs.setStopSequence(intOf(r.get("stop_sequence"), 1));
            rs.setDistanceFromOriginKm(decimalOf(r.get("distance_from_origin_km")));
            rs.setScheduledRuntimeMin(intOf(r.get("scheduled_runtime_min"), 0));
            routeStops.add(rs);
        }
        routeStopRepository.saveAll(routeStops);

        // ---- vehicles ----
        List<Vehicle> vehicles = new ArrayList<>();
        for (Map<String, String> r : readCsv("vehicles.csv")) {
            Vehicle v = new Vehicle();
            v.setVehicleId(r.get("vehicle_id"));
            v.setFleetNumber(r.get("fleet_number"));
            v.setVehicleType(r.get("vehicle_type"));
            v.setCapacity(intOf(r.get("capacity"), 0));
            v.setDepot(r.get("depot"));
            v.setStatus(r.get("status"));
            vehicles.add(v);
        }
        vehicleRepository.saveAll(vehicles);
        Set<String> vehicleIds = new LinkedHashSet<>(vehicles.stream()
                .map(Vehicle::getVehicleId).toList());

        // ---- trips ----
        List<Trip> trips = new ArrayList<>();
        for (Map<String, String> r : readCsv("trips.csv")) {
            if (!routeIds.contains(r.get("route_id"))) {
                count(skipped, "trips");
                continue;
            }
            Trip t = new Trip();
            t.setTripId(r.get("trip_id"));
            t.setRouteId(r.get("route_id"));
            String vehicleId = r.get("vehicle_id");
            t.setVehicleId(vehicleIds.contains(vehicleId) ? vehicleId : null);
            t.setServiceDate(LocalDate.parse(r.get("service_date")));
            t.setScheduledStartTime(timestampOf(r.get("scheduled_start_time")));
            t.setDirection(intOf(r.get("direction"), 0));
            t.setServicePattern(r.get("service_pattern"));
            t.setBlockId(r.get("block_id"));
            t.setStatus(r.get("status"));
            trips.add(t);
        }
        tripRepository.saveAll(trips);
        Set<String> tripIds = new LinkedHashSet<>(trips.stream()
                .map(Trip::getTripId).toList());

        // ---- scheduled_movements ----
        List<ScheduledMovement> scheduled = new ArrayList<>();
        for (Map<String, String> r : readCsv("scheduled_movements.csv")) {
            if (!tripIds.contains(r.get("trip_id"))
                    || !stopIds.contains(r.get("stop_id"))) {
                count(skipped, "scheduled_movements");
                continue;
            }
            ScheduledMovement sm = new ScheduledMovement();
            sm.setScheduledMovementId(r.get("scheduled_movement_id"));
            sm.setTripId(r.get("trip_id"));
            sm.setRouteId(r.get("route_id"));
            sm.setStopId(r.get("stop_id"));
            sm.setStopSequence(intOf(r.get("stop_sequence"), 1));
            sm.setScheduledArrival(timestampOf(r.get("scheduled_arrival")));
            sm.setScheduledDeparture(timestampOf(r.get("scheduled_departure")));
            sm.setIsOrigin(intOf(r.get("is_origin"), 0));
            sm.setIsDestination(intOf(r.get("is_destination"), 0));
            scheduled.add(sm);
        }
        scheduledRepository.saveAll(scheduled);

        // ---- actual_movements ----
        List<ActualMovement> actuals = new ArrayList<>();
        for (Map<String, String> r : readCsv("actual_movements.csv")) {
            if (!tripIds.contains(r.get("trip_id"))
                    || !stopIds.contains(r.get("stop_id"))) {
                count(skipped, "actual_movements");
                continue;
            }
            ActualMovement am = new ActualMovement();
            am.setActualMovementId(r.get("actual_movement_id"));
            am.setTripId(r.get("trip_id"));
            am.setRouteId(r.get("route_id"));
            am.setStopId(r.get("stop_id"));
            am.setStopSequence(intOf(r.get("stop_sequence"), 1));
            // Blank arrival stays null: a missing GPS event is unknown, not on time.
            am.setActualArrival(timestampOf(r.get("actual_arrival")));
            am.setActualDeparture(timestampOf(r.get("actual_departure")));
            am.setObservedDelayMin(decimalOf(r.get("observed_delay_min")));
            String flag = r.get("data_quality_flag");
            am.setDataQualityFlag(flag == null || flag.isBlank()
                    ? ActualMovement.FLAG_OK : flag);
            String diverted = r.get("is_diverted");
            am.setIsDiverted(diverted == null || diverted.isBlank() ? "N" : diverted);
            actuals.add(am);
        }
        actualRepository.saveAll(actuals);

        // ---- passenger_volumes ----
        List<PassengerVolume> volumes = new ArrayList<>();
        Set<String> seenVolumeIds = new LinkedHashSet<>();
        for (Map<String, String> r : readCsv("passenger_volumes.csv")) {
            String id = r.get("passenger_volume_id");
            // A loop route serves a stop twice, which makes the natural key
            // repeat. Keep the first and count the rest as duplicates.
            if (!seenVolumeIds.add(id)
                    || !routeIds.contains(r.get("route_id"))
                    || !stopIds.contains(r.get("stop_id"))) {
                count(skipped, "passenger_volumes");
                continue;
            }
            PassengerVolume pv = new PassengerVolume();
            pv.setPassengerVolumeId(id);
            pv.setRouteId(r.get("route_id"));
            pv.setStopId(r.get("stop_id"));
            pv.setStopSequence(intOf(r.get("stop_sequence"), 0));
            pv.setTimeBand(r.get("time_band"));
            pv.setBoardings(intOf(r.get("boardings"), 0));
            pv.setAlightings(intOf(r.get("alightings"), 0));
            pv.setAverageLoad(intOf(r.get("average_load"), 0));
            pv.setAverageWaitMin(decimalOf(r.get("average_wait_min")));
            pv.setIsLowVolume(r.get("is_low_volume"));
            volumes.add(pv);
        }
        passengerVolumeRepository.saveAll(volumes);

        // ---- connections ----
        List<TransferConnection> connections = new ArrayList<>();
        for (Map<String, String> r : readCsv("connections.csv")) {
            if (!tripIds.contains(r.get("feeder_trip_id"))
                    || !tripIds.contains(r.get("connecting_trip_id"))
                    || !stopIds.contains(r.get("interchange_stop_id"))) {
                count(skipped, "connections");
                continue;
            }
            TransferConnection c = new TransferConnection();
            c.setConnectionId(r.get("connection_id"));
            c.setInterchangeStopId(r.get("interchange_stop_id"));
            c.setFeederTripId(r.get("feeder_trip_id"));
            c.setFeederRouteId(r.get("feeder_route_id"));
            c.setConnectingTripId(r.get("connecting_trip_id"));
            c.setConnectingRouteId(r.get("connecting_route_id"));
            c.setFeederScheduledArrival(timestampOf(r.get("feeder_scheduled_arrival")));
            c.setConnectingScheduledDeparture(
                    timestampOf(r.get("connecting_scheduled_departure")));
            c.setPlannedTransferMin(decimalOf(r.get("planned_transfer_min")));
            c.setRequiredBufferMin(decimalOf(r.get("required_buffer_min")));
            c.setTypicalTransferringPassengers(
                    intOf(r.get("typical_transferring_passengers"), 0));
            connections.add(c);
        }
        connectionRepository.saveAll(connections);

        // ---- incidents ----
        List<Incident> incidents = new ArrayList<>();
        for (Map<String, String> r : readCsv("incidents.csv")) {
            if (!routeIds.contains(r.get("route_id"))) {
                count(skipped, "incidents");
                continue;
            }
            Incident i = new Incident();
            i.setIncidentId(r.get("incident_id"));
            i.setIncidentType(r.get("incident_type"));
            i.setRouteId(r.get("route_id"));
            String stopId = r.get("stop_id");
            i.setStopId(stopIds.contains(stopId) ? stopId : null);
            i.setStartTime(timestampOf(r.get("start_time")));
            i.setEndTime(timestampOf(r.get("end_time")));
            i.setSeverity(r.get("severity"));
            i.setDescription(r.get("description"));
            i.setIsDiversion(r.get("is_diversion"));
            i.setStatus(r.get("status"));
            i.setReportedBy(r.get("reported_by"));
            incidents.add(i);
        }
        incidentRepository.saveAll(incidents);

        seedConfiguration();

        log.info("Loaded datasets in {} ms: {} routes, {} stops, {} vehicles, {} trips, "
                        + "{} scheduled, {} actual, {} volumes, {} connections, {} incidents",
                System.currentTimeMillis() - start, routes.size(), stops.size(),
                vehicles.size(), trips.size(), scheduled.size(), actuals.size(),
                volumes.size(), connections.size(), incidents.size());
        if (!skipped.isEmpty()) {
            log.warn("Skipped rows with unresolved references or duplicate keys: {}", skipped);
        }
    }

    /** Seed the analytical parameters if they are not already present. */
    @Transactional
    protected void seedConfiguration() {
        if (configRepository.count() > 0) {
            return;
        }
        List<SystemConfiguration> defaults = List.of(
                cfg("on_time_threshold_min", "2.0", "NUMBER", "DELAY",
                        "A vehicle is on time if its delay is below this many minutes",
                        "0", "15"),
                cfg("minor_delay_threshold_min", "5.0", "NUMBER", "DELAY",
                        "Upper bound of a MINOR delay in minutes", "1", "30"),
                cfg("moderate_delay_threshold_min", "15.0", "NUMBER", "DELAY",
                        "Upper bound of a MODERATE delay; above this is SEVERE", "5", "60"),
                cfg("early_arrival_threshold_min", "-2.0", "NUMBER", "DELAY",
                        "Arrivals earlier than this many minutes are flagged EARLY",
                        "-15", "0"),
                cfg("propagation_window_min", "90", "NUMBER", "PROPAGATION",
                        "How far ahead the propagation engine looks, in minutes",
                        "10", "240"),
                cfg("min_propagated_delay_min", "2.0", "NUMBER", "PROPAGATION",
                        "Minimum knock-on delay worth reporting as propagation", "0", "30"),
                cfg("vehicle_turnaround_min", "5.0", "NUMBER", "PROPAGATION",
                        "Scheduled turnaround between consecutive trips of one vehicle",
                        "0", "60"),
                cfg("recovery_min_reduction_min", "1.0", "NUMBER", "PROPAGATION",
                        "Minimum delay reduction counted as recovery", "0", "30"),
                cfg("required_transfer_buffer_min", "5.0", "NUMBER", "CONNECTION",
                        "Minutes passengers need to change services at an interchange",
                        "0", "30"),
                cfg("max_transfer_wait_min", "30.0", "NUMBER", "CONNECTION",
                        "Longest wait still treated as a usable connection", "5", "120"),
                cfg("missed_connection_margin_min", "0.0", "NUMBER", "CONNECTION",
                        "Available transfer time below this counts as missed", "-10", "10"),
                cfg("low_volume_stop_threshold", "20", "NUMBER", "PASSENGER",
                        "Boardings below this mark a stop as low volume / low confidence",
                        "0", "500"),
                cfg("transfer_rate", "0.18", "NUMBER", "PASSENGER",
                        "Assumed share of boarding passengers who transfer", "0", "1"),
                cfg("additional_wait_on_missed_connection_min", "15.0", "NUMBER",
                        "PASSENGER",
                        "Assumed wait for the next service after a missed connection",
                        "1", "120"),
                cfg("missing_gps_strategy", "EXCLUDE", "STRING", "DATA_QUALITY",
                        "How missing GPS events are handled: EXCLUDE or ESTIMATE",
                        null, null),
                cfg("sensitivity_buffers_min", "3,5,8,10", "LIST", "SENSITIVITY",
                        "Transfer buffers tested by the sensitivity analysis", null, null));
        configRepository.saveAll(defaults);
        log.info("Seeded {} analytical configuration parameters", defaults.size());
    }

    private static SystemConfiguration cfg(String key, String value, String type,
                                           String category, String description,
                                           String min, String max) {
        return new SystemConfiguration(key, value, type, category, description,
                min == null ? null : new BigDecimal(min),
                max == null ? null : new BigDecimal(max));
    }

    // ------------------------------------------------------------------ //

    /** Minimal RFC 4180 CSV reader: handles quoted fields and embedded commas. */
    List<Map<String, String>> readCsv(String filename) throws IOException {
        Path path = datasetsDir.resolve(filename);
        if (!Files.exists(path)) {
            log.warn("Dataset file missing: {}", path.toAbsolutePath());
            return List.of();
        }
        List<Map<String, String>> rows = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                return rows;
            }
            List<String> headers = parseLine(headerLine);
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                List<String> values = parseLine(line);
                Map<String, String> row = new HashMap<>();
                for (int i = 0; i < headers.size(); i++) {
                    row.put(headers.get(i), i < values.size() ? values.get(i) : null);
                }
                rows.add(row);
            }
        }
        return rows;
    }

    /** Split one CSV line, honouring quoted fields and escaped quotes. */
    public static List<String> parseLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    field.append(ch);
                }
            } else if (ch == '"') {
                inQuotes = true;
            } else if (ch == ',') {
                out.add(field.toString());
                field.setLength(0);
            } else {
                field.append(ch);
            }
        }
        out.add(field.toString());
        return out;
    }

    private static void count(Map<String, Integer> map, String key) {
        map.merge(key, 1, Integer::sum);
    }

    private static Integer intOf(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return (int) Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static BigDecimal decimalOf(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Parses "yyyy-MM-dd HH:mm:ss" and ISO forms; blank means genuinely absent. */
    public static LocalDateTime timestampOf(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String v = value.trim();
        try {
            return LocalDateTime.parse(v, TS);
        } catch (Exception ignored) {
            try {
                return LocalDateTime.parse(v.replace(' ', 'T'));
            } catch (Exception e) {
                log.debug("Unparseable timestamp: {}", v);
                return null;
            }
        }
    }
}
