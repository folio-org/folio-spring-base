package org.folio.spring.filter;

import org.apache.commons.lang3.StringUtils;
import org.folio.spring.integration.XOkapiHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.GenericFilterBean;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;

@Component("defaultTenantOkapiHeaderValidationFilter")
@ConditionalOnMissingBean(name = "tenantOkapiHeaderValidationFilter")
@ConditionalOnProperty(prefix = "folio.tenant.validation", name = "enabled", matchIfMissing = true)
public class TenantOkapiHeaderValidationFilter extends GenericFilterBean {

  public static final String ERROR_MSG = XOkapiHeaders.TENANT + " header must be provided";

  @Value("${header.validation.x-okapi-tenant.exclude.base-paths:/admin}")
  private String[] excludeBasePaths;

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
    HttpServletRequest req = (HttpServletRequest) request;
    if (StringUtils.isNotBlank(req.getHeader(XOkapiHeaders.TENANT)) || Arrays.stream(excludeBasePaths)
      .anyMatch(req.getRequestURI()::startsWith)) {
      chain.doFilter(request, response);
    } else {
      var res = ((HttpServletResponse) response);
      res.setContentType("text/plain");
      res.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      res.getWriter().println(XOkapiHeaders.TENANT + " header must be provided");
    }
  }
}
