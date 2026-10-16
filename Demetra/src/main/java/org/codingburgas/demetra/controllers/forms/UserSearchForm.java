package org.codingburgas.demetra.controllers.forms;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.domain.Sort;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Search, filter, sort and page parameters of the administration user list. Unknown values fall back to defaults,
 * and the link builders keep every other parameter so that sorting or paging never loses the current search.
 */
@Getter
@Setter
public class UserSearchForm {
    public static final List<Integer> PAGE_SIZES = List.of(10, 25, 50);
    public static final String BASE_PATH = "/admin/users";
    private static final Set<String> STATUSES = Set.of("all", "active", "inactive");
    private static final Set<String> SORTS = Set.of("name", "email", "role", "date");

    private String q = "";
    private String status = "all";
    private String role = "";
    private String sort = "name";
    private String dir = "";
    private int page = 1;
    private int size = PAGE_SIZES.get(0);

    public void normalize() {
        q = q == null ? "" : q.trim().replaceAll("\\s+", " ");
        if (q.length() > 100) {
            q = q.substring(0, 100);
        }
        status = STATUSES.contains(status) ? status : "all";
        role = role == null ? "" : role.trim().toUpperCase();
        sort = SORTS.contains(sort) ? sort : "name";
        dir = "asc".equals(dir) || "desc".equals(dir) ? dir : defaultDir(sort);
        page = Math.max(page, 1);
        size = PAGE_SIZES.contains(size) ? size : PAGE_SIZES.get(0);
    }

    /** Ties are broken by name and then id, so paging is stable even when the sorted values repeat. */
    public Sort toSort() {
        Sort.Direction direction = isAscending() ? Sort.Direction.ASC : Sort.Direction.DESC;
        Sort.Order primary = switch (sort) {
            case "email" -> new Sort.Order(direction, "email").ignoreCase();
            case "role" -> new Sort.Order(direction, "role");
            case "date" -> new Sort.Order(direction, "createdAt");
            default -> new Sort.Order(direction, "name").ignoreCase();
        };
        return Sort.by(primary, Sort.Order.asc("name").ignoreCase(), Sort.Order.asc("id"));
    }

    public boolean isAscending() {
        return "asc".equals(dir);
    }

    public boolean isFiltered() {
        return !q.isEmpty() || !"all".equals(status) || !role.isEmpty();
    }

    public String pageUrl(int targetPage) {
        return url(targetPage, sort, dir);
    }

    /** Clicking the active column flips the direction; another column starts with its natural direction. */
    public String sortUrl(String field) {
        String targetDir = field.equals(sort) ? (isAscending() ? "desc" : "asc") : defaultDir(field);
        return url(1, field, targetDir);
    }

    public String ariaSort(String field) {
        if (!field.equals(sort)) {
            return "none";
        }
        return isAscending() ? "ascending" : "descending";
    }

    public String sortIndicator(String field) {
        if (!field.equals(sort)) {
            return "↕";
        }
        return isAscending() ? "▲" : "▼";
    }

    /** Names and emails read naturally A→Z; for registration dates the newest first is more useful. */
    private static String defaultDir(String field) {
        return "date".equals(field) ? "desc" : "asc";
    }

    private String url(int targetPage, String targetSort, String targetDir) {
        Map<String, Object> params = new LinkedHashMap<>();
        if (!q.isEmpty()) {
            params.put("q", q);
        }
        if (!"all".equals(status)) {
            params.put("status", status);
        }
        if (!role.isEmpty()) {
            params.put("role", role);
        }
        if (!"name".equals(targetSort)) {
            params.put("sort", targetSort);
        }
        if (!targetDir.equals(defaultDir(targetSort))) {
            params.put("dir", targetDir);
        }
        if (size != PAGE_SIZES.get(0)) {
            params.put("size", size);
        }
        if (targetPage > 1) {
            params.put("page", targetPage);
        }
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath(BASE_PATH);
        params.keySet().forEach(name -> builder.queryParam(name, "{" + name + "}"));
        // Values as URI variables are fully encoded, so a "+" in "+359..." is not read back as a space.
        return builder.encode().buildAndExpand(params).toUriString();
    }
}
