package org.example.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.jmx.JmxConfig;
import io.micrometer.jmx.JmxMeterRegistry;

import java.time.Duration;

/**
 * Configuration for metrics using Micrometer.
 */
public class MetricsConfig {
    
    private static final MeterRegistry registry = createRegistry();
    
    /**
     * Creates and configures a meter registry.
     *
     * @return the configured meter registry
     */
    private static MeterRegistry createRegistry() {
        // Create a JMX registry for production use
        JmxMeterRegistry jmxRegistry = new JmxMeterRegistry(
                config -> {
                    // Default implementation returns null for unknown keys
                    if ("step".equals(config)) return "PT10S"; // 10 seconds
                    if ("domain".equals(config)) return "org.example";
                    return null;
                },
                io.micrometer.core.instrument.Clock.SYSTEM
        );
        
        return jmxRegistry;
    }
    
    /**
     * Gets the meter registry.
     *
     * @return the meter registry
     */
    public static MeterRegistry getRegistry() {
        return registry;
    }
} 