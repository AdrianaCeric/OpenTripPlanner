package org.opentripplanner.ext.legacygraphqlapi.mapping;

import graphql.schema.DataFetchingEnvironment;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import org.opentripplanner.api.common.LocationStringParser;
import org.opentripplanner.api.parameter.QualifiedMode;
import org.opentripplanner.api.parameter.QualifiedModeSet;
import org.opentripplanner.ext.legacygraphqlapi.LegacyGraphQLRequestContext;
import org.opentripplanner.framework.graphql.GraphQLUtils;
import org.opentripplanner.model.GenericLocation;
import org.opentripplanner.routing.api.request.RouteRequest;
import org.opentripplanner.routing.api.request.framework.RequestFunctions;
import org.opentripplanner.routing.api.request.request.filter.SelectRequest;
import org.opentripplanner.routing.api.request.request.filter.TransitFilterRequest;
import org.opentripplanner.routing.api.request.request.filter.VehicleParkingFilter;
import org.opentripplanner.routing.api.request.request.filter.VehicleParkingFilter.TagsFilter;
import org.opentripplanner.routing.api.request.request.filter.VehicleParkingFilterRequest;
import org.opentripplanner.routing.core.BicycleOptimizeType;
import org.opentripplanner.transit.model.basic.MainAndSubMode;
import org.opentripplanner.transit.model.basic.TransitMode;

public class RouteRequestMapper {

