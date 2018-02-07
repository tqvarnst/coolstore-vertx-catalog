package com.example.catalog;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.flywaydb.core.Flyway;

import com.example.catalog.model.Product;
import com.example.catalog.util.Transformers;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.SQLClient;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.WebClient;

public class CatalogVerticle extends AbstractVerticle {

	private static final String SELECT_ALL_STATEMENT = "select itemId, name, description, price from catalog";

	SQLClient client;
	
	@Override
	public void start(Future<Void> fut) {
		System.out.println("STARTING CATALOG VERTICLE");
		
		int port = context.config().getInteger("http.port",8080);
		JsonObject dbConfig = context.config().getJsonObject("db");	
		
		//Database init
		Flyway flyway = new Flyway();
		flyway.setDataSource(dbConfig.getString("url"),dbConfig.getString("user"),dbConfig.getString("password"));
		flyway.migrate();

		client = JDBCClient.createShared(vertx, dbConfig);
		
		Router router = Router.router(vertx);
		router.get("/health").handler(rc -> {
			rc.response().end("Running");
		});
		router.get("/services/products").handler(this::getCatalog);
		
		vertx.createHttpServer().requestHandler(router::accept).listen(port);
		System.out.println("CATALOG ENDPOINT STARTED AT " + port);
	}

	private void getCatalog(RoutingContext rc) {
		client.getConnection(res -> {
			if (res.succeeded()) {
				SQLConnection conn = res.result();
				conn.query(SELECT_ALL_STATEMENT, query -> {
					if (query.succeeded()) {
						Map<String,Product> prodMap = Transformers.resultSetToProductMap(query);
//						rc.response().end(Transformers.mapToJsonArray(prodMap).encodePrettily());
							
						//Async calls to inventory service
						List<Future> inventoryFutureList = new ArrayList<>();
						prodMap.keySet().forEach(itemId -> {
							inventoryFutureList.add(this.getInventoryData(itemId));
						});
						//Wait for all calls to return
						CompositeFuture.join(inventoryFutureList).setHandler(ar -> {
							if(ar.succeeded()) {
								ar.result().list().forEach(o -> {
									JsonObject inventory = (JsonObject) o;
									prodMap.get(inventory.getString("itemId")).setQuantity(inventory.getInteger("quantity"));									
								});
								
								rc.response().end(Transformers.mapToJsonArray(prodMap).encodePrettily());
							} else {
								//Fallback without quantity
								rc.response().end(Transformers.mapToJsonArray(prodMap).encodePrettily());
								System.out.println("One of more inventory request failed: " + ar.cause().toString());
							}
						});
						
					} else {
						rc.response().end("Failed");
						System.out.println("query.cause() = " + query.cause());
					}
				});
			}
		});
	}

	@SuppressWarnings("unused")
	private Future<JsonObject> getInventoryData(final String itemId) {
		Future<JsonObject> future = Future.future();
		JsonObject config = context.config().getJsonObject("inventory");
		WebClient.create(vertx)
			.get(config.getInteger("remote-port"), config.getString("hostname"), "/services/inventory/"+itemId)
			.timeout(config.getInteger("timeout",3000))
			.send(ar -> {
				if(ar.succeeded()) {
					if(ar.result().statusCode() == 200) {
						future.complete(ar.result().bodyAsJsonObject());
					} else {
						future.fail(String.format("Call to http://%s:%d return status %d!", config.getString("hostname"),config.getInteger("port"),ar.result().statusCode()));
					}
				} else {
					future.fail(ar.cause());
				}
			});
		return future;
	}
	

}
