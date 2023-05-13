package com.reto.plazoleta.domain.spi;

import com.reto.plazoleta.domain.model.CategoryModel;
import org.springframework.data.domain.Page;

import java.util.List;
public interface ICategoryPersistencePort {
    CategoryModel saveCategory(CategoryModel categoryModel);

    CategoryModel findById(Long idCategory);
}