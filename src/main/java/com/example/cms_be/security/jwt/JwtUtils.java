package com.example.cms_be.security.jwt;

import com.example.cms_be.security.service.UserDetailsImpl;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.security.core.GrantedAuthority;

import java.text.ParseException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;


@Component
@Slf4j
public class JwtUtils {

    @Value("${app.jwtSecret}")
    private String jwtSecret;

    @Value("${app.jwtExpirationMs}")
    private long jwtExpirationMs;

    @Value("${app.refreshExpirationMs:604800000}") // Default 7 days
    private long refreshExpirationMs;

    public String generateToken(Authentication authentication, boolean isRefresh) {
        JWSHeader header = new JWSHeader(JWSAlgorithm.HS512);
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();

        JWTClaimsSet jwtClaimsSet = new JWTClaimsSet.Builder()
                .subject(userDetails.getUsername())
                .claim("id", userDetails.getId())
                .claim("email", userDetails.getEmail())
                .claim("username", userDetails.getUsername())
                .claim("role", userDetails.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .collect(Collectors.toList()))
                .issuer("labplatform")
                .issueTime(new Date())
                .expirationTime(new Date(Instant.now()
                        .plus(isRefresh ? refreshExpirationMs : jwtExpirationMs, ChronoUnit.MILLIS)
                        .toEpochMilli()))
                .jwtID(UUID.randomUUID().toString())
                .build();

        Payload payload = new Payload(jwtClaimsSet.toJSONObject());

        JWSObject jwsObject = new JWSObject(header, payload);
        try {
            jwsObject.sign(new MACSigner(jwtSecret.getBytes()));
            return jwsObject.serialize();
        } catch (JOSEException e) {
            log.error("Không thể tạo token", e);
            throw new RuntimeException(e);
        }
    }

    public boolean validateToken(String token, UserDetails user) throws JOSEException, ParseException {
        JWSVerifier verifier = new MACVerifier((jwtSecret.getBytes()));
        SignedJWT signedJWT = SignedJWT.parse(token);
        boolean verified = signedJWT.verify(verifier);
        Date expiryTime = signedJWT.getJWTClaimsSet().getExpirationTime();
        String userName = signedJWT.getJWTClaimsSet().getSubject();

        return verified &&
                expiryTime.after(new Date()) &&
                user.getUsername().equals(userName);
    }

    public String getUserNameFromToken(String token) throws ParseException {
        SignedJWT signedJWT = SignedJWT.parse(token);
        return signedJWT.getJWTClaimsSet().getSubject();
    }

    public Map<String, Object> getClaimsFromToken(String token) throws ParseException {
        SignedJWT signedJWT = SignedJWT.parse(token);
        return signedJWT.getJWTClaimsSet().getClaims();
    }

    public Integer getUserIdFromToken(String token) throws ParseException {
        Map<String, Object> claims = getClaimsFromToken(token);
        Object userId = claims.get("id");
        if (userId != null) {
            return Integer.valueOf((String) userId);
        }
        return 1;
    }
}
