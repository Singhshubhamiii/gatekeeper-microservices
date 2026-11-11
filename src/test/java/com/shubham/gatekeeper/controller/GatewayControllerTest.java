package com.shubham.gatekeeper.controller;

import com.shubham.gatekeeper.model.ApiRoute;
import com.shubham.gatekeeper.repository.ApiRouteRepository;
import com.shubham.gatekeeper.service.RateLimitEventPublisher;
import com.shubham.gatekeeper.service.RateLimitService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.client.RestClient;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class GatewayControllerTest {

    @Test
    void forwardsUpstreamErrorStatusHeadersAndBody() {
        ApiRouteRepository routes = mock(ApiRouteRepository.class);
        RateLimitService limiter = mock(RateLimitService.class);
        RateLimitEventPublisher events = mock(RateLimitEventPublisher.class);
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        when(routes.findByRouteKey("users"))
            .thenReturn(Optional.of(new ApiRoute("users", "http://upstream.test", 60)));
        when(limiter.allow(anyString(), eq(60))).thenReturn(true);

        server.expect(requestTo("http://upstream.test/api/check"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withStatus(HttpStatus.I_AM_A_TEAPOT)
                .header("X-Upstream-Result", "preserved")
                .body("upstream-error-body"));

        GatewayController controller = new GatewayController(
            routes, limiter, events, builder, "test-secret");

        MockHttpServletRequest request =
            new MockHttpServletRequest("GET", "/gateway/users/api/check");
        request.setRemoteAddr("127.0.0.1");

        var response = controller.forward("users", request, "test-secret", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.I_AM_A_TEAPOT);
        assertThat(response.getHeaders().getFirst("X-Upstream-Result")).isEqualTo("preserved");
        assertThat(new String(response.getBody())).isEqualTo("upstream-error-body");
        server.verify();
    }
}
