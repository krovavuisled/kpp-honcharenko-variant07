package ua.edu.nupp.kpp.waste.server.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;
import ua.edu.nupp.kpp.waste.server.model.RequestStatus;
import ua.edu.nupp.kpp.waste.server.model.Site;
import ua.edu.nupp.kpp.waste.server.service.ApiException;

import static org.junit.jupiter.api.Assertions.*;

class JsonContractTest {
    @Test void decodesUkrainianSiteJson() throws Exception {
        Site site = CrudServlet.JSON.readValue("""
                {"code":"SITE-10","name":"Західний","address":"вул. Умовна, 6","capacityContainers":5}
                """, Site.class);
        assertEquals("Західний", site.name());
        assertEquals(5, site.capacityContainers());
        assertNull(site.id());
    }

    @Test void rejectsFractionalContainerCount() {
        assertThrows(JsonProcessingException.class,
                () -> CrudServlet.JSON.readValue("{\"capacityContainers\":4.9}", Site.class));
    }

    @Test void rejectsNumericStatusOrdinal() {
        assertThrows(JsonProcessingException.class,
                () -> CrudServlet.JSON.readValue("{\"status\":1}", RequestServlet.StatusInput.class));
    }

    @Test void acceptsTextualStatus() throws Exception {
        var input = CrudServlet.JSON.readValue("{\"status\":\"PLANNED\"}", RequestServlet.StatusInput.class);
        assertEquals(RequestStatus.PLANNED, input.status());
    }

    @Test void rejectsNonItemPath() {
        assertEquals(404, assertThrows(ApiException.class, () -> CrudServlet.parseId("/7/extra")).status());
        assertEquals(7L, CrudServlet.parseId("/7"));
    }

    @Test void rejectsIdsOutsideLongRange() {
        assertEquals(400, assertThrows(ApiException.class,
                () -> CrudServlet.parseId("/9223372036854775808")).status());
    }
}
