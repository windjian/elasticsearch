/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */

package org.elasticsearch.xpack.security.authc.kerberos;

import org.elasticsearch.ElasticsearchSecurityException;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.util.concurrent.ThreadContext;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.xpack.core.security.authc.AuthenticationToken;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;

/**
 * Holds on to base 64 encoded ticket, also helps extracting token from
 * {@link ThreadContext}
 */
public final class KerberosAuthenticationToken implements AuthenticationToken {

    public static final String WWW_AUTHENTICATE = "WWW-Authenticate";
    public static final String AUTH_HEADER = "Authorization";
    public static final String NEGOTIATE_AUTH_HEADER = "Negotiate ";
    public static final String UNAUTHENTICATED_PRINCIPAL_NAME = "<Unauthenticated Principal Name>";

    private final String principalName;
    private String base64EncodedTicket;

    public KerberosAuthenticationToken(final String base64EncodedToken) {
        this.principalName = UNAUTHENTICATED_PRINCIPAL_NAME;
        this.base64EncodedTicket = base64EncodedToken;
    }

    /**
     * Extract token from header and if valid {@link #NEGOTIATE_AUTH_HEADER} then
     * returns {@link KerberosAuthenticationToken}
     *
     * @param context {@link ThreadContext}
     * @return returns {@code null} if {@link #AUTH_HEADER} is empty or not an
     *         {@link #NEGOTIATE_AUTH_HEADER} else returns valid
     *         {@link KerberosAuthenticationToken}
     */
    public static KerberosAuthenticationToken extractToken(final ThreadContext context) {
        final String authHeader = context.getHeader(AUTH_HEADER);
        if (Strings.isNullOrEmpty(authHeader)) {
            return null;
        }

        if (authHeader.startsWith(NEGOTIATE_AUTH_HEADER) == Boolean.FALSE) {
            return null;
        }

        final String base64EncodedToken = authHeader.substring(NEGOTIATE_AUTH_HEADER.length()).trim();
        if (Strings.isEmpty(base64EncodedToken)) {
            throw unauthorized("invalid negotiate authentication header value, expected base64 encoded token but value is empty", null);
        }
        final byte[] base64Token = base64EncodedToken.getBytes(StandardCharsets.UTF_8);
        byte[] decodedKerberosTicket = null;
        IllegalArgumentException rootCause = null;
        try {
            decodedKerberosTicket = Base64.getDecoder().decode(base64Token);
        } catch (IllegalArgumentException iae) {
            rootCause = iae;
        }

        if (decodedKerberosTicket == null || decodedKerberosTicket.length == 0) {
            throw unauthorized("invalid negotiate authentication header value, could not decode base64 token {}", rootCause,
                    base64EncodedToken);
        }
        return new KerberosAuthenticationToken(base64EncodedToken);
    }

    @Override
    public String principal() {
        return principalName;
    }

    @Override
    public Object credentials() {
        return base64EncodedTicket;
    }

    @Override
    public void clearCredentials() {
        this.base64EncodedTicket = null;
    }

    @Override
    public int hashCode() {
        return Objects.hash(principalName, base64EncodedTicket);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other)
            return true;
        if (other == null)
            return false;
        if (getClass() != other.getClass())
            return false;
        final KerberosAuthenticationToken otherKerbToken = (KerberosAuthenticationToken) other;
        return Objects.equals(otherKerbToken.principal(), principal()) && Objects.equals(otherKerbToken.credentials(), credentials());
    }

    private static ElasticsearchSecurityException unauthorized(String message, Throwable cause, Object... args) {
        ElasticsearchSecurityException ese = new ElasticsearchSecurityException(message, RestStatus.UNAUTHORIZED, args);
        ese.addHeader(WWW_AUTHENTICATE, NEGOTIATE_AUTH_HEADER);
        return ese;
    }
}