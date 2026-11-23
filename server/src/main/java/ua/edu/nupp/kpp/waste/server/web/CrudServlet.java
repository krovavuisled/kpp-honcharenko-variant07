package ua.edu.nupp.kpp.waste.server.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import ua.edu.nupp.kpp.waste.server.dao.DataAccessException;
import ua.edu.nupp.kpp.waste.server.service.ApiException;
import ua.edu.nupp.kpp.waste.server.service.CrudService;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

abstract class CrudServlet<T> extends HttpServlet {
    static final ObjectMapper JSON = JsonMapper.builder()
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT, DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
            .build();

    private static final Logger LOG = Logger.getLogger(CrudServlet.class.getName());
    private final CrudService<T> service;
    private final Class<T> type;

    CrudServlet(CrudService<T> service, Class<T> type) {
        this.service = service;
        this.type = type;
    }

    @Override
    protected final void service(HttpServletRequest request, HttpServletResponse response) throws IOException {
        request.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json");
        response.setHeader("Cache-Control", "no-store");
        try {
            route(request, response);
        } catch (ApiException ex) {
            error(response, ex.status(), ex.getMessage());
        } catch (JsonProcessingException ex) {
            error(response, 400, "Некоректний JSON або тип даних");
        } catch (DataAccessException ex) {
            SQLException sql = (SQLException) ex.getCause();
            LOG.log(Level.WARNING, "Операція БД не виконана. SQLState={0}, код={1}",
                    new Object[] {sql.getSQLState(), sql.getErrorCode()});
            if (ex.conflict()) {
                error(response, 409, "Конфлікт даних: запис уже існує або використовується в іншому записі");
            } else {
                error(response, 503, "База даних недоступна. Перевірте MySQL та конфігурацію сервера");
            }
        } catch (RuntimeException ex) {
            LOG.log(Level.SEVERE, "Неочікувана помилка сервера", ex);
            error(response, 500, "Внутрішня помилка сервера");
        }
    }

    void route(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String path = request.getPathInfo();
        boolean collection = path == null || path.equals("/");
        Long id = collection ? null : parseId(path);
        response.setHeader("Allow", collection ? "GET, POST" : "GET, PUT, DELETE");
        switch (request.getMethod()) {
            case "GET" -> write(response, 200, collection ? service.list() : service.get(id));
            case "POST" -> {
                if (!collection) throw new ApiException(405, "Створення виконується за адресою колекції");
                write(response, 201, service.create(read(request, type)));
            }
            case "PUT" -> {
                if (collection) throw new ApiException(405, "Для редагування вкажіть ID запису");
                write(response, 200, service.update(id, read(request, type)));
            }
            case "DELETE" -> {
                if (collection) throw new ApiException(405, "Для видалення вкажіть ID запису");
                service.delete(id);
                response.setStatus(204);
            }
            default -> throw new ApiException(405, "HTTP-метод не підтримується");
        }
    }

    static long parseId(String path) {
        if (!path.matches("/[1-9][0-9]*")) throw new ApiException(404, "Невідома адреса запиту");
        try {
            return Long.parseLong(path.substring(1));
        } catch (NumberFormatException ex) {
            throw ApiException.badRequest("Некоректний ідентифікатор");
        }
    }

    static <R> R read(HttpServletRequest request, Class<R> type) throws IOException {
        String contentType = request.getContentType();
        if (contentType == null || !contentType.split(";", 2)[0].strip().toLowerCase(Locale.ROOT).equals("application/json")) {
            throw new ApiException(415, "Очікується Content-Type application/json");
        }
        R value = JSON.readValue(request.getInputStream(), type);
        if (value == null) throw ApiException.badRequest("Потрібне тіло запиту");
        return value;
    }

    static void write(HttpServletResponse response, int status, Object value) throws IOException {
        response.setStatus(status);
        JSON.writeValue(response.getOutputStream(), value);
    }

    private static void error(HttpServletResponse response, int status, String message) throws IOException {
        if (!response.isCommitted()) write(response, status, Map.of("message", message));
    }
}
