package com.golinkgone.glgbackend.controller;

import com.golinkgone.glgbackend.entity.CreateRequest;
import com.golinkgone.glgbackend.entity.CreateResponse;
import com.golinkgone.glgbackend.entity.DashboardResponse;
import com.golinkgone.glgbackend.entity.LinkItemResponse;
import com.golinkgone.glgbackend.entity.*;
import com.golinkgone.glgbackend.service.DashboardService;
import com.golinkgone.glgbackend.service.OwnerService;
import com.golinkgone.glgbackend.service.URLShortenerService;
import com.golinkgone.glgbackend.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.AccessDeniedException;

import java.net.URI;
import java.util.UUID;

@RestController
public class URLController {
    private final URLShortenerService urlShortenerService;
    private final DashboardService dashboardService;
    private final OwnerService ownerService;
    private final UserService userService;


    public URLController(URLShortenerService urlShortenerService,
                         DashboardService dashboardService, OwnerService ownerService, UserService userService) {
        this.urlShortenerService = urlShortenerService;
        this.dashboardService = dashboardService;
        this.ownerService = ownerService;
        this.userService = userService;
    }

    @GetMapping("/health")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("GoLinkGone OK");
    }

    @GetMapping("/info")
    public ResponseEntity<String> info() {
        return ResponseEntity.ok("This is GoLinkGone :P");
    }

    @PostMapping("/create")
    public ResponseEntity<CreateResponse> createShortLink(@Valid @RequestBody CreateRequest request,
                                                          @AuthenticationPrincipal Jwt jwt) {

        UUID userId = (jwt != null) ? UUID.fromString(jwt.getSubject()) : null;
        CreateResponse response = urlShortenerService.createShortLink(request.originalUrl(), userId);
        return ResponseEntity.ok(response);
    }

    @RequestMapping(value = "/{shortKey}", method = {RequestMethod.GET, RequestMethod.HEAD})
    public ResponseEntity<Void> redirectUrl(@PathVariable String shortKey, HttpServletRequest request) {
        
        boolean countClick = "GET".equalsIgnoreCase(request.getMethod());
        String ip = countClick ? request.getRemoteAddr() : null;
        String userAgent = countClick ? request.getHeader("User-Agent") : null;
        String secChUaMobile = countClick ? request.getHeader("Sec-CH-UA-Mobile") : null;

        String originalUrl = urlShortenerService.redirectUrl(shortKey, ip, userAgent, secChUaMobile, countClick);
        return ResponseEntity.status(302).location(URI.create(originalUrl)).build();
    }

    @GetMapping("/my-links")
    public ResponseEntity<Page<LinkItemResponse>> getMyLinks(@AuthenticationPrincipal Jwt jwt,
                                                             @RequestParam(defaultValue = "0")int page,
                                                             @RequestParam(defaultValue = "30")int size){


        Page<LinkItemResponse> links = urlShortenerService.getUserLinks(UUID.fromString(jwt.getSubject()), page,
                Math.min(size, 100));
        return ResponseEntity.ok(links);
    }

    @GetMapping("/{shortKey}/dashboard")
    public ResponseEntity<DashboardResponse> getDashboard(@PathVariable String shortKey,
                                                          @RequestParam(defaultValue = "24h") String timeRange,
                                                          @RequestParam(defaultValue = "day") String granularity,
                                                          @RequestParam(defaultValue = "UTC") String tz,
                                                          @AuthenticationPrincipal Jwt jwt) {

        UUID linkId = ownerService.resolveOwnedLinkId(shortKey, UUID.fromString(jwt.getSubject()))
                .orElseThrow(() -> new AccessDeniedException("Access Denied"));

        DashboardResponse response = dashboardService.getDashboard(linkId, timeRange, granularity, tz);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{shortKey}")
    public ResponseEntity<Void> deleteLink(@PathVariable String shortKey, @AuthenticationPrincipal Jwt jwt) {

        urlShortenerService.deleteLink(shortKey, UUID.fromString(jwt.getSubject()));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/account")
    public ResponseEntity<Void> deleteAccount(@AuthenticationPrincipal Jwt jwt) {
        userService.deleteAccount(UUID.fromString(jwt.getSubject()));
        return ResponseEntity.noContent().build();
    }

}
