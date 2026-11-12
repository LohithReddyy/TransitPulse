
package com.transitpulse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.transitpulse.dto.IncidentRequest;
import com.transitpulse.repository.RouteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end API tests over the real HTTP layer.
 *
 * These tests verify the API response contract:
 * {success, message, data, timestamp}.
 *
 * Protected endpoints are accessed using a valid JWT token.
 * Public endpoints are tested without authentication.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class ApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RouteRepository routeRepository;

    private String authToken;

    /**
     * Register a unique test user before every test
     * and obtain a JWT token for protected endpoints.
     */
    @BeforeEach
    void authenticateTestUser() throws Exception {

        String email = "api-test-" + System.nanoTime() + "@example.com";

        String requestBody = """
                {
                    "fullName": "API Test User",
                    "email": "%s",
                    "password": "TransitPulse!2026"
                }
                """.formatted(email);

        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        authToken = objectMapper.readTree(response)
                .path("data")
                .path("token")
                .asText();

        if (authToken.isBlank()) {
            throw new IllegalStateException(
                    "JWT token was not generated for the integration test user."
            );
        }
    }

    /**
     * Add JWT authentication to protected API requests.
     */
    private org.springframework.test.web.servlet.request
            .MockHttpServletRequestBuilder authenticated(
                    org.springframework.test.web.servlet.request
                            .MockHttpServletRequestBuilder request) {

        return request.header("Authorization", "Bearer " + authToken);
    }

    @Test
    @DisplayName("health endpoint reports UP without authentication")
    void health() throws Exception {

        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("UP"));
    }

    @Test
    @DisplayName("routes endpoint returns the standard envelope")
    void routesEnvelope() throws Exception {

        mockMvc.perform(authenticated(get("/api/routes")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("stops endpoint returns the standard envelope")
    void stopsEnvelope() throws Exception {

        mockMvc.perform(authenticated(get("/api/stops")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("dashboard always answers, even before the engine has run")
    void dashboardAlwaysAvailable() throws Exception {

        mockMvc.perform(authenticated(get("/api/analytics/dashboard")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalRoutes").exists())
                .andExpect(jsonPath("$.data.totalStops").exists())
                .andExpect(jsonPath("$.data.analyticsAvailable").exists());
    }

    @Test
    @DisplayName("configuration endpoints expose the analytical parameters")
    void configuration() throws Exception {

        mockMvc.perform(authenticated(get("/api/config")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());

        mockMvc.perform(authenticated(get("/api/config/grouped")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isMap());
    }

    @Test
    @DisplayName("an unknown route yields 404 with a clear message")
    void unknownRouteIs404() throws Exception {

        mockMvc.perform(authenticated(get("/api/routes/DOES_NOT_EXIST")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("DOES_NOT_EXIST")
                ));
    }

    @Test
    @DisplayName("an invalid configuration value yields 400")
    void invalidConfigIs400() throws Exception {

        mockMvc.perform(authenticated(
                        put("/api/config/on_time_threshold_min")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"configValue\":\"not-a-number\"}")
                ))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("an incident with a bad enum fails validation with field errors")
    void incidentValidation() throws Exception {

        IncidentRequest bad = new IncidentRequest();
        bad.setIncidentType("NOT_A_TYPE");
        bad.setRouteId("R01");
        bad.setStartTime("2026-03-16T09:00:00");

        mockMvc.perform(authenticated(
                        post("/api/incidents")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(bad))
                ))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.incidentType").exists());
    }

    @Test
    @DisplayName("an incident on an unknown route is rejected")
    void incidentUnknownRoute() throws Exception {

        IncidentRequest bad = new IncidentRequest();
        bad.setIncidentType("ROAD_WORKS");
        bad.setRouteId("NOPE");
        bad.setStartTime("2026-03-16T09:00:00");

        mockMvc.perform(authenticated(
                        post("/api/incidents")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(bad))
                ))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("Unknown routeId")
                ));
    }

    @Test
    @DisplayName("an incident can be created, updated and deleted")
    void incidentLifecycle() throws Exception {

        org.junit.jupiter.api.Assumptions.assumeFalse(
                routeRepository.findAll().isEmpty(),
                "datasets not loaded"
        );

        String routeId = routeRepository.findAll().get(0).getRouteId();

        IncidentRequest request = new IncidentRequest();
        request.setIncidentType("TRAFFIC_CONGESTION");
        request.setRouteId(routeId);
        request.setStartTime("2026-03-16T09:00:00");
        request.setEndTime("2026-03-16T10:00:00");
        request.setSeverity("HIGH");
        request.setDescription("Created by the integration test");

        String body = mockMvc.perform(authenticated(
                        post("/api/incidents")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                ))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.incidentId").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String incidentId = objectMapper.readTree(body)
                .get("data")
                .get("incidentId")
                .asText();

        request.setSeverity("CRITICAL");

        mockMvc.perform(authenticated(
                        put("/api/incidents/" + incidentId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.severity").value("CRITICAL"));

        mockMvc.perform(authenticated(
                        delete("/api/incidents/" + incidentId)
                ))
                .andExpect(status().isOk());

        mockMvc.perform(authenticated(
                        get("/api/incidents/" + incidentId)
                ))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("an incident whose end precedes its start is rejected")
    void incidentBadTimeWindow() throws Exception {

        org.junit.jupiter.api.Assumptions.assumeFalse(
                routeRepository.findAll().isEmpty(),
                "datasets not loaded"
        );

        IncidentRequest request = new IncidentRequest();
        request.setIncidentType("WEATHER");
        request.setRouteId(routeRepository.findAll().get(0).getRouteId());
        request.setStartTime("2026-03-16T10:00:00");
        request.setEndTime("2026-03-16T09:00:00");

        mockMvc.perform(authenticated(
                        post("/api/incidents")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                ))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("cannot be before")
                ));
    }

    @Test
    @DisplayName("engine status is reported without failing when analytics are absent")
    void engineStatus() throws Exception {

        mockMvc.perform(authenticated(
                        get("/api/analytics/engine-status")
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.payloads").exists());
    }

    @Test
    @DisplayName("vehicles and connections endpoints respond")
    void referenceData() throws Exception {

        mockMvc.perform(authenticated(get("/api/vehicles")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());

        mockMvc.perform(authenticated(get("/api/connections")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }
}