  @Nonnull
  public static RouteRequest toRouteRequest(
    DataFetchingEnvironment environment,
    LegacyGraphQLRequestContext context
  ) {
    RouteRequest request = context.defaultRouteRequest();

    CallerWithEnvironment callWith = new CallerWithEnvironment(environment);

    callWith.argument(
      "fromPlace",
      (String from) -> request.setFrom(LocationStringParser.fromOldStyleString(from))
    );
    callWith.argument(
      "toPlace",
      (String to) -> request.setTo(LocationStringParser.fromOldStyleString(to))
    );

    callWith.argument("from", (Map<String, Object> v) -> request.setFrom(toGenericLocation(v)));
    callWith.argument("to", (Map<String, Object> v) -> request.setTo(toGenericLocation(v)));

    request.setDateTime(
      environment.getArgument("date"),
      environment.getArgument("time"),
      context.transitService().getTimeZone()
    );

    callWith.argument("wheelchair", request::setWheelchair);
    callWith.argument("numItineraries", request::setNumItineraries);
    callWith.argument("searchWindow", (Long m) -> request.setSearchWindow(Duration.ofSeconds(m)));
    callWith.argument("pageCursor", request::setPageCursorFromEncoded);

    request.withPreferences(preferences -> {
      preferences.withBike(bike -> {
        callWith.argument("bikeReluctance", bike::withReluctance);
        callWith.argument("bikeWalkingReluctance", bike::withWalkingReluctance);
        callWith.argument("bikeWalkingSpeed", bike::withWalkingSpeed);
        callWith.argument("bikeSpeed", bike::withSpeed);
        callWith.argument("bikeSwitchTime", bike::withSwitchTime);
        callWith.argument("bikeSwitchCost", bike::withSwitchCost);
        callWith.argument("bikeBoardCost", bike::withBoardCost);

        if (environment.getArgument("optimize") != null) {
          bike.withOptimizeType(BicycleOptimizeType.valueOf(environment.getArgument("optimize")));
        }
        if (bike.optimizeType() == BicycleOptimizeType.TRIANGLE) {
          bike.withOptimizeTriangle(triangle -> {
            callWith.argument("triangle.timeFactor", triangle::withTime);
            callWith.argument("triangle.slopeFactor", triangle::withSlope);
            callWith.argument("triangle.safetyFactor", triangle::withSafety);
          });
        }
      });

      preferences.withCar(car -> callWith.argument("carReluctance", car::withReluctance));

      preferences.withWalk(b -> {
        callWith.argument("walkReluctance", b::withReluctance);
        callWith.argument("walkSpeed", b::withSpeed);
        callWith.argument("walkBoardCost", b::withBoardCost);
        callWith.argument("walkSafetyFactor", b::withSafetyFactor);
      });
      preferences.withRental(rental -> {
        callWith.argument(
          "keepingRentedBicycleAtDestinationCost",
          rental::withArrivingInRentalVehicleAtDestinationCost
        );
        rental.withUseAvailabilityInformation(request.isTripPlannedForNow());
      });
      callWith.argument(
        "debugItineraryFilter",
        (Boolean v) -> preferences.withItineraryFilter(it -> it.withDebug(v))
      );
      preferences.withTransit(tr -> {
        callWith.argument("boardSlack", tr::withDefaultBoardSlackSec);
        callWith.argument("alightSlack", tr::withDefaultAlightSlackSec);
        callWith.argument(
          "preferred.otherThanPreferredRoutesPenalty",
          tr::setOtherThanPreferredRoutesPenalty
        );
        // This is deprecated, if both are set, the proper one will override this
        callWith.argument(
          "unpreferred.useUnpreferredRoutesPenalty",
          (Integer v) ->
            tr.setUnpreferredCostString(
              RequestFunctions.serialize(RequestFunctions.createLinearFunction(v, 0.0))
            )
        );
        callWith.argument("unpreferred.unpreferredCost", tr::setUnpreferredCostString);
        callWith.argument("ignoreRealtimeUpdates", tr::setIgnoreRealtimeUpdates);
        callWith.argument(
          "modeWeight",
          (Map<String, Object> modeWeights) ->
            tr.setReluctanceForMode(
              modeWeights
                .entrySet()
                .stream()
                .collect(
                  Collectors.toMap(e -> TransitMode.valueOf(e.getKey()), e -> (Double) e.getValue())
                )
            )
        );
      });
      preferences.withTransfer(tx -> {
        callWith.argument("transferPenalty", tx::withCost);
        callWith.argument("minTransferTime", tx::withSlack);
        callWith.argument("waitReluctance", tx::withWaitReluctance);
        callWith.argument("maxTransfers", tx::withMaxTransfers);
        callWith.argument("nonpreferredTransferPenalty", tx::withNonpreferredCost);
      });
    });

    callWith.argument(
      "allowKeepingRentedBicycleAtDestination",
      request.journey().rental()::setAllowArrivingInRentedVehicleAtDestination
    );
    callWith.argument("arriveBy", request::setArriveBy);

    callWith.argument(
      "preferred.routes",
      request.journey().transit()::setPreferredRoutesFromString
    );

    callWith.argument(
      "preferred.agencies",
      request.journey().transit()::setPreferredAgenciesFromString
    );
    callWith.argument(
      "unpreferred.routes",
      request.journey().transit()::setUnpreferredRoutesFromString
    );
    callWith.argument(
      "unpreferred.agencies",
      request.journey().transit()::setUnpreferredAgenciesFromString
    );

    var transitDisabled = false;
    if (hasArgument(environment, "banned") || hasArgument(environment, "transportModes")) {
      var filterRequestBuilder = TransitFilterRequest.of();

      if (hasArgument(environment, "banned.routes")) {
        callWith.argument(
          "banned.routes",
          s ->
            filterRequestBuilder.addNot(SelectRequest.of().withRoutesFromString((String) s).build())
        );
      }

      if (hasArgument(environment, "banned.agencies")) {
        callWith.argument(
          "banned.agencies",
          s ->
            filterRequestBuilder.addNot(
              SelectRequest.of().withAgenciesFromString((String) s).build()
            )
        );
      }

      callWith.argument("banned.trips", request.journey().transit()::setBannedTripsFromString);

      if (hasArgument(environment, "transportModes")) {
        QualifiedModeSet modes = new QualifiedModeSet("WALK");

        modes.qModes =
          environment
            .<List<Map<String, String>>>getArgument("transportModes")
            .stream()
            .map(transportMode ->
              new QualifiedMode(
                transportMode.get("mode") +
                (transportMode.get("qualifier") == null ? "" : "_" + transportMode.get("qualifier"))
              )
            )
            .collect(Collectors.toSet());

        var requestModes = modes.getRequestModes();
        request.journey().access().setMode(requestModes.accessMode);
        request.journey().egress().setMode(requestModes.egressMode);
        request.journey().direct().setMode(requestModes.directMode);
        request.journey().transfer().setMode(requestModes.transferMode);

        var tModes = modes.getTransitModes().stream().map(MainAndSubMode::new).toList();
        if (tModes.isEmpty()) {
          transitDisabled = true;
        } else {
          filterRequestBuilder.addSelect(SelectRequest.of().withTransportModes(tModes).build());
        }
      }

      if (transitDisabled) {
        request.journey().transit().disable();
      } else {
        request.journey().transit().setFilters(List.of(filterRequestBuilder.build()));
      }
    }

    if (hasArgument(environment, "allowedTicketTypes")) {
      // request.allowedFares = new HashSet();
      // ((List<String>)environment.getArgument("allowedTicketTypes")).forEach(ticketType -> request.allowedFares.add(ticketType.replaceFirst("_", ":")));
    }

    var vehicleRental = request.journey().rental();

    // Deprecated, the next one will override this, if both are set
    callWith.argument(
      "allowedBikeRentalNetworks",
      (Collection<String> v) -> vehicleRental.setAllowedNetworks(new HashSet<>(v))
    );
    callWith.argument(
      "allowedVehicleRentalNetworks",
      (Collection<String> v) -> vehicleRental.setAllowedNetworks(new HashSet<>(v))
    );
    callWith.argument(
      "bannedVehicleRentalNetworks",
      (Collection<String> v) -> vehicleRental.setBannedNetworks(new HashSet<>(v))
    );

    var parking = request.journey().parking();
    callWith.argument("parking.unpreferredCost", parking::setUnpreferredCost);

    callWith.argument(
      "parking.filters",
      (Collection<Map<String, Object>> filters) -> parking.setFilter(parseFilters(filters))
    );

    callWith.argument(
      "parking.preferred",
      (Collection<Map<String, Object>> filters) -> parking.setPreferred(parseFilters(filters))
    );

    callWith.argument(
      "locale",
      (String v) -> request.setLocale(GraphQLUtils.getLocale(environment, v))
    );
    return request;
  }

