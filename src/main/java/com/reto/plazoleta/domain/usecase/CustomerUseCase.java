package com.reto.plazoleta.domain.usecase;

import com.reto.plazoleta.domain.api.ICustomerServicePort;
import com.reto.plazoleta.domain.exception.*;
import com.reto.plazoleta.domain.gateways.IUserGateway;
import com.reto.plazoleta.domain.model.DishModel;
import com.reto.plazoleta.domain.model.OrderDishModel;
import com.reto.plazoleta.domain.model.OrderModel;
import com.reto.plazoleta.domain.model.RestaurantModel;
import com.reto.plazoleta.domain.spi.IDishPersistencePort;
import com.reto.plazoleta.domain.spi.IOrderDishPersistencePort;
import com.reto.plazoleta.domain.spi.IOrderPersistencePort;
import com.reto.plazoleta.domain.spi.IRestaurantPersistencePort;
import com.reto.plazoleta.infraestructure.configuration.security.jwt.JwtProvider;
import com.reto.plazoleta.infraestructure.drivenadapter.entity.StatusOrder;
import com.reto.plazoleta.infraestructure.drivenadapter.gateways.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class CustomerUseCase implements ICustomerServicePort {

    private final IOrderPersistencePort orderPersistencePort;
    private final IRestaurantPersistencePort restaurantPersistencePort;
    private final IDishPersistencePort dishPersistencePort;
    private final IUserGateway userGateway;
    private final JwtProvider jwtProvider;
    private final IOrderDishPersistencePort orderDishPersistencePort;

    public CustomerUseCase(IOrderPersistencePort orderPersistencePort, IRestaurantPersistencePort restaurantPersistencePort,
                           IDishPersistencePort dishPersistencePort, IUserGateway userGateway,
                           JwtProvider jwtProvider, IOrderDishPersistencePort orderDishPersistencePort) {
        this.orderPersistencePort = orderPersistencePort;
        this.restaurantPersistencePort = restaurantPersistencePort;
        this.dishPersistencePort = dishPersistencePort;
        this.userGateway = userGateway;
        this.jwtProvider = jwtProvider;
        this.orderDishPersistencePort = orderDishPersistencePort;
    }

    @Override
    public OrderModel saveOrder(OrderModel orderModelRequest, String tokenWithPrefixBearer) {
        String emailFromUserAuthenticated = getEmailFromUserAuthenticatedByTokenWithPrefixBearer(tokenWithPrefixBearer);
        final User userCustomerFound = getUserByEmail(emailFromUserAuthenticated, tokenWithPrefixBearer);
        validateRestaurant(orderModelRequest.getRestaurantModel().getIdRestaurant());
        checkStatusFromUserOrdersInARestaurant(orderModelRequest.getRestaurantModel().getIdRestaurant(), userCustomerFound.getIdUser());

        List<OrderDishModel> ordersDishesModelsRequest = orderModelRequest.getOrdersDishesModel();

        orderModelRequest.setIdUserCustomer(userCustomerFound.getIdUser());
        orderModelRequest.setDate(LocalDate.now());
        orderModelRequest.setStatus(StatusOrder.PENDIENTE);
        orderModelRequest.setOrdersDishesModel(new ArrayList<>());
        OrderModel orderModelSaved = this.orderPersistencePort.saveOrder(orderModelRequest);
        orderModelSaved.setOrdersDishesModel(ordersDishesModelsRequest);
        List<OrderDishModel> addOrderAndAmountOfDish = createOrdersDishesComplete(orderModelSaved);
        List<OrderDishModel> orderDishModelsSaved = this.orderDishPersistencePort.saveAllOrdersDishes(addOrderAndAmountOfDish);
        orderModelSaved.setOrdersDishesModel(orderDishModelsSaved);
        return orderModelSaved;
    }

    private void validateStatusFromOrderAndIfBelongTheUserAuthenticated(OrderModel orderModelToValidate, Long idUserAuthenticated) {
        if (orderModelToValidate == null) {
            throw new OrderNotExistsException("The order not exist");
        } else if (!orderModelToValidate.getIdUserCustomer().equals(idUserAuthenticated)) {
            throw new OrderNotExistsException("The order does not belong to the user");
        } else if (!orderModelToValidate.getStatus().equals(StatusOrder.PENDIENTE)) {
            throw new OrderInProcessException("Lo sentimos, tu pedido ya está en preparación y no puede cancelarse");
        }
    }

    @Override
    public Page<DishModel> getAllDishesActivePaginatedFromARestaurantOrderByCategoryAscending(Integer numberPage, Integer sizeItems, Long idRestaurant) {
        validateRestaurant(idRestaurant);
        Page<DishModel> dishesPaginatedAndOrderByCategory = this.dishPersistencePort
                .getAllDishesActiveOfARestaurantOrderByCategoryAscending(PageRequest.of(numberPage, sizeItems), idRestaurant);
        checkIfListIsEmpty(dishesPaginatedAndOrderByCategory.isEmpty());
        return dishesPaginatedAndOrderByCategory;
    }

    private void checkIfListIsEmpty(boolean isTheListEmpty) {
        if (isTheListEmpty)
            throw new NoDataFoundException();
    }

    @Override
    public OrderModel orderCanceled(Long idOrder, String tokenWithPrefixBearer) {
        String emailFromUserAuthenticated = getEmailFromUserAuthenticatedByTokenWithPrefixBearer(tokenWithPrefixBearer);
        final User userCustomerAuthenticated = getUserByEmail(emailFromUserAuthenticated, tokenWithPrefixBearer);
        OrderModel orderModelToChangeStatusToCanceled = this.orderPersistencePort.findByIdOrder(idOrder);
        validateStatusFromOrderAndIfBelongTheUserAuthenticated(orderModelToChangeStatusToCanceled, userCustomerAuthenticated.getIdUser());
        orderModelToChangeStatusToCanceled.setStatus(StatusOrder.CANCELADO);
        return this.orderPersistencePort.saveOrder(orderModelToChangeStatusToCanceled);
    }

    private String getEmailFromUserAuthenticatedByTokenWithPrefixBearer(String tokenWithPrefixBearer) {
        return this.jwtProvider.getAuthentication(tokenWithPrefixBearer.replace("Bearer ", "")).getName();
    }

    private User getUserByEmail(String email, String tokenWithPrefixBearer) {
        return this.userGateway.getUserByEmailInTheToken(email, tokenWithPrefixBearer);
    }

    private void validateRestaurant(Long idRestaurant) {
        final RestaurantModel restaurantFoundModel = this.restaurantPersistencePort.findByIdRestaurant(idRestaurant);
        if (restaurantFoundModel == null)
            throw new ObjectNotFoundException("The restaurant in the order does not exist");
    }

    private void checkStatusFromUserOrdersInARestaurant(Long idRestaurant, Long idUserCustomer) {
        final List<OrderModel> listOfOrdersFromUserFromSameRestaurant = this.orderPersistencePort.findByIdUserCustomerAndIdRestaurant(
                        idUserCustomer, idRestaurant).stream()
                .filter(order -> !order.getStatus().equals(StatusOrder.CANCELADO) && !order.getStatus().equals(StatusOrder.ENTREGADO))
                .collect(Collectors.toList());
        if (!listOfOrdersFromUserFromSameRestaurant.isEmpty())
            throw new CustomerHasAOrderInProcessException("The customer user has an order in process");
    }

    private List<OrderDishModel> createOrdersDishesComplete(OrderModel orderModel) {
        List<OrderDishModel> ordersDishesModelToSave = new ArrayList<>();
        for (OrderDishModel orderDishModel : orderModel.getOrdersDishesModel()) {
            DishModel dishFoundWithValueInAllItsFields = this.dishPersistencePort.findById(orderDishModel.getDishModel().getIdDish());
            if (dishFoundWithValueInAllItsFields == null) {
                throw new DishNotExistsException("The dish does not exist");
            } else if (dishFoundWithValueInAllItsFields.getRestaurantModel().getIdRestaurant().equals(orderModel.getRestaurantModel().getIdRestaurant())) {
                OrderDishModel orderDishModelToSave = new OrderDishModel();
                orderDishModelToSave.setOrderModel(orderModel);
                orderDishModelToSave.setDishModel(dishFoundWithValueInAllItsFields);
                orderDishModelToSave.setAmount(orderDishModel.getAmount());
                ordersDishesModelToSave.add(orderDishModelToSave);
            }
        }
        return ordersDishesModelToSave;
    }
}
