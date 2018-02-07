package com.example.catalog.util;

import java.util.Map;
import java.util.stream.Collectors;

import com.example.catalog.model.Product;

import io.vertx.core.AsyncResult;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.sql.ResultSet;

public class Transformers {
	
	public static JsonObject productToJsonObject(Product product) {
		return new JsonObject()
					.put("itemId", product.getItemId())
					.put("name", product.getName())
					.put("description", product.getDescription())
					.put("price",product.getPrice())
					.put("quantity", product.getQuantity());
				
	}
	
	public static Product jsonObjectToProduct(JsonObject json) {
		Product prod = new Product();
		prod.setItemId(json.getString("itemId"));
		prod.setDescription(json.getString("description"));
		prod.setName(json.getString("name"));
		prod.setPrice(json.getDouble("price"));
		prod.setQuantity(json.getInteger("quantity"));
		return prod;
	}
	
	public static Product resultSetToProduct(JsonArray rsArr) {
		Product prod = new Product();
		prod.setItemId(rsArr.getString(0));
		prod.setName(rsArr.getString(1));
		prod.setDescription(rsArr.getString(2));
		prod.setPrice(rsArr.getDouble(3));
		return prod;
	}
	
	public static JsonArray mapToJsonArray(Map<String,Product> prodMap) {
		JsonArray jsArr = new JsonArray();
		prodMap.values().forEach(prod -> jsArr.add(productToJsonObject(prod)));
		return jsArr;
	}
		
	public static Map<String, Product> resultSetToProductMap(AsyncResult<ResultSet> query) {
		return query.result().getResults().stream().collect(Collectors.toMap(rsArr -> ((String) rsArr.getValue(0)),rsArr -> Transformers.resultSetToProduct(rsArr)));
	}
	

}
