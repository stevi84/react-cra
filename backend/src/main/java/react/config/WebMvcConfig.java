package react.config;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.AbstractResourceResolver;
import org.springframework.web.servlet.resource.ResourceResolverChain;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Configuration
class WebMvcConfig implements WebMvcConfigurer {
  public static final String FORWARD_INDEX_HTML = "forward:/index.html";
  public static final String FORWARD_FRONTEND_INDEX_HTML = "forward:/frontend/index.html";

  private static final Logger LOG = LoggerFactory.getLogger(WebMvcConfig.class);

  // only needed for legacy integration
  private static class SimpleResolver extends AbstractResourceResolver {

    @Override
    protected Resource resolveResourceInternal(
        HttpServletRequest request,
        String requestPath,
        List<? extends Resource> locations,
        ResourceResolverChain chain
    ) {
      return locations.get(0);
    }

    @Override
    protected String resolveUrlPathInternal(
        String resourceUrlPath,
        List<? extends Resource> locations,
        ResourceResolverChain chain
    ) {
      return null;
    }
  }

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    ClassLoader classLoader = getClass().getClassLoader();
    registry.addResourceHandler("/**")
        .addResourceLocations("classpath:/legacy/")
        .addResourceLocations("classpath:/frontend/")
        .setCacheControl(CacheControl.maxAge(2, TimeUnit.HOURS))
        .setUseLastModified(true);
    registry.addResourceHandler("/frontend/**")
        .addResourceLocations("classpath:/frontend/")
        .setCacheControl(CacheControl.maxAge(2, TimeUnit.HOURS))
        .setUseLastModified(true);
    // make React application's code available to legacy application
    // only needed for legacy integration
    try (InputStream is = classLoader.getResourceAsStream("frontend/index.html")) {
      try (InputStreamReader isr = new InputStreamReader(is)) {
        try (BufferedReader br = new BufferedReader(isr)) {
          Pattern p = Pattern.compile(".*(/static/js/main.[0-9,a-f]+.js).*");
          for (String line = br.readLine(); line != null; line = br.readLine()) {
            Matcher matcher = p.matcher(line);
            if (matcher.matches()) {
              String match = matcher.group(1);
              registry.addResourceHandler("/frontend/static/js/main.js")
                  .addResourceLocations("classpath:/frontend" + match)
                  .setCacheControl(CacheControl.maxAge(2, TimeUnit.HOURS))
                  .setUseLastModified(true)
                  .resourceChain(true)
                  .addResolver(new SimpleResolver());
            }
          }
        }
      }
    } catch (IOException ioe) {
      LOG.warn("could not connect frontend to legacy", ioe);
    }
  }

  @Override
  public void addViewControllers(ViewControllerRegistry registry) {
    // list them separately - /api and /static might otherwise get covered up
    registry.addViewController("/frontend/").setViewName(FORWARD_FRONTEND_INDEX_HTML);
    registry.addViewController("/").setViewName(FORWARD_INDEX_HTML);
  }

  @Bean
  RestTemplate restTemplate() {
    return new RestTemplateBuilder().build();
  }
}
