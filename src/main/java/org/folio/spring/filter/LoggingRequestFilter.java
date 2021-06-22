package org.folio.spring.filter;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.time.Instant;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.GenericFilterBean;
import org.springframework.web.util.ContentCachingResponseWrapper;

import org.folio.spring.integration.XOkapiHeaders;
import org.folio.spring.utils.MultiReadHttpServletRequestWrapper;

@Log4j2
@Component
@ConditionalOnProperty(
  prefix = "folio.logging.request",
  name = "enabled",
  havingValue = "true"
)
public class LoggingRequestFilter extends GenericFilterBean {

  private static final String START_TIME_ATTR = "startTime";

  private final Level level;

  public LoggingRequestFilter(@Value("${folio.logging.request.level: FULL}") Level level) {
    this.level = level;
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
    throws IOException, ServletException {
    if (log.isInfoEnabled()) {
      filterWrapped(wrapRequest(request), wrapResponse(response), chain);
    } else {
      chain.doFilter(request, response);
    }
  }

  private void filterWrapped(MultiReadHttpServletRequestWrapper request, ContentCachingResponseWrapper response,
                             FilterChain chain) throws ServletException, IOException {
    filterBefore(request);
    chain.doFilter(request, response);
    filterAfter(request, response);
    response.copyBodyToResponse();
  }

  private void filterBefore(MultiReadHttpServletRequestWrapper request) throws IOException {
    request.setAttribute(START_TIME_ATTR, Instant.now().toEpochMilli());

    var requestId = getRequestId(request);

    log.info("[{}] ---> {} {} {}",
      requestId,
      request.getMethod(),
      request.getRequestURI(),
      request.getQueryString()
    );

    if (level.ordinal() >= Level.HEADERS.ordinal()) {
      var headerNames = request.getHeaderNames();
      while (headerNames.hasMoreElements()) {
        var headerName = headerNames.nextElement();
        log.info("[{}] {}: {}", requestId, headerName, request.getHeader(headerName));
      }
    }

    if (level.ordinal() == Level.FULL.ordinal()) {
      var body = IOUtils.toString(request.getInputStream(), request.getCharacterEncoding());
      log.info("[{}] Body: {}", requestId, body);
    }

    log.info("[{}] ---> END HTTP", requestId);
  }

  private void filterAfter(MultiReadHttpServletRequestWrapper request, ContentCachingResponseWrapper response)
    throws UnsupportedEncodingException {
    var startTime = (long) request.getAttribute(START_TIME_ATTR);
    var requestId = getRequestId(request);

    log.info("[{}] <--- {} in {}ms",
      requestId,
      response.getStatus(),
      (Instant.now().toEpochMilli() - startTime)
    );

    if (level.ordinal() == Level.FULL.ordinal()) {
      var body = new String(response.getContentAsByteArray(), response.getCharacterEncoding());
      log.info("[{}] Body: {}", requestId, body);
    }

    log.info("[{}] <--- END HTTP", requestId);
  }

  private ContentCachingResponseWrapper wrapResponse(ServletResponse response) {
    return new ContentCachingResponseWrapper((HttpServletResponse) response);
  }

  private MultiReadHttpServletRequestWrapper wrapRequest(ServletRequest request) {
    return new MultiReadHttpServletRequestWrapper((HttpServletRequest) request);
  }

  private String getRequestId(MultiReadHttpServletRequestWrapper request) {
    return request.getHeader(XOkapiHeaders.REQUEST_ID);
  }

  enum Level {
    NONE,
    BASIC,
    HEADERS,
    FULL
  }

}
