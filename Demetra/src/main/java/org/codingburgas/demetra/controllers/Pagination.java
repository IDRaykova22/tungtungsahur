package org.codingburgas.demetra.controllers;

import java.util.ArrayList;
import java.util.List;

final class Pagination {
    private Pagination() {
    }

    /** Page numbers to show: first, last and two either side of the current one; 0 marks a gap ("…"). */
    static List<Integer> pageItems(int current, int total) {
        List<Integer> items = new ArrayList<>();
        for (int page = 1; page <= total; page++) {
            if (page == 1 || page == total || Math.abs(page - current) <= 2) {
                items.add(page);
            } else if (items.get(items.size() - 1) != 0) {
                items.add(0);
            }
        }
        return items;
    }
}
