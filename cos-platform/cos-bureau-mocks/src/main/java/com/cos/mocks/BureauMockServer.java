package com.cos.mocks;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

/**
 * Starts a single WireMock instance serving all 6 bureau mocks from the Integration Workbook
 * (Northgate KYC, Northgate KYB, Meridian Bureau, Sentinel Compliance, Clearbank Verification,
 * Lumen Document Intelligence). One instance is enough since every service's resource path is
 * distinct; the orchestration service is configured (per environment) to point at this
 * instance's host:port instead of each bureau's real hostname.
 */
public final class BureauMockServer {

    public static final int DEFAULT_PORT = 9561;

    private BureauMockServer() {
    }

    public static void main(String[] args) {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        WireMockServer server = create(port);
        server.start();
        System.out.println("Bureau mocks listening on http://localhost:" + port);
    }

    public static WireMockServer create(int port) {
        WireMockConfiguration config = WireMockConfiguration.options()
                .port(port)
                .usingFilesUnderClasspath("wiremock")
                .globalTemplating(true);
        return new WireMockServer(config);
    }
}
