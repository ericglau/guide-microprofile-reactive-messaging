// tag::copyright[]
/*******************************************************************************
 * Copyright (c) 2020 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - Initial implementation
 *******************************************************************************/
// end::copyright[]
package io.openliberty.guides.order;

import io.openliberty.guides.models.Order;
import io.openliberty.guides.models.OrderRequest;
import io.openliberty.guides.models.Status;
import io.openliberty.guides.models.Type;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.json.bind.JsonbBuilder;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.eclipse.microprofile.reactive.streams.operators.PublisherBuilder;
import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;

@ApplicationScoped
@Path("/orders")
public class OrderResource {
	@Inject
	private OrderManager manager;

	private BlockingQueue<Order> foodQueue = new LinkedBlockingQueue<>();
	private BlockingQueue<Order> drinkQueue = new LinkedBlockingQueue<>();

	private AtomicInteger counter = new AtomicInteger();

	@POST
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/")
	public Response createOrder(OrderRequest orderRequest) {
		//Consumes an Order from the end user in a specific format. See finish/order.json as an example
		String orderId;
		Order newOrder;

		for(String foodItem : orderRequest.getFoodList()){
			orderId = String.format("%04d", counter.incrementAndGet());

			newOrder = new Order(orderId, orderRequest.getTableID(), Type.FOOD, foodItem, Status.NEW);
			manager.addOrder(newOrder);
			foodQueue.add(newOrder);
		}

		for(String drinkItem : orderRequest.getDrinkList()){
			orderId = String.format("%04d", counter.incrementAndGet());

			newOrder = new Order(orderId, orderRequest.getTableID(), Type.DRINK, drinkItem, Status.NEW);
			manager.addOrder(newOrder);
			drinkQueue.add(newOrder);
		}

		return Response
				.status(Response.Status.OK)
				.entity(orderRequest)
				.build();
	}

	@Outgoing("food")
	public PublisherBuilder<String> sendFoodOrder() {
		return ReactiveStreams.generate(() -> {
			try {
				return JsonbBuilder.create().toJson(foodQueue.take());
			} catch (InterruptedException e) {
				e.printStackTrace();
				return null;
			}
		});
	}

	@Outgoing("drink")
	public PublisherBuilder<String> sendDrinkOrder() {
		return ReactiveStreams.generate(() -> {
			try {
				return JsonbBuilder.create().toJson(drinkQueue.take());
			} catch (InterruptedException e) {
				e.printStackTrace();
				return null;
			}
		});
	}

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Path("{orderId}")
	public Response getOrder(@PathParam("orderId") String orderId) {
		Order order = manager.getOrder(orderId);

		if (order == null) {
			return Response
					.status(Response.Status.BAD_REQUEST)
					.entity("Order id does not exist.")
					.build();
		}

		return Response
				.status(Response.Status.OK)
				.entity(order)
				.build();
	}

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/")
	public Response getOrdersList(@QueryParam("tableId") String tableId) {
		List<Order> ordersList = manager.getOrders()
				.values()
				.stream()
				.filter(order -> (tableId == null) || order.getTableID().equals(tableId))
				.collect(Collectors.toList());

		return Response
				.status(Response.Status.OK)
				.entity(ordersList)
				.build();
	}

	@Incoming("updateStatus")
	public void updateStatus(String stringOrder)  {
		System.out.println(" In Update Status method ");
		Order order = JsonbBuilder.create().fromJson(stringOrder,Order.class);
		manager.getOrder(order.getOrderID()).setStatus(order.getStatus());
		System.out.println(stringOrder);
	}
}