package com.infobip.openapi.mcp.auth.scope;

import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.BDDMockito.given;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.text.ParseException;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.BDDMockito;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JwtScopeServiceTest {

    @InjectMocks
    private JwtScopeService jwtScopeService;

    @Mock
    private ScopeDiscoveryService scopeDiscoveryService;

    private static final String SCOPE_CLAIM = "scope";

    @ParameterizedTest
    @ValueSource(strings = {"Bearer", "bearer", "bEaRer", "BEARER"})
    void shouldDecodeValidJwtFromBearerTokenWhileCaseInsensitive(String givenValidPrefix) throws ParseException {
        // Given
        var givenScopes = "read write";
        var givenJwt = givenJwtToken(givenScopes);
        var givenAuthHeader = givenValidPrefix + " " + givenJwt;

        // When
        var actualScopes = jwtScopeService.decodeJwtTokenAndExtractScopes(givenAuthHeader);

        // Then
        then(actualScopes).isNotEmpty();
        then(actualScopes).contains(givenScopes.split(" "));
        BDDMockito.then(scopeDiscoveryService).shouldHaveNoInteractions();
    }

    @Test
    void shouldHandleLeadingAndTrailingWhitespaceInToken() throws ParseException {
        // Given
        var givenScopes = "read write";
        var givenJwt = givenJwtToken(givenScopes);
        var givenAuthHeaderWithWhitespace = "Bearer   " + givenJwt + "   ";

        // When
        var actualScopes = jwtScopeService.decodeJwtTokenAndExtractScopes(givenAuthHeaderWithWhitespace);

        // Then
        then(actualScopes).isNotEmpty();
        then(actualScopes).contains(givenScopes.split(" "));
        BDDMockito.then(scopeDiscoveryService).shouldHaveNoInteractions();
    }

    @Test
    void shouldReturnNullWhenAuthHeaderIsNull() {
        // When
        var actualScopes = jwtScopeService.decodeJwtTokenAndExtractScopes(null);

        // Then
        then(actualScopes).isEmpty();
        BDDMockito.then(scopeDiscoveryService).shouldHaveNoInteractions();
    }

    @Test
    void shouldReturnNullWhenMalformedJwtToken() {
        // Given
        var givenAuthHeader = "Bearer invalid.malformed.jwt";

        // When
        var actualScopes = jwtScopeService.decodeJwtTokenAndExtractScopes(givenAuthHeader);

        // Then
        then(actualScopes).isEmpty();
        BDDMockito.then(scopeDiscoveryService).shouldHaveNoInteractions();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "NotBearer"})
    void shouldReturnNullForInvalidAuthHeaderFormats(String givenInvalidFormat) {
        // Given
        var givenScopes = "read write";
        var givenJwt = givenJwtToken(givenScopes);
        var givenAuthHeader = givenInvalidFormat + " " + givenJwt;

        // When
        var actualScopes = jwtScopeService.decodeJwtTokenAndExtractScopes(givenAuthHeader);

        // Then
        then(actualScopes).isEmpty();
        BDDMockito.then(scopeDiscoveryService).shouldHaveNoInteractions();
    }

    @Test
    void shouldExtractScopesFromJwtClaims() {
        // Given
        var givenScopes = "read:users write:users admin:config";
        var givenClaims = new JWTClaimsSet.Builder().claim("scope", givenScopes).build();
        var givenJwt = givenJwtTokenWithClaimSet(givenClaims);
        var givenAuthHeader = "Bearer " + givenJwt;

        // When
        var actualScopes = jwtScopeService.decodeJwtTokenAndExtractScopes(givenAuthHeader);

        // Then
        then(actualScopes).hasSize(3);
        then(actualScopes).containsExactlyInAnyOrder("read:users", "write:users", "admin:config");
        BDDMockito.then(scopeDiscoveryService).shouldHaveNoInteractions();
    }

    @Test
    void shouldReturnEmptySetWhenScopeClaimNotPresent() {
        // Given
        var givenClaimsWithoutScope = new JWTClaimsSet.Builder().build();
        var givenJwt = givenJwtTokenWithClaimSet(givenClaimsWithoutScope);
        var givenAuthHeader = "Bearer " + givenJwt;

        // When
        var actualScopes = jwtScopeService.decodeJwtTokenAndExtractScopes(givenAuthHeader);

        // Then
        then(actualScopes).isEmpty();
        BDDMockito.then(scopeDiscoveryService).shouldHaveNoInteractions();
    }

    @Test
    void shouldReturnEmptySetWhenScopeClaimIsBlank() {
        // Given
        var givenScopes = "   ";
        var givenClaimWithBlankScope =
                new JWTClaimsSet.Builder().claim(SCOPE_CLAIM, givenScopes).build();
        var givenJwt = givenJwtTokenWithClaimSet(givenClaimWithBlankScope);
        var givenAuthHeader = "Bearer " + givenJwt;

        // When
        var actualScopes = jwtScopeService.decodeJwtTokenAndExtractScopes(givenAuthHeader);

        // Then
        then(actualScopes).isEmpty();
        BDDMockito.then(scopeDiscoveryService).shouldHaveNoInteractions();
    }

    @Test
    void shouldHandleMultipleConsecutiveSpacesInScopes() {
        // Given
        var givenScopes = "read:users  write:users      admin:config";
        var givenClaimsWithSpaces =
                new JWTClaimsSet.Builder().claim(SCOPE_CLAIM, givenScopes).build();
        var givenJwt = givenJwtTokenWithClaimSet(givenClaimsWithSpaces);
        var givenAuthHeader = "Bearer " + givenJwt;

        // When
        var actualScopes = jwtScopeService.decodeJwtTokenAndExtractScopes(givenAuthHeader);

        // Then
        then(actualScopes).contains("read:users", "write:users", "admin:config");
        BDDMockito.then(scopeDiscoveryService).shouldHaveNoInteractions();
    }

    @Test
    void shouldVerifyScopesWhenTokenScopesFromHeaderContainAllRequired() {
        // Given
        var givenAuthHeader = "Bearer " + givenJwtToken("read:users write:users admin:config");
        var givenRequiredScopes = Set.of("read:users", "write:users");

        given(scopeDiscoveryService.getDiscoveredScopes()).willReturn(givenRequiredScopes);

        // When
        var isValid = jwtScopeService.verifyScopesFromHeader(givenAuthHeader);

        // Then
        then(isValid).isTrue();
        BDDMockito.then(scopeDiscoveryService).should().getDiscoveredScopes();
    }

    @Test
    void shouldVerifyScopesFromHeaderWithExactMatch() {
        // Given
        var givenAuthHeader = "Bearer " + givenJwtToken("read:users write:users");
        var givenRequiredScopes = Set.of("read:users", "write:users");

        given(scopeDiscoveryService.getDiscoveredScopes()).willReturn(givenRequiredScopes);

        // When
        var isValid = jwtScopeService.verifyScopesFromHeader(givenAuthHeader);

        // Then
        then(isValid).isTrue();
        BDDMockito.then(scopeDiscoveryService).should().getDiscoveredScopes();
    }

    @Test
    void shouldSucceedWhenRequiredScopesEmpty() {
        // Given
        var givenAuthHeader = "Bearer " + givenJwtToken("");
        var givenRequiredScopes = Set.<String>of();

        given(scopeDiscoveryService.getDiscoveredScopes()).willReturn(givenRequiredScopes);

        // When
        var isValid = jwtScopeService.verifyScopesFromHeader(givenAuthHeader);

        // Then
        then(isValid).isTrue();
        BDDMockito.then(scopeDiscoveryService).should().getDiscoveredScopes();
    }

    @Test
    void shouldFailVerificationWhenTokenScopesMissingRequired() {
        // Given
        var givenAuthHeader = "Bearer " + givenJwtToken("read:users write:users");
        var givenRequiredScopes = Set.of("read:users", "write:users", "admin:config");

        given(scopeDiscoveryService.getDiscoveredScopes()).willReturn(givenRequiredScopes);

        // When
        var isValid = jwtScopeService.verifyScopesFromHeader(givenAuthHeader);

        // Then
        then(isValid).isFalse();
        BDDMockito.then(scopeDiscoveryService).should().getDiscoveredScopes();
    }

    private String givenJwtToken(String scopes) {
        try {
            var claimSet = new JWTClaimsSet.Builder()
                    .claim(SCOPE_CLAIM, scopes)
                    .subject("test-user")
                    .build();

            var header = new JWSHeader(JWSAlgorithm.HS256);
            var signedJwt = new SignedJWT(header, claimSet);

            var secret = "secret-secret-secret-secret-secret-secret".getBytes();
            var signer = new MACSigner(secret);
            signedJwt.sign(signer);

            return signedJwt.serialize();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate JWT token for test", e);
        }
    }

    private String givenJwtTokenWithClaimSet(JWTClaimsSet claimSet) {
        try {
            var header = new JWSHeader(JWSAlgorithm.HS256);
            var signedJwt = new SignedJWT(header, claimSet);

            var secret = "secret-secret-secret-secret-secret-secret".getBytes();
            var signer = new MACSigner(secret);
            signedJwt.sign(signer);

            return signedJwt.serialize();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate JWT token for test", e);
        }
    }
}
