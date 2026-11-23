package ua.edu.nupp.kpp.waste.server.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ua.edu.nupp.kpp.waste.server.dao.Repository;
import ua.edu.nupp.kpp.waste.server.dao.RequestRepository;
import ua.edu.nupp.kpp.waste.server.dao.TripRepository;
import ua.edu.nupp.kpp.waste.server.model.Request;
import ua.edu.nupp.kpp.waste.server.model.RequestStatus;
import ua.edu.nupp.kpp.waste.server.model.Site;
import ua.edu.nupp.kpp.waste.server.model.Trip;
import ua.edu.nupp.kpp.waste.server.model.Vehicle;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.*;

class WasteServiceTest {
    private SiteService sites;
    private VehicleService vehicles;
    private RequestService requests;
    private TripService trips;

    @BeforeEach
    void setUp() {
        Object writeLock = new Object();
        Memory<Site> siteRepo = new Memory<>((id, v) -> new Site(id, v.code(), v.name(), v.address(), v.capacityContainers()));
        Memory<Vehicle> vehicleRepo = new Memory<>((id, v) -> new Vehicle(id, v.plate(), v.model(), v.capacityKg(), v.active()));
        MemoryRequests requestRepo = new MemoryRequests();
        MemoryTrips tripRepo = new MemoryTrips();
        sites = new SiteService(siteRepo, writeLock);
        vehicles = new VehicleService(vehicleRepo, writeLock);
        requests = new RequestService(requestRepo, siteRepo, tripRepo, vehicleRepo, writeLock);
        trips = new TripService(tripRepo, requestRepo, vehicleRepo, writeLock);

        sites.create(new Site(null, "S-1", "Перший", "Адреса", 4));
        vehicles.create(new Vehicle(null, "DEMO-1", "Модель", 5000, true));
        vehicles.create(new Vehicle(null, "DEMO-2", "Резерв", 9000, false));
        requests.create(newRequest(null));
    }

    @Test void clientCannotSetComputedPriority() {
        Request request = requests.get(1);
        assertEquals(new BigDecimal("65.50"), request.priority());
        assertEquals(RequestStatus.CREATED, request.status());
    }

    @Test void ordinaryUpdateCannotJumpStatus() {
        ApiException error = assertThrows(ApiException.class, () -> requests.update(1, newRequest(RequestStatus.PLANNED)));
        assertEquals(409, error.status());
        assertEquals(RequestStatus.CREATED, requests.get(1).status());
    }

    @Test void ordinaryUpdateWithSameStatusRecomputesPriority() {
        Request request = requests.update(1, new Request(null, 1L, "Змінено", BigDecimal.ZERO, BigDecimal.ZERO, 0,
                new BigDecimal("99"), RequestStatus.CREATED));
        assertEquals("Змінено", request.title());
        assertEquals(new BigDecimal("0.00"), request.priority());
    }

    @Test void rejectsUnknownSiteBeforeSaving() {
        ApiException error = assertThrows(ApiException.class, () -> requests.create(new Request(null, 987L, "Заявка",
                BigDecimal.ZERO, BigDecimal.ZERO, 0, null, null)));
        assertEquals(400, error.status());
        assertEquals(1, requests.list().size());
    }

    @Test void rejectsNewRequestWithLaterStatus() {
        assertEquals(409, assertThrows(ApiException.class, () -> requests.create(newRequest(RequestStatus.COMPLETED))).status());
    }

    @Test void rejectsSkippedStatus() {
        assertEquals(409, assertThrows(ApiException.class, () -> requests.transition(1, RequestStatus.IN_PROGRESS)).status());
        assertEquals(RequestStatus.CREATED, requests.get(1).status());
    }

    @Test void planningRequiresTrip() {
        ApiException error = assertThrows(ApiException.class, () -> requests.transition(1, RequestStatus.PLANNED));
        assertEquals(409, error.status());
        assertTrue(error.getMessage().contains("виїзд"));
    }

    @Test void planningRejectsInactiveVehicle() {
        trips.create(trip(2));
        assertEquals(409, assertThrows(ApiException.class, () -> requests.transition(1, RequestStatus.PLANNED)).status());
        assertEquals(RequestStatus.CREATED, requests.get(1).status());
    }

    @Test void passesThroughTheWholeLifecycle() {
        trips.create(trip(1));
        assertEquals(RequestStatus.PLANNED, requests.transition(1, RequestStatus.PLANNED).status());
        assertEquals(RequestStatus.IN_PROGRESS, requests.transition(1, RequestStatus.IN_PROGRESS).status());
        assertEquals(RequestStatus.COMPLETED, requests.transition(1, RequestStatus.COMPLETED).status());
        assertEquals(409, assertThrows(ApiException.class, () -> requests.transition(1, RequestStatus.PLANNED)).status());
    }