  private static VehicleParkingFilterRequest parseFilters(Collection<Map<String, Object>> filters) {
    var not = parseFilters(filters, "not");
    var select = parseFilters(filters, "select");
    return new VehicleParkingFilterRequest(not, select);
  }

  @Nonnull
  private static Set<VehicleParkingFilter> parseFilters(
    Collection<Map<String, Object>> filters,
    String key
  ) {
    return filters
      .stream()
      .flatMap(f ->
        parseOperation((Collection<Map<String, Collection<String>>>) f.getOrDefault(key, List.of()))
      )
      .collect(Collectors.toSet());
  }

  private static Stream<VehicleParkingFilter> parseOperation(
    Collection<Map<String, Collection<String>>> map
  ) {
    return map
      .stream()
      .map(f -> {
        var tags = f.getOrDefault("tags", List.of());
        return new TagsFilter(Set.copyOf(tags));
      });
  }

  private static <T> boolean hasArgument(Map<String, T> m, String name) {
    return m.containsKey(name) && m.get(name) != null;
  }

  private static boolean hasArgument(DataFetchingEnvironment environment, String name) {
    return environment.containsArgument(name) && environment.getArgument(name) != null;
  }

  private static GenericLocation toGenericLocation(Map<String, Object> m) {
    double lat = (double) m.get("lat");
    double lng = (double) m.get("lon");
    String address = (String) m.get("address");

    if (address != null) {
      return new GenericLocation(address, null, lat, lng);
    }

    return new GenericLocation(lat, lng);
  }

  private static class CallerWithEnvironment {

    private final DataFetchingEnvironment environment;

    public CallerWithEnvironment(DataFetchingEnvironment e) {
      this.environment = e;
    }

    private static <T> void call(
      DataFetchingEnvironment environment,
      String name,
      Consumer<T> consumer
    ) {
      if (!name.contains(".")) {
        if (hasArgument(environment, name)) {
          consumer.accept(environment.getArgument(name));
        }
      } else {
        String[] parts = name.split("\\.");
        if (hasArgument(environment, parts[0])) {
          Map<String, T> nm = environment.getArgument(parts[0]);
          call(nm, String.join(".", Arrays.copyOfRange(parts, 1, parts.length)), consumer);
        }
      }
    }

    private static <T> void call(Map<String, T> m, String name, Consumer<T> consumer) {
      if (!name.contains(".")) {
        if (hasArgument(m, name)) {
          T v = m.get(name);
          consumer.accept(v);
        }
      } else {
        String[] parts = name.split("\\.");
        if (hasArgument(m, parts[0])) {
          Map<String, T> nm = (Map<String, T>) m.get(parts[0]);
          call(nm, String.join(".", Arrays.copyOfRange(parts, 1, parts.length)), consumer);
        }
      }
    }

    private <T> void argument(String name, Consumer<T> consumer) {
      call(environment, name, consumer);
    }
  }
}
