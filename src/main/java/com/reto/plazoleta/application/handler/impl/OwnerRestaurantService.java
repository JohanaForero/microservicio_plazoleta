package com.reto.plazoleta.application.handler.impl;

import com.reto.plazoleta.application.dto.request.DishRequestDto;
import com.reto.plazoleta.application.handler.IOwnerRestaurantService;
import com.reto.plazoleta.application.mapper.requestmapper.IDishRequestMapper;
import com.reto.plazoleta.domain.api.IOwnerRestaurantServicePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;

@RequiredArgsConstructor
@Service
@Transactional
public class OwnerRestaurantService implements IOwnerRestaurantService {

    private final IOwnerRestaurantServicePort ownerRestaurantServicePort;
    private final IDishRequestMapper dishRequestMapper;

    @Override
    public void saveDish(DishRequestDto dishRequestDto) {
        ownerRestaurantServicePort.saveDish(dishRequestMapper.toDishModel(dishRequestDto));
    }
}
