package react.api;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Controller;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Controller
@Profile("develop")
public class FrontendController {
  private static final Logger LOGGER = LoggerFactory.getLogger(FrontendController.class);

  private static final String SCHEMA = "http";
  private static final String SERVER = "localhost";
  private static final int PORT = 3000;

  private final RestTemplate restTemplate;

  FrontendController(RestTemplate restTemplate) {
    this.restTemplate = restTemplate;
  }

  @Autowired
  ResourceLoader resourceLoader;

  // only needed for legacy integration
  @RequestMapping(value = "/frontend/static/js/main.js", produces = "text/javascript")
  public ResponseEntity<?> mainJs() throws IOException {
    Resource resource = resourceLoader.getResource("classpath:/frontend/index.html");
    try (InputStream is = resource.getInputStream()) {
      try (InputStreamReader isr = new InputStreamReader(is)) {
        try (BufferedReader br = new BufferedReader(isr)) {
          Pattern p = Pattern.compile(".*(/static/js/main.[0-9,a-f]+.js).*");
          for (String line = br.readLine(); line != null; line = br.readLine()) {
            Matcher matcher = p.matcher(line);
            if (matcher.matches()) {
              String match = matcher.group(1);
              try (InputStream js = resourceLoader.getResource("classpath:/frontend" + match).getInputStream()) {
                String content = StreamUtils.copyToString(js, Charset.defaultCharset());
                return ResponseEntity.ok(content);
              }
            }
          }
        }
      }
    }
    return ResponseEntity.notFound().build();
  }

  // "/" hinzufügen, wenn nicht im legacy Modus
  @RequestMapping({  "/frontend/**",
      "/static/**", "/fonts/**", "/logo*.png", "/favicon.ico", "/manifest.json", "/*.hot-update.*"})
  public ResponseEntity<?> mirrorRest(
      @RequestBody(required = false) String body, HttpMethod method, HttpServletRequest request
  ) throws URISyntaxException {
    URI uri = new URI(SCHEMA, null, SERVER, PORT, request.getRequestURI(), request.getQueryString(),
        null);
    LOGGER.debug("Mirror http request for {} to {}://{}:{}",
        request.getRequestURI(), uri.getScheme(), uri.getHost(), uri.getPort());

    return restTemplate.exchange(uri, method,
        body != null
        ? new HttpEntity<>(body)
        : null, byte[].class);
  }

  /**
   * A proxy for websocket connections, which relays messages unchanged.
   * <p>
   * It is itself also a WebSocketHandler, which handles new incoming websocket connection from clients.
   */
  public static class WebSocketMirror extends AbstractWebSocketHandler {

    /**
     * Collection of path which should be proxied to upstream
     */
    public static final Collection<String> PATHS = Set.of("/sockjs-node", "/ws");

    private static final Logger LOGGER = LoggerFactory.getLogger(WebSocketMirror.class);

    /**
     * The schema to use to connect to upstream
     */
    private static final String SCHEMA = "ws";

    /**
     * Map from client WebSocketSession ID to the associated upstream WebSocketSession
     **/
    private final Map<String, WebSocketSession> upstreamHandlers = new ConcurrentHashMap<>();

    /**
     * For the given client WebSocketSession, return the associated upstream WebSocketSession. If none exists yet, it
     * will be created.
     *
     * @param clientSession The client session to create the upstream for
     * @return the connected and ready-to-use upstream WebSocketSession
     */
    private WebSocketSession getUpstream(WebSocketSession clientSession) {
      return this.upstreamHandlers.computeIfAbsent(
          clientSession.getId(),
          k -> {
            try {
              return new StandardWebSocketClient()
                  .doHandshake(
                      new UpstreamHandler(clientSession),
                      new WebSocketHttpHeaders(),
                      new URI(SCHEMA, null, SERVER, PORT,
                          clientSession.getUri().getPath(), null, null))
                  .get(1000, TimeUnit.MILLISECONDS);
            } catch (URISyntaxException | ExecutionException | TimeoutException e) {
              throw new IllegalStateException("Error establishing WebSocket connection to upstream", e);
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              throw new IllegalStateException("Interrupted while waiting", e);
            }
          }
      );
    }

    /**
     * When a new connection is established, creates a new upstream handler and establish the connection to upstream.
     *
     * @param clientSession client WebSocketSession
     */
    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession clientSession) {
      LOGGER.debug("Mirror WebSocket connection for {} to {}://{}:{}",
          clientSession.getUri().getPath(), SCHEMA, SERVER, PORT);
      getUpstream(clientSession);
    }

    /**
     * When receiving a new message from the client, pass it on as-is to upstream.
     *
     * @param clientSession client WebSocketSession
     */
    @Override
    public void handleMessage(
        @NonNull WebSocketSession clientSession,
        @NonNull WebSocketMessage<?> webSocketMessage
    )
        throws
        IOException {
      LOGGER.debug("WebSocket message from client to upstream: {}", webSocketMessage);
      getUpstream(clientSession).sendMessage(webSocketMessage);
    }

    /**
     * When the connection from the client is closed, also close the connection to upstream.
     *
     * @param clientSession client WebSocketSession
     */
    @Override
    public void afterConnectionClosed(
        @NonNull WebSocketSession clientSession,
        @NonNull CloseStatus status
    ) throws Exception {
      LOGGER.debug("WebSocket client connection closed, Reason {}", status);
      getUpstream(clientSession).close();
    }

    /**
     * Handler for the WebSocket connection to upstream, which passes the messages from upstream on to the client.
     */
    private static class UpstreamHandler extends AbstractWebSocketHandler {

      /**
       * The client WebSocketSession to pass messages on to
       */
      private final WebSocketSession clientSession;

      public UpstreamHandler(WebSocketSession clientSession) {
        this.clientSession = clientSession;
      }

      /**
       * When receiving a new message from upstream, pass it on as-is to the client.
       *
       * @param upstreamSession upstream WebSocketSession
       */
      @Override
      public void handleMessage(
          @NonNull WebSocketSession upstreamSession,
          @NonNull WebSocketMessage<?> webSocketMessage
      )
          throws IOException {
        LOGGER.debug("WebSocket message from upstream to client: {}", webSocketMessage);
        this.clientSession.sendMessage(webSocketMessage);
      }
    }
  }

}
