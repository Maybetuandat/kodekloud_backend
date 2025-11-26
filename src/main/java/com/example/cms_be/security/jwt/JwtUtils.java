package com.example.cms_be.security.jwt;

import com.example.cms_be.security.service.UserDetailsImpl;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

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
        try {
            JWSHeader header = new JWSHeader(JWSAlgorithm.HS512);
            UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();

            var roles = userDetails.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.toList());

            JWTClaimsSet.Builder claimsBuilder = new JWTClaimsSet.Builder()
                    .subject(userDetails.getUsername())
                    .claim("id", userDetails.getId())
                    .claim("email", userDetails.getEmail())
                    .claim("username", userDetails.getUsername())
                    .claim("roles", roles) // Đổi thành số nhiều cho chuẩn
                    .issuer("labplatform")
                    .issueTime(new Date())
                    .expirationTime(new Date(Instant.now()
                            .plus(isRefresh ? refreshExpirationMs : jwtExpirationMs, ChronoUnit.MILLIS)
                            .toEpochMilli()))
                    .jwtID(UUID.randomUUID().toString());

            Payload payload = new Payload(claimsBuilder.build().toJSONObject());
            JWSObject jwsObject = new JWSObject(header, payload);

            jwsObject.sign(new MACSigner(jwtSecret.getBytes(StandardCharsets.UTF_8)));

            return jwsObject.serialize();
        } catch (JOSEException e) {
            log.error("Error generating JWT token: {}", e.getMessage());
            throw new RuntimeException("Cannot generate JWT token", e);
        }
    }

    public boolean validateToken(String token, UserDetails user) throws JOSEException, ParseException {
        JWSVerifier verifier = new MACVerifier(jwtSecret.getBytes(StandardCharsets.UTF_8));
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
        Object userIdObj = claims.get("id");

        if (userIdObj == null) return null;

        // FIX: Xử lý an toàn kiểu số từ JSON (tránh ClassCastException)
        if (userIdObj instanceof Number) {
            return ((Number) userIdObj).intValue();
        } else if (userIdObj instanceof String) {
            try {
                return Integer.valueOf((String) userIdObj);
            } catch (NumberFormatException e) {
                log.error("Invalid user ID format in token: {}", userIdObj);
                return null;
            }
        }

        return null; // Không nên return 1 mặc định (Bảo mật)
    }
}