    @Test void activeRequestProtectsTripAndCompletedRequestCanReleaseIt() {
        Trip trip = trips.create(trip(1));
        requests.transition(1, RequestStatus.PLANNED);
        assertEquals(409, assertThrows(ApiException.class, () -> trips.update(trip.id(), trip(1))).status());
        assertEquals(409, assertThrows(ApiException.class, () -> trips.delete(trip.id())).status());
        requests.transition(1, RequestStatus.IN_PROGRESS);
        assertEquals(409, assertThrows(ApiException.class, () -> trips.delete(trip.id())).status());
        requests.transition(1, RequestStatus.COMPLETED);
        trips.delete(trip.id());
        assertEquals(404, assertThrows(ApiException.class, () -> trips.get(trip.id())).status());
    }

    @Test void preventsTwoTripsForOneRequest() {
        trips.create(trip(1));
        assertEquals(409, assertThrows(ApiException.class, () -> trips.create(trip(1))).status());
        assertEquals(1, trips.list().size());
    }

    @Test void rejectsImpossibleTripDate() {
        ApiException error = assertThrows(ApiException.class,
                () -> trips.create(new Trip(null, 1L, 1L, "2026-02-30", "")));
        assertEquals(400, error.status());
    }

    @Test void supportsSiteCrudAndTrimsText() {
        Site created = sites.create(new Site(404L, "  S-2 ", " Друга ", " Адреса ", 5));
        assertEquals(2L, created.id());
        assertEquals("S-2", created.code());
        Site changed = sites.update(created.id(), new Site(null, "S-2", "Інша назва", "Інша адреса", 7));
        assertEquals(7, sites.get(changed.id()).capacityContainers());
        sites.delete(changed.id());
        assertEquals(404, assertThrows(ApiException.class, () -> sites.get(changed.id())).status());
    }

    @Test void rejectsSiteWithoutContainersAndVehicleWithoutAvailability() {
        assertEquals(400, assertThrows(ApiException.class, () -> sites.create(new Site(null, "S-2", "Друга", "Адреса", 0))).status());
        assertEquals(400, assertThrows(ApiException.class, () -> vehicles.create(new Vehicle(null, "D-1", "Модель", 1000, null))).status());
    }

    private static Request newRequest(RequestStatus status) {
        return new Request(null, 1L, "Заповнені контейнери",
                new BigDecimal("80"), new BigDecimal("60"), 36, BigDecimal.TEN, status);
    }

    private static Trip trip(long vehicleId) {
        return new Trip(null, 1L, vehicleId, "2026-11-24", "Забрати відходи");
    }

    private static class Memory<T> implements Repository<T> {
        protected final Map<Long, T> values = new LinkedHashMap<>();
        private final BiFunction<Long, T, T> withId;
        private long nextId;

        Memory(BiFunction<Long, T, T> withId) { this.withId = withId; }
        public List<T> findAll() { return List.copyOf(values.values()); }
        public Optional<T> findById(long id) { return Optional.ofNullable(values.get(id)); }
        public long insert(T value) {
            long id = ++nextId;
            values.put(id, withId.apply(id, value));
            return id;
        }
        public boolean update(long id, T value) {
            return values.replace(id, withId.apply(id, value)) != null;
        }
        public boolean delete(long id) { return values.remove(id) != null; }
    }

    private static final class MemoryRequests extends Memory<Request> implements RequestRepository {
        MemoryRequests() {
            super((id, v) -> new Request(id, v.siteId(), v.title(), v.fillLevel(), v.sanitaryRisk(), v.overdueHours(),
                    v.priority(), v.status()));
        }

        public boolean changeStatus(long id, RequestStatus previous, RequestStatus next) {
            Request v = values.get(id);
            if (v == null || v.status() != previous) return false;
            return update(id, new Request(id, v.siteId(), v.title(), v.fillLevel(), v.sanitaryRisk(),
                    v.overdueHours(), v.priority(), next));
        }
    }

    private static final class MemoryTrips extends Memory<Trip> implements TripRepository {
        MemoryTrips() {
            super((id, v) -> new Trip(id, v.requestId(), v.vehicleId(), v.scheduledDate(), v.notes()));
        }

        public Optional<Trip> findByRequest(long requestId) {
            return values.values().stream().filter(trip -> trip.requestId() == requestId).findFirst();
        }
    }
}
