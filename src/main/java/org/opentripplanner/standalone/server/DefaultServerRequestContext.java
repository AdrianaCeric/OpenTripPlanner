package org.opentripplanner.standalone.server;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.Locale;
import javax.annotation.Nullable;
import org.opentripplanner.astar.spi.TraverseVisitor;
import org.opentripplanner.ext.vectortiles.VectorTilesResource;
import org.opentripplanner.inspector.raster.TileRendererManager;
import org.opentripplanner.raptor.api.request.RaptorTuningParameters;
import org.opentripplanner.raptor.configure.RaptorConfig;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.TransitTuningParameters;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.TripSchedule;
import org.opentripplanner.routing.api.RoutingService;
import org.opentripplanner.routing.api.request.RouteRequest;
import org.opentripplanner.routing.graph.Graph;
import org.opentripplanner.routing.service.DefaultRoutingService;
import org.opentripplanner.service.vehiclepositions.VehiclePositionService;
import org.opentripplanner.service.vehiclerental.VehicleRentalService;
import org.opentripplanner.service.worldenvelope.WorldEnvelopeService;
import org.opentripplanner.standalone.api.HttpRequestScoped;
import org.opentripplanner.standalone.api.OtpServerRequestContext;
import org.opentripplanner.standalone.config.routerconfig.TransitRoutingConfig;
import org.opentripplanner.standalone.config.sandbox.FlexConfig;
import org.opentripplanner.standalone.configure.RequestLoggerFactory;
import org.opentripplanner.transit.service.TransitService;
import org.slf4j.Logger;

@HttpRequestScoped
public class DefaultServerRequestContext implements OtpServerRequestContext {

  private RouteRequest routeRequest = null;
  private final Graph graph;
  private final TransitService transitService;
  private final TransitRoutingConfig transitRoutingConfig;
  private final RouteRequest routeRequestDefaults;
  private final MeterRegistry meterRegistry;
  private final RaptorConfig<TripSchedule> raptorConfig;
  private final Logger requestLogger;
  private final TileRendererManager tileRendererManager;
  private final VectorTilesResource.LayersParameters<VectorTilesResource.LayerType> vectorTileLayers;
  private final FlexConfig flexConfig;
  private final TraverseVisitor traverseVisitor;
  private final WorldEnvelopeService worldEnvelopeService;
  private final VehiclePositionService vehiclePositionService;
  private final VehicleRentalService vehicleRentalService;

  /**
   * Make sure all mutable components are copied/cloned before calling this constructor.
   */
  private DefaultServerRequestContext(
    Graph graph,
    TransitService transitService,
    TransitRoutingConfig transitRoutingConfig,
    RouteRequest routeRequestDefaults,
    MeterRegistry meterRegistry,
    RaptorConfig<TripSchedule> raptorConfig,
    Logger requestLogger,
    TileRendererManager tileRendererManager,
    VectorTilesResource.LayersParameters<VectorTilesResource.LayerType> vectorTileLayers,
    WorldEnvelopeService worldEnvelopeService,
    VehiclePositionService vehiclePositionService,
    VehicleRentalService vehicleRentalService,
    TraverseVisitor traverseVisitor,
    FlexConfig flexConfig
  ) {
    this.graph = graph;
    this.transitService = transitService;
    this.transitRoutingConfig = transitRoutingConfig;
    this.meterRegistry = meterRegistry;
    this.raptorConfig = raptorConfig;
    this.requestLogger = requestLogger;
    this.tileRendererManager = tileRendererManager;
    this.vectorTileLayers = vectorTileLayers;
    this.vehicleRentalService = vehicleRentalService;
    this.flexConfig = flexConfig;
    this.traverseVisitor = traverseVisitor;
    this.routeRequestDefaults = routeRequestDefaults;
    this.worldEnvelopeService = worldEnvelopeService;
    this.vehiclePositionService = vehiclePositionService;
  }

  /**
   * Create a server context valid for one http request only!
   */
  public static DefaultServerRequestContext create(
    TransitRoutingConfig transitRoutingConfig,
    RouteRequest routeRequestDefaults,
    RaptorConfig<TripSchedule> raptorConfig,
    Graph graph,
    TransitService transitService,
    MeterRegistry meterRegistry,
    VectorTilesResource.LayersParameters<VectorTilesResource.LayerType> vectorTileLayers,
    WorldEnvelopeService worldEnvelopeService,
    VehiclePositionService vehiclePositionService,
    VehicleRentalService vehicleRentalService,
    FlexConfig flexConfig,
    @Nullable TraverseVisitor traverseVisitor,
    @Nullable String requestLogFile
  ) {
    return new DefaultServerRequestContext(
      graph,
      transitService,
      transitRoutingConfig,
      routeRequestDefaults,
      meterRegistry,
      raptorConfig,
      RequestLoggerFactory.createLogger(requestLogFile),
      new TileRendererManager(graph, routeRequestDefaults.preferences()),
      vectorTileLayers,
      worldEnvelopeService,
      vehiclePositionService,
      vehicleRentalService,
      traverseVisitor,
      flexConfig
    );
  }

  @Override
  public RouteRequest defaultRouteRequest() {
    // Lazy initialize request-scoped request to avoid doing this when not needed
    if (routeRequest == null) {
      routeRequest = routeRequestDefaults.copyWithDateTimeNow();
    }
    return routeRequest;
  }

  /**
   * Return the default routing request locale(without cloning the request).
   */
  @Override
  public Locale defaultLocale() {
    return routeRequestDefaults.locale();
  }

  @Override
  public RaptorConfig<TripSchedule> raptorConfig() {
    return raptorConfig;
  }

  @Override
  public Graph graph() {
    return graph;
  }

  @Override
  public TransitService transitService() {
    return transitService;
  }

  @Override
  public RoutingService routingService() {
    return new DefaultRoutingService(this);
  }

  @Override
  public WorldEnvelopeService worldEnvelopeService() {
    return worldEnvelopeService;
  }

  @Override
  public VehiclePositionService vehiclePositionService() {
    return vehiclePositionService;
  }

  @Override
  public VehicleRentalService vehicleRentalService() {
    return vehicleRentalService;
  }

  @Override
  public TransitTuningParameters transitTuningParameters() {
    return transitRoutingConfig;
  }

  @Override
  public RaptorTuningParameters raptorTuningParameters() {
    return transitRoutingConfig;
  }

  @Override
  public MeterRegistry meterRegistry() {
    return meterRegistry;
  }

  @Override
  public Logger requestLogger() {
    return requestLogger;
  }

  @Override
  public TileRendererManager tileRendererManager() {
    return tileRendererManager;
  }

  @Override
  public TraverseVisitor traverseVisitor() {
    return traverseVisitor;
  }

  @Override
  public FlexConfig flexConfig() {
    return flexConfig;
  }

  @Override
  public VectorTilesResource.LayersParameters<VectorTilesResource.LayerType> vectorTileLayers() {
    return vectorTileLayers;
  }
}
