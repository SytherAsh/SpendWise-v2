package com.spendwise.transaction;

import java.util.List;

public interface CategoryService {

    /** All 12 seeded categories (docs/database.md). */
    List<Category> listAll();
}
