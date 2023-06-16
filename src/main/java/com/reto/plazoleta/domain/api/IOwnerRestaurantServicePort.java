package com.reto.plazoleta.domain.api;

import com.reto.plazoleta.domain.model.DishModel;
import com.reto.plazoleta.domain.model.EmployeeRestaurantModel;

public interface IOwnerRestaurantServicePort {

    DishModel saveDish(DishModel dishModel);

    DishModel updateDish(DishModel dishModel);

    DishModel updateStateDish(DishModel dishModel);
}
