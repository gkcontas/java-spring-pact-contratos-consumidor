package com.gkcontas.catalog.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * Test-only filter that rewrites the provider's responses according to the
 * {@link ResponseMutation} named in a system property. With no property set it does
 * nothing, so it is safe in every test context.
 */
@TestComponent
public class ResponseMutationFilter extends OncePerRequestFilter {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        ResponseMutation mutation = ResponseMutation.active();
        if (mutation == null) {
            chain.doFilter(request, response);
            return;
        }
        ContentCachingResponseWrapper wrapper = new ContentCachingResponseWrapper(response);
        chain.doFilter(request, wrapper);

        byte[] body = wrapper.getContentAsByteArray();
        int status = wrapper.getStatus();
        ResponseMutation.Endpoint endpoint =
                ResponseMutation.Endpoint.of(request.getMethod(), request.getRequestURI(), status);
        if (endpoint == mutation.endpoint() && body.length > 0) {
            JsonNode json = mapper.readTree(body);
            if (json instanceof ObjectNode object) {
                status = mutation.apply(object, status);
                body = mapper.writeValueAsBytes(object);
            }
        }
        response.setStatus(status);
        response.setContentLength(body.length);
        response.getOutputStream().write(body);
    }
}
