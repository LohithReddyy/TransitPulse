package com.transitpulse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * TransitPulse - Public Transit Delay Propagation & Passenger Impact Analysis.
 *
 * <p>Serves master transit data from the relational database and analytical
 * results produced by the Python data engine.
 */
@SpringBootApplication
public class TransitPulseApplication {

    public static void main(String[] args) {
        SpringApplication.run(TransitPulseApplication.class, args);
    }
}
